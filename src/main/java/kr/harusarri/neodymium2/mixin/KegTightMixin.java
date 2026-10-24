package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import umpaz.brewinandchewin.common.block.entity.KegBlockEntity;

@Pseudo
@Mixin(value = KegBlockEntity.class, remap = false)
public abstract class KegTightMixin {
    @Shadow
    private int fermentTime;

    @Shadow
    private int fermentTimeTotal;

    @Shadow
    private boolean deferFluidExtraction;

    @Shadow
    private boolean initialisedTemperature;

    @Shadow
    private int burnTime;

    @Shadow
    private boolean burnedByFire;

    @Invoker("hasInput")
    abstract boolean neo$hasInput();

    @Inject(method = "fermentingTick", at = @At("HEAD"), cancellable = true)
    private static void neo$skipIdleKegTick(Level level, BlockPos pos, BlockState state, KegBlockEntity keg, CallbackInfo ci) {
        KegTightMixin self = (KegTightMixin) (Object) keg;
        if (self.fermentTime <= 0 && self.fermentTimeTotal <= 0 && !self.deferFluidExtraction
                && self.burnTime <= 0 && !self.burnedByFire && self.initialisedTemperature
                && OutsideWorldOptimizer.enabled()) {
            long gt = level.getGameTime();
            if (gt % 10L != 0L) {
                if (!self.neo$hasInput()) {
                    OutsideWorldOptimizer.recordSkippedIdleBlockEntityTick();
                    ci.cancel();
                } else if (!Neodymium2.regionProtectsBlock(level, pos) && ((pos.asLong() + gt) & 3L) != 0L) {
                    OutsideWorldOptimizer.recordSkippedIdleBlockEntityTick();
                    ci.cancel();
                }
            }
        }
    }
}
