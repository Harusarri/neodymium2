package kr.harusarri.neodymium2;
import net.minecraft.world.entity.ai.Brain;
import java.lang.reflect.Field;

/** Lithium's launch optimization caches running tasks around Brain's own loops.
 * External maintenance must invalidate it when changing a vanilla task's status.
 * When Lithium/the launch optimization is absent, vanilla derives the list itself. */
public final class BrainTaskCache {
    private static final Field RUNNING=find();
    private static Field find(){
        try{var field=Brain.class.getDeclaredField("runningTasks");field.setAccessible(true);return field;}
        catch(NoSuchFieldException absent){return null;}
    }
    public static void invalidate(Brain<?> brain){
        if(RUNNING==null)return;
        try{RUNNING.set(brain,null);}catch(IllegalAccessException ex){throw new IllegalStateException("Cannot refresh Lithium Brain task cache",ex);}
    }
    private BrainTaskCache(){}
}
