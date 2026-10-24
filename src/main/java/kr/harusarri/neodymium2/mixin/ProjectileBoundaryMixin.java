package kr.harusarri.neodymium2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import kr.harusarri.neodymium2.ProjectileCleanup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;

@Mixin(AbstractHurtingProjectile.class)
public abstract class ProjectileBoundaryMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void neo$beforeTick(CallbackInfo ci){if(ProjectileCleanup.discardIfInactive((Entity)(Object)this))ci.cancel();}

    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;Lnet/minecraft/world/level/ClipContext$Block;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult neo$limitTrace(Entity entity,Predicate<Entity> matching,ClipContext.Block clip,Operation<HitResult> original) {
        Vec3 movement=entity.getDeltaMovement();double t=ProjectileCleanup.boundary(entity,movement);
        if(!Double.isFinite(t))return original.call(entity,matching,clip);
        HitResult hit;
        // Leave a small gap before the lazy boundary so vanilla's block raycast cannot
        // reach across it. Restore velocity even when another mod throws an exception.
        double fraction=Math.max(0,t-1.0e-5/Math.max(1,movement.length()));
        if(fraction==0){ProjectileCleanup.discard(entity,true);return BlockHitResult.miss(entity.position(),net.minecraft.core.Direction.UP,entity.blockPosition());}
        entity.setDeltaMovement(movement.scale(fraction));
        try{hit=original.call(entity,matching,clip);}finally{entity.setDeltaMovement(movement);}
        // A collision before the boundary remains a normal vanilla hit/deflection.
        if(hit.getType()==HitResult.Type.MISS)ProjectileCleanup.discard(entity,true);
        return hit;
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/ProjectileUtil;rotateTowardsMovement(Lnet/minecraft/world/entity/Entity;F)V"),cancellable=true)
    private void neo$beforeEffects(CallbackInfo ci){if(((Entity)(Object)this).isRemoved())ci.cancel();}
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/hurtingprojectile/AbstractHurtingProjectile;applyEffectsFromBlocks()V"),cancellable=true)
    private void neo$afterMove(CallbackInfo ci){if(ProjectileCleanup.discardIfInactive((Entity)(Object)this))ci.cancel();}
}
