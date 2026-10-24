package kr.harusarri.neodymium2.mixin;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.*;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.sensing.*;
import net.minecraft.world.entity.schedule.Activity;
@Mixin(Brain.class)
public interface BrainMaintenanceAccess {
    @Invoker("forgetOutdatedMemories") void neo$forgetOutdatedMemories();
    @Accessor("availableBehaviorsByPriority") Map<Integer,Map<Activity,Set<BehaviorControl<?>>>> neo$behaviors();
    @Accessor("sensors") Map<SensorType<?>,Sensor<?>> neo$sensors();
}
