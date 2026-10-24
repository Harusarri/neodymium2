package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelChunk.class, priority = 900)
public abstract class LevelChunkTightMixin {
    @Unique
    private int neo$cachedStatusEpoch;

    @Unique
    private FullChunkStatus neo$cachedFullStatus;

    @Inject(method = "getFullStatus", at = @At("HEAD"), cancellable = true)
    private void neo$getCachedFullStatusDuringBeTick(CallbackInfoReturnable<FullChunkStatus> cir) {
        int epoch = OutsideWorldOptimizer.currentBlockEntityTickEpoch();
        if (epoch != 0 && this.neo$cachedStatusEpoch == epoch) {
            cir.setReturnValue(this.neo$cachedFullStatus);
        }
    }

    @Inject(method = "getFullStatus", at = @At("RETURN"))
    private void neo$storeCachedFullStatusDuringBeTick(CallbackInfoReturnable<FullChunkStatus> cir) {
        int epoch = OutsideWorldOptimizer.currentBlockEntityTickEpoch();
        if (epoch != 0) {
            this.neo$cachedStatusEpoch = epoch;
            this.neo$cachedFullStatus = cir.getReturnValue();
        }
    }
}
