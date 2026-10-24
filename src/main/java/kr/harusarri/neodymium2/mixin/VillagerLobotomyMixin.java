package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.*;
import kr.harusarri.neodymium2.core.VillagerDormantAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;

@Mixin(value=Villager.class,priority=900)
public abstract class VillagerLobotomyMixin implements VillagerDormantAccess {
    @Unique private VillagerLobotomy.State neo$lobotomy;
    @Override
    public boolean neo$isDormantVillager(){
        return this.neo$lobotomy!=null&&this.neo$lobotomy.dormant()&&VillagerLobotomy.enabled();
    }
    @WrapMethod(method="customServerAiStep")
    private void neo$ownedScope(ServerLevel level,Operation<Void> original){
        if(!VillagerLobotomy.enabled()){original.call(level);return;}
        VillagerLobotomy.enterBrainScope();try{original.call(level);}finally{VillagerLobotomy.exitBrainScope();}
    }
    @WrapOperation(method="customServerAiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void neo$brain(Brain<?> brain,ServerLevel level,LivingEntity entity,Operation<Void> original){
        if(neo$lobotomy==null&&VillagerLobotomy.enabled())neo$lobotomy=new VillagerLobotomy.State();
        if(neo$lobotomy==null||!neo$lobotomy.suppress((Villager)entity,level))original.call(brain,level,entity);
    }
    @Inject(method="rewardTradeXp",at=@At("HEAD"))
    private void neo$trade(MerchantOffer offer,CallbackInfo ci){
        if(VillagerLobotomy.enabled()){
            if(neo$lobotomy==null)neo$lobotomy=new VillagerLobotomy.State();
            neo$lobotomy.wake((Villager)(Object)this,Neodymium2.snapshot().settings().villagerLobotomy().wakeAfterTradeTicks());
        }
    }
    @Inject(method="mobInteract",at=@At("HEAD"))
    private void neo$wakeUntradedOnInteract(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir){
        var v=(Villager)(Object)this;
        if(VillagerLobotomy.enabled()&&(v.getVillagerXp()<=0||v.getOffers().isEmpty())){
            if(neo$lobotomy==null)neo$lobotomy=new VillagerLobotomy.State();
            neo$lobotomy.wake(v,100);
        }
    }
}
