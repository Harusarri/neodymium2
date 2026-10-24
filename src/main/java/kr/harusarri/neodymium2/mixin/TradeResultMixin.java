package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.VillagerTrading;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MerchantResultSlot.class)
public abstract class TradeResultMixin {
    @Shadow @Final private Merchant merchant;
    @Inject(method="mayPickup",at=@At("HEAD"))
    private void neo$validateCurrentPrice(Player player,CallbackInfoReturnable<Boolean> cir){if(merchant instanceof VillagerTrading v)v.neo$refreshCustomerPrice(player);}
}
