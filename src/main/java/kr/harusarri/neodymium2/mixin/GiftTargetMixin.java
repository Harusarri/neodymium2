package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.VillagerLobotomy;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GiveGiftToHero.class)
public abstract class GiftTargetMixin {
    @Inject(method="throwGift",at=@At("HEAD"),cancellable=true)
    private void neo$validRecipient(ServerLevel level,Villager v,LivingEntity target,CallbackInfo ci){
        if(VillagerLobotomy.enabled()&&(!(target instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()||p.level()!=level||!level.players().contains(p)||!p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)||!v.hasLineOfSight(p)))ci.cancel();
    }
}
