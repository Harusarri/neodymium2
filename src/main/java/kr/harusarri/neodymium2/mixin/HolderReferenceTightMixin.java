package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
import java.util.Set;

/**
 * Pluto / Leaf Holder.Reference#is(TagKey) Identity Fast-Path:
 * Since TagKey.create() interns all TagKey instances globally in TagKey.VALUES,
 * replaces ImmutableCollections$SetN.contains(TagKey) (which computes Record.hashCode()
 * and Record.equals() on ResourceKey + Identifier) with:
 * - Mode 1 (0 tags): instant false
 * - Mode 2 (1..4 tags): inline register pointer comparisons (tag == t0..t3)
 * - Mode 3 (>4 tags): open-addressed power-of-2 identity table probed by System.identityHashCode(tag)
 */
@Mixin(value = Holder.Reference.class, priority = 900)
public abstract class HolderReferenceTightMixin<T> {
    @Shadow
    private Set<TagKey<T>> tags;

    @Unique
    private Set<TagKey<T>> neo$boundTagsRef;

    @Unique
    private byte neo$tagMode;

    @Unique
    private TagKey<?> neo$t0, neo$t1, neo$t2, neo$t3;

    @Unique
    private TagKey<?>[] neo$identityTable;

    @Inject(method = "bindTags", at = @At("RETURN"))
    private void neo$buildFastTagTableOnBind(Collection<TagKey<T>> collection, CallbackInfo ci) {
        this.neo$rebuildFastTagTable(this.tags);
    }

    @Unique
    private void neo$rebuildFastTagTable(Set<TagKey<T>> current) {
        this.neo$boundTagsRef = current;
        this.neo$t0 = null;
        this.neo$t1 = null;
        this.neo$t2 = null;
        this.neo$t3 = null;
        this.neo$identityTable = null;
        if (current == null) {
            this.neo$tagMode = 0;
            return;
        }
        int size = current.size();
        if (size == 0) {
            this.neo$tagMode = 1;
            return;
        }
        if (size <= 4) {
            int i = 0;
            for (TagKey<T> k : current) {
                switch (i++) {
                    case 0 -> this.neo$t0 = k;
                    case 1 -> this.neo$t1 = k;
                    case 2 -> this.neo$t2 = k;
                    default -> this.neo$t3 = k;
                }
            }
            this.neo$tagMode = 2;
            return;
        }
        int cap = Integer.highestOneBit(size * 2 - 1) << 1;
        if (cap < 16) cap = 16;
        TagKey<?>[] table = new TagKey<?>[cap];
        int mask = cap - 1;
        for (TagKey<T> k : current) {
            int slot = ((System.identityHashCode(k) * 0x9E3779B9) >>> 16) & mask;
            while (table[slot] != null) {
                slot = (slot + 1) & mask;
            }
            table[slot] = k;
        }
        this.neo$identityTable = table;
        this.neo$tagMode = 3;
    }

    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void neo$fastIsTag(TagKey<T> tag, CallbackInfoReturnable<Boolean> cir) {
        Set<TagKey<T>> current = this.tags;
        if (current == null || !OutsideWorldOptimizer.enabled()) {
            return;
        }
        if (current != this.neo$boundTagsRef) {
            this.neo$rebuildFastTagTable(current);
        }
        OutsideWorldOptimizer.recordFastTagBitLookup();
        byte mode = this.neo$tagMode;
        if (mode == 1) {
            cir.setReturnValue(false);
            return;
        }
        if (mode == 2) {
            cir.setReturnValue(tag == this.neo$t0 || tag == this.neo$t1 || tag == this.neo$t2 || tag == this.neo$t3);
            return;
        }
        if (mode == 3) {
            TagKey<?>[] tab = this.neo$identityTable;
            int mask = tab.length - 1;
            int slot = ((System.identityHashCode(tag) * 0x9E3779B9) >>> 16) & mask;
            while (true) {
                TagKey<?> k = tab[slot];
                if (k == tag) {
                    cir.setReturnValue(true);
                    return;
                }
                if (k == null) {
                    cir.setReturnValue(false);
                    return;
                }
                slot = (slot + 1) & mask;
            }
        }
    }
}
