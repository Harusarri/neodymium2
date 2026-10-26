package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.DormantTickingWrapper;
import kr.harusarri.neodymium2.core.LevelDormantTickersAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Single-Pass Unboxed BlockEntity Tick Pipeline + Active-Set <lithium_sleeping> Compaction
 * + Stable CHUNK_OUTSIDE Chunk-Locality Grouping + L0/L1 Chunk Tickability Gate:
 * 1. Parks sleeping wrappers outside vanillaSensitive regions in O(1) during the main pass.
 * 2. Stably groups CHUNK_OUTSIDE tickers by ChunkPos so same-chunk BlockEntities run contiguously
 *    with 100% L0 register hits on shouldTickBlocksAt(chunkKey), while keeping all vanillaSensitive
 *    BlockEntities in their exact 100% original Vanilla order.
 * 3. Eliminates Iterator allocation, O(N^2) ArrayList.remove shifting, and Lithium's @WrapOperation
 *    Object[]/Boolean boxing + LocalLongRefImpl interface dispatch on every BlockEntity tick.
 */
@Mixin(value = Level.class, priority = 900)
public abstract class LevelDormantTickersMixin implements LevelDormantTickersAccess {
    @Shadow @Final
    protected List<TickingBlockEntity> blockEntityTickers;

    @Shadow @Final
    private List<TickingBlockEntity> pendingBlockEntityTickers;

    @Shadow
    private boolean tickingBlockEntities;

    @Shadow
    public abstract boolean shouldTickBlocksAt(long chunkPos);

    @Unique
    private final Set<TickingBlockEntity> neo$parkedSleepingTickers = Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique
    private static final int NEO$CHUNK_TICK_MASK = 8191;

    @Unique
    private final int[] neo$chunkTickEpoch = new int[NEO$CHUNK_TICK_MASK + 1];

    @Unique
    private final long[] neo$chunkTickKeys = new long[NEO$CHUNK_TICK_MASK + 1];

    @Unique
    private final boolean[] neo$chunkTickVals = new boolean[NEO$CHUNK_TICK_MASK + 1];

    @Unique
    private boolean neo$needsChunkSort;

    @Unique
    private long neo$lastSortGameTime = Long.MIN_VALUE;

    @Unique
    private long neo$lastParkedRegionGen = -1L;

    @Override
    public void neo$markHasSleepingToCompact() {
    }

