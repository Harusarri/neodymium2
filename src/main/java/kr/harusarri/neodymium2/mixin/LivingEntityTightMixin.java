package kr.harusarri.neodymium2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kr.harusarri.neodymium2.Neodymium2;
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
    @Unique private BlockPos neo$lastClimbablePos;
    @Unique private BlockState neo$lastClimbableState;
    @Unique private boolean neo$lastClimbableResult;

    @WrapOperation(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"
        )
    )
    private void neo$skipDormantOrDistantAmbientTravel(LivingEntity self, Vec3 travelVector, Operation<Void> original) {
        if (!OutsideEntityOptimizer.shouldSkipOutsideTravel(self, travelVector)) {
            original.call(self, travelVector);
        }
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void neo$cachedOutsideOnClimbable(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mob mob
            && OutsideWorldOptimizer.enabled()
            && this.neo$lastClimbablePos != null
            && mob.blockPosition().equals(this.neo$lastClimbablePos)
            && mob.level().getBlockState(this.neo$lastClimbablePos) == this.neo$lastClimbableState
            && !Neodymium2.regionProtects(mob)) {
            cir.setReturnValue(this.neo$lastClimbableResult);
        }
    }

    @Inject(method = "onClimbable", at = @At("RETURN"))
    private void neo$recordClimbableState(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mob mob) {
            BlockPos pos = mob.blockPosition();
            this.neo$lastClimbablePos = pos;
            this.neo$lastClimbableState = mob.level().getBlockState(pos);
            this.neo$lastClimbableResult = cir.getReturnValueZ();
        }
    }

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
