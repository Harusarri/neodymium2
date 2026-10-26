package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.core.DormantTickingWrapper;
import kr.harusarri.neodymium2.core.LevelDormantTickersAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper")
public abstract class RebindableTickingWrapperMixin implements DormantTickingWrapper {
    @Shadow
    private TickingBlockEntity ticker;

    @Unique
    private Level neo$level;

    @Unique
    private BlockPos neo$lastPos;

    @Unique
    private boolean neo$unlinked;

    @Unique
    private long neo$cachedSortGen = -1L;

    @Unique
    private long neo$cachedSortKey = Long.MIN_VALUE;

    @Override
    public void neo$bindLevel(Level level) {
        this.neo$level = level;
        this.neo$cachedSortGen = -1L;
        if (this.ticker != null) {
            BlockPos p = this.ticker.getPos();
            if (p != null) {
                this.neo$lastPos = p;
            }
        }
    }

    @Override
    public BlockPos neo$getLastKnownPos() {
        return this.neo$lastPos;
    }

    @Override
    public boolean neo$isUnlinked() {
        return this.neo$unlinked;
    }

    @Override
    public void neo$setUnlinked(boolean unlinked) {
        this.neo$unlinked = unlinked;
    }

    @Override
    public long neo$getCachedSortKey(Level level, long regionGen) {
        if (this.neo$cachedSortGen == regionGen) {
            return this.neo$cachedSortKey;
        }
        BlockPos pos = this.ticker != null ? this.ticker.getPos() : null;
        if (pos != null) {
            this.neo$lastPos = pos;
        } else {
            pos = this.neo$lastPos;
        }
        long key;
        if (pos == null || Neodymium2.regionProtectsBlock(level, pos)) {
            key = Long.MIN_VALUE;
        } else {
            long ck = ChunkPos.pack(pos);
            key = ck == Long.MIN_VALUE ? Long.MIN_VALUE + 1L : ck;
        }
        this.neo$cachedSortKey = key;
        this.neo$cachedSortGen = regionGen;
        return key;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neo$captureInitialPos(TickingBlockEntity initialTicker, CallbackInfo ci) {
        if (initialTicker != null) {
            BlockPos p = initialTicker.getPos();
            if (p != null) {
                this.neo$lastPos = p;
            }
        }
    }

    @Inject(method = "rebind", at = @At("HEAD"))
    private void neo$capturePosBeforeRebind(TickingBlockEntity newTicker, CallbackInfo ci) {
        if (this.ticker != null) {
            BlockPos p = this.ticker.getPos();
            if (p != null) {
                this.neo$lastPos = p;
            }
        }
        if (newTicker != null) {
            BlockPos np = newTicker.getPos();
            if (np != null) {
                this.neo$lastPos = np;
            }
        }
    }

    @Inject(method = "rebind", at = @At("RETURN"))
    private void neo$handleSleepOrWakeTransition(TickingBlockEntity newTicker, CallbackInfo ci) {
        if (!(this.neo$level instanceof LevelDormantTickersAccess access)) {
            return;
        }
        if (newTicker == null || newTicker.isRemoved()) {
            if (this.neo$unlinked) {
                this.neo$unlinked = false;
                access.neo$unparkSleepingTicker((TickingBlockEntity) (Object) this);
            }
            this.neo$level = null;
        } else if (newTicker.getPos() == null) {
            // Transitioned to Lithium's SLEEPING_BLOCK_ENTITY_TICKER (<lithium_sleeping>)
            if (!this.neo$unlinked) {
                access.neo$markHasSleepingToCompact();
            }
        } else {
            // Woke up with a real BoundTickingBlockEntity (getPos() != null)
            if (this.neo$unlinked) {
                this.neo$unlinked = false;
                access.neo$wakeSleepingTicker((TickingBlockEntity) (Object) this);
            }
        }
    }
}
