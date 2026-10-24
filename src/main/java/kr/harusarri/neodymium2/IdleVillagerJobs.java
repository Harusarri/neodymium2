package kr.harusarri.neodymium2;
import kr.harusarri.neodymium2.mixin.BrainMaintenanceAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import java.util.*;

/** Drive the existing native job acquisition behavior; hand movement/assignment back to Brain. */
public final class IdleVillagerJobs {
    private Brain<?> brain;
    private BehaviorControl<Villager> acquire;
    private long generation=-1,nextScan,nextChanges,revision;
    private int interval=40,lastX,lastZ;
    private static long attempts,found;
    public static long attempts(){return attempts;}
    public static String status(){return " · 무직 작업대 탐색/발견 "+attempts+"/"+found;}
    public void brainChanged(Villager v){if(brain!=null&&brain!=v.getBrain()){brain=null;acquire=null;generation=-1;}}
    @SuppressWarnings("unchecked")
    public boolean search(Villager v,ServerLevel level,long now){
        if(brain!=v.getBrain()){
            brain=v.getBrain();acquire=null;
            var core=((BrainMaintenanceAccess)brain).neo$behaviors().get(6);
            var candidates=core==null?null:core.get(Activity.CORE);
            if(candidates!=null&&candidates.size()==1)acquire=(BehaviorControl<Villager>)candidates.iterator().next();
            generation=-1;
        }
        if(acquire==null)return false; // Unknown Brain layout: preserve normal behavior.
        var cfg=Neodymium2.snapshot();
        if(generation!=cfg.generation()){
            generation=cfg.generation();interval=40;nextScan=now+Math.floorMod(v.getId(),20);nextChanges=now;
            revision=((PoiChanges)level.getPoiManager()).neo$revisionNear(v.blockPosition());
            lastX=v.getBlockX();lastZ=v.getBlockZ();
        }
        if(now>=nextChanges){
            nextChanges=now+20;
            long current=((PoiChanges)level.getPoiManager()).neo$revisionNear(v.blockPosition());
            if(current!=revision||lastX!=v.getBlockX()||lastZ!=v.getBlockZ()){
                interval=40;nextScan=Math.min(nextScan,now+Math.floorMod(v.getId(),20));
                revision=current;lastX=v.getBlockX();lastZ=v.getBlockZ();
            }
        }
        if(now>=nextScan){
            var before=acquire.getStatus();boolean ran=acquire.tryStart(level,v,now);
            if(acquire.getStatus()==Behavior.Status.RUNNING)acquire.tickOrStop(level,v,now);
            if(before!=acquire.getStatus())BrainTaskCache.invalidate(brain);
            if(ran){attempts++;int max=cfg.settings().villagerLobotomy().jobSearchMaxIntervalTicks();nextScan=now+Math.min(interval+Math.floorMod(v.getId(),20),max);interval=Math.min(interval*2,max);}
            else nextScan=now+20;
        }
        if(brain.hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE)){found++;return false;}
        return true;
    }
}
