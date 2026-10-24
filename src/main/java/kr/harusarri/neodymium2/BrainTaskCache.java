package kr.harusarri.neodymium2;
import net.minecraft.world.entity.ai.Brain;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/** Lithium's launch optimization caches running tasks around Brain's own loops,
 * and memory_changes caches Behavior entry conditions by memoryModCount.
 * External maintenance must invalidate/increment them when mutating Brain state directly. */
public final class BrainTaskCache {
    private static final Field RUNNING = find();
    private static final MethodHandle ON_MEMORY_MODIFIED = findMemoryModified();

    private static Field find() {
        try {
            var field = Brain.class.getDeclaredField("runningTasks");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException absent) {
            return null;
        }
    }

    private static MethodHandle findMemoryModified() {
        try {
            return MethodHandles.lookup().findVirtual(
                Brain.class,
                "lithium$onMemoryModified",
                MethodType.methodType(void.class)
            );
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }

    public static void invalidate(Brain<?> brain) {
        if (RUNNING == null) return;
        try {
            RUNNING.set(brain, null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot refresh Lithium Brain task cache", ex);
        }
    }

    public static void notifyMemoryModified(Brain<?> brain) {
        if (ON_MEMORY_MODIFIED == null) return;
        try {
            ON_MEMORY_MODIFIED.invokeExact(brain);
        } catch (Throwable ex) {
            throw new IllegalStateException("Cannot notify Lithium Brain memory modification counter", ex);
        }
    }

    private BrainTaskCache() {}
}
