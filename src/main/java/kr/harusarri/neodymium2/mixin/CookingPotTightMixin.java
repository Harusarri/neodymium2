package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

@Pseudo
@Mixin(value = CookingPotBlockEntity.class, remap = false)
public abstract class CookingPotTightMixin {
    @Shadow
    private int cookTime;

    @Shadow
    public abstract ItemStack getMeal();

    @Invoker("hasInput")
    abstract boolean neo$hasInput();

    @Inject(method = "cookingTick", at = @At("HEAD"), cancellable = true)
    private static void neo$skipIdleCookingPotTick(ServerLevel level, BlockPos pos, BlockState state, CookingPotBlockEntity cookingPot, CallbackInfo ci) {
        CookingPotTightMixin self = (CookingPotTightMixin) (Object) cookingPot;
        if (self.cookTime == 0 && self.getMeal().isEmpty() && OutsideWorldOptimizer.enabled()) {
            if (!self.neo$hasInput()) {
                OutsideWorldOptimizer.recordSkippedIdleBlockEntityTick();
                ci.cancel();
            } else if (!Neodymium2.regionProtectsBlock(level, pos) && ((pos.asLong() + level.getGameTime()) & 3L) != 0L) {
                OutsideWorldOptimizer.recordSkippedIdleBlockEntityTick();
                ci.cancel();
            }
        }
    }
}
