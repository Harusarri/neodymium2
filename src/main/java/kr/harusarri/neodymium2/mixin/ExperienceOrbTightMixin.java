package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paper / Purpur optimization: Throttle ExperienceOrb player/merge AABB scans
 * outside vanillaSensitive regions while preserving 100% Vanilla behavior inside
 * protected regions.
 */
@Mixin(value = ExperienceOrb.class, priority = 900)
public abstract class ExperienceOrbTightMixin {
    @Inject(method = "followNearbyPlayer", at = @At("HEAD"), cancellable = true)
    private void neo$throttleOutsidePlayerScan(CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldThrottleXpOrbScan((ExperienceOrb) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "scanForMerges", at = @At("HEAD"), cancellable = true)
    private void neo$throttleOutsideOrbMerge(CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldThrottleXpOrbScan((ExperienceOrb) (Object) this)) {
            ci.cancel();
        }
    }
}
