package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.VillagerTrading;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerMenu.class)
public abstract class TradeClickMixin {
    @Inject(method="clicked",at=@At("HEAD"))
    private void neo$priceBeforeTransaction(int slot,int button,ContainerInput input,Player player,CallbackInfo ci){
        if((Object)this instanceof MerchantMenuAccess menu&&menu.neo$merchant() instanceof VillagerTrading v)v.neo$refreshCustomerPrice(player);
    }
}
