package kr.harusarri.neodymium2.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {SignBlock.class, CeilingHangingSignBlock.class, WallHangingSignBlock.class}, priority = 900)
public abstract class SignBlockTightMixin {
    @Inject(method = "getTicker", at = @At("HEAD"), cancellable = true)
    private <T extends BlockEntity> void neo$elideServerSignTicker(Level level, BlockState state, BlockEntityType<T> type, CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (!level.isClientSide()) {
            cir.setReturnValue(null);
        }
    }
}
