package kr.harusarri.neodymium2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ServerExplosion.class, priority = 900)
public abstract class ExplosionTightMixin {
    @WrapMethod(method = "getSeenPercent")
    private static float neo$cacheOutsideExplosionSeenPercent(Vec3 source, Entity entity, Operation<Float> original) {
        return OutsideWorldOptimizer.getSeenPercent(source, entity, original);
    }
}
