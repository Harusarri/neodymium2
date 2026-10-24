package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gale / Petal optimization: Retain Sensing seen/unseen 3D raycast cache across multiple ticks
 * for mobs outside vanillaSensitive regions. Inside protected regions, 100% Vanilla 1-tick
 * cache clearing is preserved.
 */
@Mixin(value = Sensing.class, priority = 900)
public abstract class SensingTightMixin {
    @Shadow @Final private Mob mob;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void neo$retainLineOfSightCache(CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldRetainSensingCache(this.mob)) {
            ci.cancel();
        }
    }
}
