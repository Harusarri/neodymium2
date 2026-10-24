package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.VillagerLobotomy;
import me.wesley1808.servercore.common.config.impl.FeatureConfigImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=FeatureConfigImpl.class,remap=false)
public abstract class ServerCoreVillagerConfigMixin {
    // Override only while executing an owned villager Brain call. Config display,
    // serialization and other threads see the original ServerCore value.
    @Inject(method="lobotomizeVillagers",at=@At("HEAD"),cancellable=true)
    private void neo$singlePolicy(CallbackInfoReturnable<Boolean> cir){if(VillagerLobotomy.ownsCurrentBrainCall())cir.setReturnValue(false);}
}
