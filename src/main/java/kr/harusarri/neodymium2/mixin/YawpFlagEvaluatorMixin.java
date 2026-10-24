package kr.harusarri.neodymium2.mixin;

import de.z0rdak.yawp.api.FlagEvaluator;
import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.EntityFastStateAccess;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caches YAWP FlagEvaluator.checkMobGrief(Entity, CallbackInfo) per (region generation, entity blockPosition,
 * 20-tick window) so stationary mobs with canPickUpLoot() (such as all 275 Villagers) do not allocate
 * RegionDataManager Stream pipelines and call getOnPos() on every single tick.
 */
@Pseudo
@Mixin(value = FlagEvaluator.class, remap = false)
public abstract class YawpFlagEvaluatorMixin {
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
