package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.BrainTaskCache;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.MemoryTypeIndexAccess;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Arrays;
import java.util.Map;

/**
 * Gale / Leaf Flat-Array Direct Memory Indexing for Brain:
 * Replaces HashMap.get(MemoryModuleType) pointer-chasing in Brain.getMemorySlotIfPresent
 * and HashMap$Values iterator allocation in Brain.forgetOutdatedMemories / clearMemories
 * with 100% mathematically identical O(1) flat array lookups while preserving Lithium's
 * MemoryModificationCounter (memoryModCount) invalidation on slot expiry/clearing.
 */
@Mixin(value = Brain.class, priority = 900)
public abstract class BrainTightMixin {
    @Shadow @Final
    private Map<MemoryModuleType<?>, MemorySlot<?>> memories;

    @Unique
    private MemorySlot<?>[] neo$slotByType;

    @Unique
    private MemorySlot<?>[] neo$compactSlots;

    @Unique
    private int neo$compactCount;

    @Inject(method = "registerMemory", at = @At("RETURN"))
    private void neo$indexRegisteredMemory(MemoryModuleType<?> type, CallbackInfo ci) {
        MemorySlot<?> slot = this.memories.get(type);
        if (slot != null && type instanceof MemoryTypeIndexAccess access) {
            int idx = access.neo$getMemoryIndex();
            if (idx >= 0 && idx < 1024) {
                if (this.neo$slotByType == null) {
                    this.neo$slotByType = new MemorySlot<?>[Math.max(160, idx + 32)];
                } else if (idx >= this.neo$slotByType.length) {
                    this.neo$slotByType = Arrays.copyOf(this.neo$slotByType, Math.max(this.neo$slotByType.length * 2, idx + 32));
                }
                if (this.neo$slotByType[idx] == null) {
                    this.neo$slotByType[idx] = slot;
                    if (this.neo$compactSlots == null) {
                        this.neo$compactSlots = new MemorySlot<?>[16];
                    } else if (this.neo$compactCount >= this.neo$compactSlots.length) {
                        this.neo$compactSlots = Arrays.copyOf(this.neo$compactSlots, this.neo$compactSlots.length * 2);
                    }
                    this.neo$compactSlots[this.neo$compactCount++] = slot;
                }
            }
        }
    }

    @Inject(method = "getMemorySlotIfPresent", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private <T> void neo$fastGetMemorySlotIfPresent(MemoryModuleType<T> type, CallbackInfoReturnable<MemorySlot<T>> cir) {
        if (OutsideWorldOptimizer.enabled() && this.neo$compactCount == this.memories.size() && type instanceof MemoryTypeIndexAccess access) {
            OutsideWorldOptimizer.recordFastBrainMemoryLookup();
            MemorySlot<?>[] table = this.neo$slotByType;
            if (table == null) {
                cir.setReturnValue(null);
                return;
            }
            int idx = access.neo$getMemoryIndex();
            cir.setReturnValue(idx >= 0 && idx < table.length ? (MemorySlot<T>) table[idx] : null);
        }
    }

    @Inject(method = "forgetOutdatedMemories", at = @At("HEAD"), cancellable = true)
    private void neo$fastForgetOutdatedMemories(CallbackInfo ci) {
        if (OutsideWorldOptimizer.enabled() && this.neo$compactCount == this.memories.size()) {
            MemorySlot<?>[] slots = this.neo$compactSlots;
            boolean expiredAny = false;
            for (int i = 0, n = this.neo$compactCount; i < n; i++) {
                MemorySlot<?> slot = slots[i];
                if (slot.canExpire()) {
                    slot.tick();
                    if (!slot.hasValue()) {
                        expiredAny = true;
                    }
                }
            }
            if (expiredAny) {
                BrainTaskCache.notifyMemoryModified((Brain<?>) (Object) this);
            }
            ci.cancel();
        }
    }

    @Inject(method = "clearMemories", at = @At("HEAD"), cancellable = true)
    private void neo$fastClearMemories(CallbackInfo ci) {
        if (OutsideWorldOptimizer.enabled() && this.neo$compactCount == this.memories.size()) {
            MemorySlot<?>[] slots = this.neo$compactSlots;
            boolean clearedAny = false;
            for (int i = 0, n = this.neo$compactCount; i < n; i++) {
                MemorySlot<?> slot = slots[i];
                if (slot.hasValue()) {
                    clearedAny = true;
                }
                slot.clear();
            }
            if (clearedAny) {
                BrainTaskCache.notifyMemoryModified((Brain<?>) (Object) this);
            }
            ci.cancel();
        }
    }
}
