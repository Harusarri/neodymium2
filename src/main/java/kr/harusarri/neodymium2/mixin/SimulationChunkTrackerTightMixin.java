package kr.harusarri.neodymium2.mixin;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.server.level.SimulationChunkTracker;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lossless L0/L1 Direct-Mapped Chunk Level Cache + Concrete Long2ByteOpenHashMap Dispatch
 * for SimulationChunkTracker.getLevel(long):
 * Eliminates repeated interface-dispatched Long2ByteOpenHashMap probing across
 * tickSpawningChunk, tickChunks, NearbyEntityTracking, and shouldTickBlocksAt.
 * Synchronously updated on every setLevel(long, int) mutation for 100% Vanilla equivalence.
 */
@Mixin(value = SimulationChunkTracker.class, priority = 900)
public abstract class SimulationChunkTrackerTightMixin {
    @Shadow @Final
    protected Long2ByteMap chunks;

    @Unique
    private static final int NEO$CACHE_MASK = 4095;

    @Unique
    private final long[] neo$cacheKeys = new long[NEO$CACHE_MASK + 1];

    @Unique
    private final byte[] neo$cacheVals = new byte[NEO$CACHE_MASK + 1];

    @Unique
    private final boolean[] neo$cacheValid = new boolean[NEO$CACHE_MASK + 1];

    @Unique
    private Long2ByteOpenHashMap neo$openChunks;

    @Unique
    private volatile Thread neo$ownerThread;

    @Unique
    private long neo$lastPos = Long.MIN_VALUE;

    @Unique
    private byte neo$lastVal;

    @Unique
    private boolean neo$hasLast;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neo$captureOpenChunks(TicketStorage ticketStorage, CallbackInfo ci) {
        this.neo$ownerThread = Thread.currentThread();
        if (this.chunks instanceof Long2ByteOpenHashMap open) {
            this.neo$openChunks = open;
        }
    }

    @Inject(method = "setLevel(JI)V", at = @At("RETURN"))
    private void neo$syncCacheOnSetLevel(long chunkPos, int level, CallbackInfo ci) {
        this.neo$ownerThread = Thread.currentThread();
        Long2ByteOpenHashMap open = this.neo$openChunks;
        byte def = open != null ? open.defaultReturnValue() : this.chunks.defaultReturnValue();
        int maxLevel = def & 0xFF;
        byte val = level >= maxLevel ? def : (byte) level;
        if (this.neo$hasLast && this.neo$lastPos == chunkPos) {
            this.neo$lastVal = val;
        }
        int slot = (int) (HashCommon.mix(chunkPos) & NEO$CACHE_MASK);
        this.neo$cacheKeys[slot] = chunkPos;
        this.neo$cacheVals[slot] = val;
        this.neo$cacheValid[slot] = true;
    }

    @Inject(method = "getLevel(J)I", at = @At("HEAD"), cancellable = true)
    private void neo$fastGetLevel(long chunkPos, CallbackInfoReturnable<Integer> cir) {
        if (Thread.currentThread() != this.neo$ownerThread) {
            return;
        }
        if (this.neo$hasLast && this.neo$lastPos == chunkPos) {
            OutsideWorldOptimizer.recordCachedBlockTickChunk();
            cir.setReturnValue((int) this.neo$lastVal);
            return;
        }
        int slot = (int) (HashCommon.mix(chunkPos) & NEO$CACHE_MASK);
        if (this.neo$cacheValid[slot] && this.neo$cacheKeys[slot] == chunkPos) {
            byte val = this.neo$cacheVals[slot];
            this.neo$lastPos = chunkPos;
            this.neo$lastVal = val;
            this.neo$hasLast = true;
            OutsideWorldOptimizer.recordCachedBlockTickChunk();
            cir.setReturnValue((int) val);
            return;
        }
        Long2ByteOpenHashMap open = this.neo$openChunks;
        byte val = open != null ? open.get(chunkPos) : this.chunks.get(chunkPos);
        this.neo$cacheKeys[slot] = chunkPos;
        this.neo$cacheVals[slot] = val;
        this.neo$cacheValid[slot] = true;
        this.neo$lastPos = chunkPos;
        this.neo$lastVal = val;
        this.neo$hasLast = true;
        cir.setReturnValue((int) val);
    }
}
