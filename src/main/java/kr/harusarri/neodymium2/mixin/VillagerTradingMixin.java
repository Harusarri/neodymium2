package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(Villager.class)
public abstract class VillagerTradingMixin implements VillagerTrading {
    @Shadow protected abstract void updateSpecialPrices(Player player);
    @Unique private UUID neo$customer;
    @Unique private int neo$hero;
    @Unique private int neo$reputation;
    public void neo$refreshCustomerPrice(Player player){
        var v=(Villager)(Object)this;
        if(!VillagerLobotomy.enabled()||v.getTradingPlayer()!=player)return;
        var effect=player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        int hero=effect==null?-1:effect.getAmplifier();
        int reputation=v.getPlayerReputation(player);
        if(player.getUUID().equals(neo$customer)&&hero==neo$hero&&reputation==neo$reputation)return;
        neo$customer=player.getUUID();neo$hero=hero;neo$reputation=reputation;
        updateSpecialPrices(player); // Vanilla resets the previous special price before recalculating.
    }
    @Inject(method="tick",at=@At("TAIL"))
    private void neo$liveEffectPrice(CallbackInfo ci){
        var p=((Villager)(Object)this).getTradingPlayer();
        if(p==null){neo$customer=null;return;}
        neo$refreshCustomerPrice(p);
    }
    @Inject(method="setTradingPlayer",at=@At("TAIL"))
    private void neo$newSession(Player player,CallbackInfo ci){neo$customer=null;}
}
