package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paper / Gale optimization: Throttle frequent ItemEntity AABB neighbour merge scans
 * outside vanillaSensitive regions while keeping 100% Vanilla merge timing inside
 * protected regions (farms, sorters, water streams).
 */
@Mixin(value = ItemEntity.class, priority = 900)
public abstract class ItemEntityTightMixin {
    @Inject(method = "mergeWithNeighbours", at = @At("HEAD"), cancellable = true)
    private void neo$throttleOutsideItemMerge(CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldThrottleItemMerge((ItemEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
