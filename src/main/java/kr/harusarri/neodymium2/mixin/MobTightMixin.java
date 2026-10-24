package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Mob.class, priority = 900)
public abstract class MobTightMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void neo$tightOutsideAi(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (OutsideEntityOptimizer.shouldSkipAi(self)) {
            if (!self.getNavigation().isDone()) {
                self.getNavigation().tick();
            }
            self.getMoveControl().tick();
            self.getLookControl().tick();
            self.getJumpControl().tick();
            ci.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"))
    private void neo$wakeOnPlayerInteract(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        OutsideEntityOptimizer.wakeOnPlayerInteract((Mob) (Object) this);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void neo$wakeOnMobInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        OutsideEntityOptimizer.wakeOnPlayerInteract((Mob) (Object) this);
    }
}
