package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.Neodymium2;
import me.wesley1808.servercore.common.interfaces.activation_range.ActivationEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Entity.class,priority=900,remap=false)
public abstract class EntityBoundaryMixin {
    @Inject(method="push(DDD)V",at=@At("HEAD"))private void neo$boundary(double x,double y,double z,CallbackInfo ci){
        var ae=(ActivationEntity)this;if(ae.servercore$isInactive()&&Neodymium2.forceActive((Entity)(Object)this))ae.servercore$setInactive(false);
    }
}
