package kr.harusarri.neodymium2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.ServerLevelTagAccess;
import kr.harusarri.neodymium2.core.TagSelectorPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = EntitySelector.class, priority = 900)
public abstract class EntitySelectorMixin {
    @Unique
    private static final Entity[] NEO$EMPTY_ENTITIES = new Entity[0];

    @Shadow @Final
    private List<Predicate<Entity>> contextFreePredicates;

    @Shadow @Final
    private MinMaxBounds.Doubles range;

    @Shadow @Final
    private AABB aabb;

    @Unique
    private String neo$requiredPositiveTag;

    @Unique
    private boolean neo$topologyMemoizable;

    @Unique
    private final ServerLevel[] neo$memoLevels = new ServerLevel[4];

    @Unique
    private final long[] neo$memoTicks = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};

    @Unique
    private final long[] neo$memoRevs = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};

    @Unique
    private final Entity[][] neo$memoEntities = new Entity[4][];

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neo$extractRequiredPositiveTagAndMemoizability(CallbackInfo ci) {
        if (this.contextFreePredicates != null) {
            for (Predicate<Entity> p : this.contextFreePredicates) {
                if (p instanceof TagSelectorPredicate tagPred && !tagPred.invert() && !tagPred.tag().isEmpty()) {
                    this.neo$requiredPositiveTag = tagPred.tag();
                    break;
                }
            }
        }
        this.neo$topologyMemoizable = this.aabb == null && (this.range == null || this.range.isAny());
    }

    @Unique
    private static int neo$levelSlot(ServerLevel level) {
        var dim = level.dimension();
        if (dim == Level.OVERWORLD) return 0;
        if (dim == Level.NETHER) return 1;
        if (dim == Level.END) return 2;
        return 3;
    }

    @WrapMethod(method = "addEntities")
    private void neo$shortCircuitOrScopeRequiredTag(
        List<Entity> result,
        ServerLevel level,
        AABB aabb,
        Predicate<Entity> predicate,
        Operation<Void> original
    ) {
        if (!OutsideWorldOptimizer.enabled()) {
            original.call(result, level, aabb, predicate);
            return;
        }
        boolean canMemo = this.neo$topologyMemoizable && aabb == null;
        int slot = neo$levelSlot(level);
        long tick = level.getGameTime();
        long rev = OutsideWorldOptimizer.entityTopologyRevision();
        if (canMemo
            && this.neo$memoLevels[slot] == level
            && this.neo$memoTicks[slot] == tick
            && this.neo$memoRevs[slot] == rev
            && this.neo$memoEntities[slot] != null) {
            OutsideWorldOptimizer.recordMemoizedSelectorHit();
            for (Entity e : this.neo$memoEntities[slot]) {
                if (!e.isRemoved()) {
                    result.add(e);
                }
            }
            return;
        }
        int startSize = result.size();
        String reqTag = this.neo$requiredPositiveTag;
        if (reqTag != null) {
            if (level instanceof ServerLevelTagAccess levelAccess) {
                var tagLookup = levelAccess.neo$getEntityLookupTagAccess();
                if (tagLookup != null && !tagLookup.neo$hasEntitiesWithTag(reqTag)) {
                    OutsideWorldOptimizer.recordFastTagLookup();
                    if (canMemo) {
                        this.neo$memoLevels[slot] = level;
                        this.neo$memoTicks[slot] = tick;
                        this.neo$memoRevs[slot] = rev;
                        this.neo$memoEntities[slot] = NEO$EMPTY_ENTITIES;
                    }
                    return;
                }
            }
            String prev = OutsideWorldOptimizer.pushRequiredSelectorTag(reqTag);
            try {
                original.call(result, level, aabb, predicate);
            } finally {
                OutsideWorldOptimizer.popRequiredSelectorTag(prev);
            }
        } else {
            original.call(result, level, aabb, predicate);
        }
        if (canMemo) {
            int added = result.size() - startSize;
            this.neo$memoLevels[slot] = level;
            this.neo$memoTicks[slot] = tick;
            this.neo$memoRevs[slot] = OutsideWorldOptimizer.entityTopologyRevision();
            this.neo$memoEntities[slot] = added <= 0
                ? NEO$EMPTY_ENTITIES
                : result.subList(startSize, result.size()).toArray(Entity[]::new);
        }
    }
}