    @Override
    public void neo$wakeSleepingTicker(TickingBlockEntity wrapper) {
        if (this.neo$parkedSleepingTickers.remove(wrapper)) {
            (this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(wrapper);
            this.neo$needsChunkSort = true;
        }
    }

    @Override
    public void neo$unparkSleepingTicker(TickingBlockEntity wrapper) {
        this.neo$parkedSleepingTickers.remove(wrapper);
    }

    @Override
    public int neo$parkedSleepingCount() {
        return this.neo$parkedSleepingTickers.size();
    }

    @Inject(method = "addBlockEntityTicker", at = @At("HEAD"))
    private void neo$bindWrapperLevelOnAdd(TickingBlockEntity ticker, CallbackInfo ci) {
        Level self = (Level) (Object) this;
        if (ticker instanceof DormantTickingWrapper wrapper) {
            wrapper.neo$bindLevel(self);
            if (wrapper.neo$getCachedSortKey(self, Neodymium2.snapshot().generation()) != Long.MIN_VALUE) {
                this.neo$needsChunkSort = true;
            }
        }
    }

    @Unique
    private static long neo$chunkSortKey(Level level, long regionGen, TickingBlockEntity ticker) {
        if (ticker instanceof DormantTickingWrapper w) {
            return w.neo$getCachedSortKey(level, regionGen);
        }
        BlockPos pos = ticker.getPos();
        if (pos == null || Neodymium2.regionProtectsBlock(level, pos)) {
            return Long.MIN_VALUE;
        }
        long ck = ChunkPos.pack(pos);
        return ck == Long.MIN_VALUE ? Long.MIN_VALUE + 1L : ck;
    }

    @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void neo$fastTickBlockEntities(CallbackInfo ci) {
        long currentGen = Neodymium2.snapshot().generation();
        boolean tight = OutsideWorldOptimizer.enabled();
        boolean genChanged = currentGen != this.neo$lastParkedRegionGen;
        if (!this.neo$parkedSleepingTickers.isEmpty() && (!tight || genChanged)) {
            this.neo$lastParkedRegionGen = currentGen;
            for (TickingBlockEntity ticker : this.neo$parkedSleepingTickers) {
                if (ticker instanceof DormantTickingWrapper wrapper) {
                    wrapper.neo$setUnlinked(false);
                }
                if (!ticker.isRemoved()) {
                    this.pendingBlockEntityTickers.add(ticker);
                }
            }
            this.neo$parkedSleepingTickers.clear();
            this.neo$needsChunkSort = true;
        }
        if (!tight) {
            return;
        }

        this.neo$lastParkedRegionGen = currentGen;
        Level self = (Level) (Object) this;
        this.tickingBlockEntities = true;

        if (!this.pendingBlockEntityTickers.isEmpty()) {
            for (int i = 0, n = this.pendingBlockEntityTickers.size(); i < n; i++) {
                TickingBlockEntity pending = this.pendingBlockEntityTickers.get(i);
                if (pending.isRemoved()) {
                    continue;
                }
                BlockPos p = pending.getPos();
                if (p == null && pending instanceof DormantTickingWrapper wrapper) {
                    BlockPos lastPos = wrapper.neo$getLastKnownPos();
                    if (lastPos == null || !Neodymium2.regionProtectsBlock(self, lastPos)) {
                        wrapper.neo$setUnlinked(true);
                        this.neo$parkedSleepingTickers.add(pending);
                        continue;
                    }
                }
                this.blockEntityTickers.add(pending);
                if (p != null && neo$chunkSortKey(self, currentGen, pending) != Long.MIN_VALUE) {
                    this.neo$needsChunkSort = true;
                }
            }
            this.pendingBlockEntityTickers.clear();
        }

        long gameTime = self.getGameTime();
        if (this.neo$needsChunkSort && this.blockEntityTickers.size() > 1
                && (genChanged || gameTime - this.neo$lastSortGameTime >= 200L || this.neo$lastSortGameTime == Long.MIN_VALUE)) {
            this.neo$needsChunkSort = false;
            this.neo$lastSortGameTime = gameTime;
            this.blockEntityTickers.sort((a, b) -> Long.compare(
                    neo$chunkSortKey(self, currentGen, a),
                    neo$chunkSortKey(self, currentGen, b)));
        }

        int epoch = OutsideWorldOptimizer.beginBlockEntityTickPass();
        boolean runsNormally = self.tickRateManager().runsNormally();
        long lastChunkKey = Long.MIN_VALUE;
        boolean lastChunkHasValue = false;
        boolean lastChunkCanTick = false;
        List<TickingBlockEntity> tickers = this.blockEntityTickers;
        int size = tickers.size();
        int read = 0;
        int write = 0;

        try {
            while (read < size) {
                TickingBlockEntity ticker = tickers.get(read++);
                if (ticker.isRemoved()) {
                    continue;
                }
                BlockPos pos = ticker.getPos();
                if (pos == null) {
                    if (ticker instanceof DormantTickingWrapper wrapper) {
                        BlockPos lastPos = wrapper.neo$getLastKnownPos();
                        if (lastPos == null || !Neodymium2.regionProtectsBlock(self, lastPos)) {
                            wrapper.neo$setUnlinked(true);
                            this.neo$parkedSleepingTickers.add(ticker);
                            continue;
                        }
                    }
                    if (write != read - 1) {
                        tickers.set(write, ticker);
                    }
                    write++;
                    continue;
                }

                if (write != read - 1) {
                    tickers.set(write, ticker);
                }
                write++;

                if (runsNormally) {
                    long chunkKey = ChunkPos.pack(pos);
                    boolean canTick;
                    if (lastChunkHasValue && chunkKey == lastChunkKey) {
                        canTick = lastChunkCanTick;
                        OutsideWorldOptimizer.recordCachedBlockTickChunk();
                    } else {
                        int slot = (int) (it.unimi.dsi.fastutil.HashCommon.mix(chunkKey) & NEO$CHUNK_TICK_MASK);
                        if (this.neo$chunkTickEpoch[slot] == epoch && this.neo$chunkTickKeys[slot] == chunkKey) {
                            canTick = this.neo$chunkTickVals[slot];
                            OutsideWorldOptimizer.recordCachedBlockTickChunk();
                        } else {
                            canTick = this.shouldTickBlocksAt(chunkKey);
                            this.neo$chunkTickEpoch[slot] = epoch;
                            this.neo$chunkTickKeys[slot] = chunkKey;
                            this.neo$chunkTickVals[slot] = canTick;
                        }
                        lastChunkKey = chunkKey;
                        lastChunkCanTick = canTick;
                        lastChunkHasValue = true;
                    }

                    if (canTick) {
                        ticker.tick();
                        if (ticker.isRemoved()) {
                            write--;
                        } else if (ticker.getPos() == null && ticker instanceof DormantTickingWrapper wrapper) {
                            BlockPos lastPos = wrapper.neo$getLastKnownPos();
                            if (lastPos == null || !Neodymium2.regionProtectsBlock(self, lastPos)) {
                                wrapper.neo$setUnlinked(true);
                                this.neo$parkedSleepingTickers.add(ticker);
                                write--;
                            }
                        }
                    }
                }
            }
        } finally {
            if (write != read) {
                while (read < size) {
                    tickers.set(write++, tickers.get(read++));
                }
                tickers.subList(write, size).clear();
            }
            this.tickingBlockEntities = false;
            OutsideWorldOptimizer.endBlockEntityTickPass();
        }

        int parkedCount = this.neo$parkedSleepingTickers.size();
        if (parkedCount > 0) {
            OutsideWorldOptimizer.recordCompactedSleepingBlockEntities(parkedCount);
        }
        ci.cancel();
    }
}
