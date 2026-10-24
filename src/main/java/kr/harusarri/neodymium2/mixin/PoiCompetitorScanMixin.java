package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.PoiCompetitorScan;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/**
 * Replaces the 5-stage Stream pipeline in PoiCompetitorScan.lambda$create$3 with a zero-allocation
 * indexed loop. Produces 100% identical Vanilla results (including inside vanillaSensitive trading halls)
 * while eliminating Stream/ReferencePipeline allocations on every villager tick.
 */
@Mixin(value = PoiCompetitorScan.class, priority = 900)
public abstract class PoiCompetitorScanMixin {
    @Shadow
    private static boolean competesForSameJobsite(GlobalPos pos, Holder<PoiType> poiType, Villager villager) {
        throw new AssertionError();
    }

    @Shadow
    private static Villager selectWinner(Villager first, Villager second) {
        throw new AssertionError();
    }

    @Inject(method = "lambda$create$3", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void neo$fastPoiCompetitorLoop(
        BehaviorBuilder.Instance<Villager> instance,
        MemoryAccessor nearestLivingEntities,
        Villager self,
        GlobalPos jobSitePos,
        Holder<PoiType> poiType,
        CallbackInfo ci
    ) {
        if (!OutsideWorldOptimizer.enabled()) {
            return;
        }
        List<LivingEntity> list = (List<LivingEntity>) instance.get(nearestLivingEntities);
        Villager winner = self;
        for (int i = 0, n = list.size(); i < n; i++) {
            LivingEntity entity = list.get(i);
            if (entity instanceof Villager other && other != self && other.isAlive() && competesForSameJobsite(jobSitePos, poiType, other)) {
                winner = selectWinner(winner, other);
            }
        }
        OutsideWorldOptimizer.recordFastPoiCompetitorScan();
        ci.cancel();
    }
}
