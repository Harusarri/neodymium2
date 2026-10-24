package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BellBlockEntity.class, priority = 900)
public abstract class BellTightMixin {
    @Shadow private boolean resonating;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void neo$skipIdleBellTick(Level level, BlockPos pos, BlockState state, BellBlockEntity bell, CallbackInfo ci) {
        if (OutsideWorldOptimizer.shouldSkipIdleBellTick(bell.shaking, ((BellTightMixin) (Object) bell).resonating)) {
            ci.cancel();
        }
    }
}
