package kr.harusarri.neodymium2.mixin;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccess {
    @Accessor("trader") Merchant neo$merchant();
}
