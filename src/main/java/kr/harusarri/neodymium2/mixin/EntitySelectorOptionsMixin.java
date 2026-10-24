package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.EntityFastStateAccess;
import kr.harusarri.neodymium2.core.TagSelectorPredicate;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntitySelectorOptions.class, priority = 900)
public abstract class EntitySelectorOptionsMixin {
    @Shadow @Final
    private static Logger LOGGER;

    @Inject(method = "lambda$bootStrap$42", at = @At("HEAD"), cancellable = true)
    private static void neo$parseIndexableTagPredicate(EntitySelectorParser parser, CallbackInfo ci) {
        boolean invert = parser.shouldInvertValue();
        String tag = parser.getReader().readUnquotedString();
        parser.addPredicate(new TagSelectorPredicate(tag, invert));
        ci.cancel();
    }

    @Inject(method = "lambda$bootStrap$45", at = @At("HEAD"), cancellable = true)
    private static void neo$fastPathAndCacheSelectorNbt(
        CompoundTag expected,
        boolean invert,
        Entity entity,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!OutsideWorldOptimizer.enabled()) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            ItemStack selected = player.getInventory().getSelectedItem();
            if (expected.size() == 1 && expected.contains("SelectedItem")) {
                if (selected.isEmpty()) {
                    OutsideWorldOptimizer.recordFastSelectorNbtCheck();
                    cir.setReturnValue(invert);
                    return;
                }
                try (ProblemReporter.ScopedCollector collector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
                    TagValueOutput output = TagValueOutput.createWithContext(collector, entity.registryAccess());
                    output.store("SelectedItem", ItemStack.CODEC, selected);
                    boolean match = NbtUtils.compareNbt(expected.get("SelectedItem"), output.buildResult().get("SelectedItem"), true);
                    OutsideWorldOptimizer.recordFastSelectorNbtCheck();
                    cir.setReturnValue(match != invert);
                    return;
                }
            }
            if (expected.size() == 1 && expected.contains("Inventory") && player.getInventory().isEmpty()) {
                Tag expInv = expected.get("Inventory");
                if (expInv instanceof ListTag list && !list.isEmpty()) {
                    OutsideWorldOptimizer.recordFastSelectorNbtCheck();
                    cir.setReturnValue(invert);
                    return;
                }
            }
        }
        if (entity instanceof EntityFastStateAccess access && entity.level() != null) {
            long gt = entity.level().getGameTime();
            ItemStack sel = entity instanceof ServerPlayer sp ? sp.getInventory().getSelectedItem() : ItemStack.EMPTY;
            CompoundTag cached = access.neo$getSelectorNbtTag();
            if (cached != null && access.neo$getSelectorNbtTick() == gt && access.neo$getSelectorNbtSelectedItem() == sel) {
                boolean match = NbtUtils.compareNbt(expected, cached, true);
                OutsideWorldOptimizer.recordFastSelectorNbtCheck();
                cir.setReturnValue(match != invert);
                return;
            }
            try (ProblemReporter.ScopedCollector collector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
                TagValueOutput output = TagValueOutput.createWithContext(collector, entity.registryAccess());
                entity.saveWithoutId(output);
                if (entity instanceof ServerPlayer sp && !sel.isEmpty()) {
                    output.store("SelectedItem", ItemStack.CODEC, sel);
                }
                CompoundTag built = output.buildResult();
                access.neo$setSelectorNbtCache(gt, sel, built);
                boolean match = NbtUtils.compareNbt(expected, built, true);
                cir.setReturnValue(match != invert);
            }
        }
    }
}
