package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrialSpawner.class, priority = 900)
public abstract class TrialSpawnerTightMixin {
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void neo$throttleOutsideTrialSpawner(ServerLevel level, BlockPos pos, boolean ominous, CallbackInfo ci) {
        if (OutsideWorldOptimizer.shouldThrottleOutsideTrialOrVault(level, pos)) {
            ci.cancel();
        }
    }
}
