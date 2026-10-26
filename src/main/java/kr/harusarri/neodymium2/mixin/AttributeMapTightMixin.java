package kr.harusarri.neodymium2.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/**
 * Pufferfish 0014 AttributeMap.getInstance Allocation Elision:
 * Looks up existing AttributeInstances and checks supplier.hasAttribute(attribute) before
 * falling through to computeIfAbsent, eliminating per-lookup capturing Function lambda allocations.
 * 100% Vanilla-identical behavior and compatible with Lithium's AttributeMapMixin (<init> only).
 */
@Mixin(value = AttributeMap.class, priority = 900)
public abstract class AttributeMapTightMixin {
    @Shadow @Final
    private Map<Holder<Attribute>, AttributeInstance> attributes;

    @Shadow @Final
    private AttributeSupplier supplier;

    @Inject(method = "getInstance", at = @At("HEAD"), cancellable = true)
    private void neo$fastGetInstance(Holder<Attribute> attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        AttributeInstance existing = this.attributes.get(attribute);
        if (existing != null) {
            cir.setReturnValue(existing);
            return;
        }
        if (!this.supplier.hasAttribute(attribute)) {
            cir.setReturnValue(null);
        }
    }
}
