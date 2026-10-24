package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.Neodymium2;
import me.wesley1808.servercore.common.activation_range.ActivationRange;
import me.wesley1808.servercore.common.config.data.activation_range.*;
import me.wesley1808.servercore.common.interfaces.activation_range.ActivationEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ActivationRange.class,remap=false)
public abstract class ActivationRangeMixin {
    @Inject(method="checkIfActive",at=@At("HEAD"),cancellable=true)
    private static void neo$protect(Entity entity,int tick,CallbackInfoReturnable<Boolean> cir){if(Neodymium2.forceActive(entity)){var ae=(ActivationEntity)entity;ae.servercore$setActivatedTick(tick);ae.servercore$setInactive(false);cir.setReturnValue(true);}}
    @ModifyExpressionValue(method="shouldTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;servercore$isExcluded()Z"))
    private static boolean neo$exclusion(boolean original,Entity entity,ActivationRangeConfig config){return Neodymium2.excluded(original,entity);}
    @ModifyExpressionValue(method={"checkIfActive","checkInactiveWakeup"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;servercore$getActivationType()Lme/wesley1808/servercore/common/config/data/activation_range/ActivationType;"))
    private static ActivationType neo$interval(ActivationType original,Entity entity,int tick){return Neodymium2.profile(original,entity);}
    @ModifyExpressionValue(method="isWithinRange",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;servercore$getActivationType()Lme/wesley1808/servercore/common/config/data/activation_range/ActivationType;"))
    private static ActivationType neo$distance(ActivationType original,ServerPlayer player,Entity entity,ActivationRangeConfig config){return Neodymium2.profile(original,entity);}
    @ModifyArg(method="activateEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"),index=0)
    private static double neo$search(double value){return Neodymium2.searchRange(value);}
    @ModifyArg(method="activateEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"),index=2)
    private static double neo$searchZ(double value){return Neodymium2.searchRange(value);}
}
