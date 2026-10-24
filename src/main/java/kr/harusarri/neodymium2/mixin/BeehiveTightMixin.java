package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BeehiveBlockEntity.class, priority = 900)
public abstract class BeehiveTightMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void neo$skipEmptyBeehiveTick(Level level, BlockPos pos, BlockState state, BeehiveBlockEntity hive, CallbackInfo ci) {
        if (OutsideWorldOptimizer.shouldSkipEmptyBeehiveTick(hive.isEmpty())) {
            ci.cancel();
        }
    }
}
