package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.AdvancementOptimizer;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;

@Mixin(SimpleCriterionTrigger.class)
public abstract class SimpleCriterionTriggerMixin<T extends SimpleCriterionTrigger.SimpleInstance> {
    @Inject(method = "trigger", at = @At("HEAD"), cancellable = true)
    private void neo$filterOrStrideTrigger(ServerPlayer player, Predicate<T> testTrigger, CallbackInfo ci) {
        if (AdvancementOptimizer.shouldSkipTrigger((SimpleCriterionTrigger<?>) (Object) this, player)) {
            ci.cancel();
        }
    }
}
