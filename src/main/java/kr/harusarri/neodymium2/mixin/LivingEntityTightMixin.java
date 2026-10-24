package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityTightMixin {
    @Unique private Vec3 neo$lastSafeWallPos;
    @Unique private BlockPos neo$lastEyeBlockPos;
    @Unique private BlockState neo$lastEyeBlockState;

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void neo$tightOutsidePushEntities(CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldSkipPushEntities((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void neo$tightOutsideDoPush(Entity entity, CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldSkipDoPush((LivingEntity) (Object) this, entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void neo$shortCircuitStationaryOutsideIsInWall(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mob mob
            && this.neo$lastSafeWallPos != null
            && mob.position().equals(this.neo$lastSafeWallPos)
            && ((mob.getId() + mob.tickCount) & 7) != 0
            && OutsideWorldOptimizer.shouldSkipInWallScan(mob, this.neo$lastEyeBlockPos, this.neo$lastEyeBlockState)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInWall", at = @At("RETURN"))
    private void neo$recordSafeWallState(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (Object) this instanceof Mob mob) {
            BlockPos eyePos = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
            this.neo$lastSafeWallPos = mob.position();
            this.neo$lastEyeBlockPos = eyePos;
            this.neo$lastEyeBlockState = mob.level().getBlockState(eyePos);
        } else {
            this.neo$lastSafeWallPos = null;
        }
    }
}
