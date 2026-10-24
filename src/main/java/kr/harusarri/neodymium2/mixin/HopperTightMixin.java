package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HopperBlockEntity.class, priority = 900)
public abstract class HopperTightMixin {
    @Shadow private int cooldownTime;

    @Inject(method = "pushItemsTick", at = @At("HEAD"), cancellable = true)
    private static void neo$throttleOutsideIdleHopperPoll(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, CallbackInfo ci) {
        if (OutsideWorldOptimizer.shouldSkipIdleHopperPoll(level, pos, ((HopperTightMixin) (Object) hopper).cooldownTime)) {
            ci.cancel();
        }
    }
}
