package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Short-circuits Carpet-TIS-Addition's MicroTimingLoggerManager stage setters when microTiming
 * logging is disabled (!isLoggerActivated()), avoiding hundreds of thousands of Optional.ifPresent
 * and lambda allocations per second across ticks, chunks, block entities, and raids.
 */
@Pseudo
@Mixin(targets = "carpettisaddition.logging.loggers.microtiming.MicroTimingLoggerManager", remap = false)
public abstract class MicroTimingLoggerManagerMixin {
    @Shadow
    public static boolean isLoggerActivated() {
        return false;
    }

    @Inject(
        method = {
            "setTickStage(Lnet/minecraft/world/level/Level;Lcarpettisaddition/logging/loggers/microtiming/enums/TickStage;)V",
            "setTickStage(Lcarpettisaddition/logging/loggers/microtiming/enums/TickStage;)V",
            "setTickStageDetail(Lnet/minecraft/world/level/Level;Ljava/lang/String;)V",
            "setSubTickStage(Lnet/minecraft/world/level/Level;Lcarpettisaddition/logging/loggers/microtiming/tickphase/substages/AbstractSubStage;)V",
            "setSubTickStage(Lcarpettisaddition/logging/loggers/microtiming/tickphase/substages/AbstractSubStage;)V",
            "setCurrentWorld(Lnet/minecraft/server/level/ServerLevel;)V"
        },
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void neo$shortCircuitInactiveMicroTimingStage(CallbackInfo ci) {
        if (!isLoggerActivated() && OutsideWorldOptimizer.enabled()) {
            OutsideWorldOptimizer.recordSkippedMicroTimingCall();
            ci.cancel();
        }
    }
}
