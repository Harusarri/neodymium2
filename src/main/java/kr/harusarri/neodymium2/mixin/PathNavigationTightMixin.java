package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/**
 * Paper / Purpur / Pufferfish optimization: Backoff redundant A* PathFinder searches
 * for non-combat wandering mobs outside vanillaSensitive regions.
 */
@Mixin(value = PathNavigation.class, priority = 900)
public abstract class PathNavigationTightMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected Path path;
    @Unique private int neo$lastPathSearchTick = -100;

    @Inject(method = "recomputePath", at = @At("HEAD"), cancellable = true)
    private void neo$throttleRecomputePath(CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldThrottlePathSearch(this.mob, this.mob.tickCount - this.neo$lastPathSearchTick)) {
            ci.cancel();
        } else {
            this.neo$lastPathSearchTick = this.mob.tickCount;
        }
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void neo$throttleCreatePath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange, CallbackInfoReturnable<Path> cir) {
        if (this.path != null && !this.path.isDone() && targets != null && targets.contains(this.path.getTarget())
            && OutsideEntityOptimizer.shouldThrottlePathSearch(this.mob, this.mob.tickCount - this.neo$lastPathSearchTick)) {
            cir.setReturnValue(this.path);
        } else {
            this.neo$lastPathSearchTick = this.mob.tickCount;
        }
    }
}
