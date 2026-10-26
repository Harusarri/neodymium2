package kr.harusarri.neodymium2.mixin;

import de.z0rdak.yawp.api.FlagEvaluator;
import de.z0rdak.yawp.core.region.IMarkableRegion;
import de.z0rdak.yawp.data.region.RegionDataManager;
import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.EntityFastStateAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;

/**
 * Caches YAWP FlagEvaluator.checkMobGrief(Entity, CallbackInfo) per (region generation, entity blockPosition,
 * 20-tick window) so stationary mobs with canPickUpLoot() (such as all 275 Villagers) do not allocate
 * RegionDataManager Stream pipelines and call getOnPos() on every single tick, and replaces
 * getInvolvedRegionFor's per-block Stream + ArrayList allocation pipeline with a zero-allocation scan.
 */
@Pseudo
@Mixin(value = FlagEvaluator.class, remap = false)
public abstract class YawpFlagEvaluatorMixin {
    @Inject(
        method = "getInvolvedRegionFor(Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;)Lde/z0rdak/yawp/core/region/IMarkableRegion;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void neo$fastGetInvolvedRegionFor(BlockPos pos, ResourceKey<Level> dim, CallbackInfoReturnable<IMarkableRegion> cir) {
        if (!OutsideWorldOptimizer.enabled()) {
            return;
        }
        Collection<IMarkableRegion> regions = RegionDataManager.getLocalsFor(dim);
        if (regions == null || regions.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }
        IMarkableRegion best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (IMarkableRegion region : regions) {
            if (region != null && region.isActive()) {
                int prio = region.getPriority();
                if ((best == null || prio > bestPriority) && region.contains(pos)) {
                    best = region;
                    bestPriority = prio;
                }
            }
        }
        OutsideWorldOptimizer.recordCachedYawpFlagEval();
        cir.setReturnValue(best);
    }
    @Inject(
        method = "checkMobGrief(Lnet/minecraft/world/entity/Entity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void neo$checkCachedMobGrief(Entity entity, CallbackInfo targetCi, CallbackInfo selfCi) {
        if (!OutsideWorldOptimizer.enabled() || !(entity instanceof EntityFastStateAccess access)) {
            return;
        }
        long gen = Neodymium2.snapshot().generation();
        long posLong = entity.blockPosition().asLong();
        int tick = entity.tickCount;
        if (access.neo$getYawpMobGriefGen() == gen
            && access.neo$getYawpMobGriefPos() == posLong
            && tick >= 0
            && tick <= access.neo$getYawpMobGriefUntilTick()) {
            if (access.neo$isYawpMobGriefCancelled()) {
                targetCi.cancel();
            }
            OutsideWorldOptimizer.recordCachedYawpFlagEval();
            selfCi.cancel();
        }
    }

    @Inject(
        method = "checkMobGrief(Lnet/minecraft/world/entity/Entity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
        at = @At("RETURN")
    )
    private static void neo$storeCachedMobGrief(Entity entity, CallbackInfo targetCi, CallbackInfo selfCi) {
        if (OutsideWorldOptimizer.enabled() && entity instanceof EntityFastStateAccess access) {
            access.neo$setYawpMobGriefCache(
                Neodymium2.snapshot().generation(),
                entity.blockPosition().asLong(),
                entity.tickCount + 20,
                targetCi.isCancelled()
            );
        }
    }
}
