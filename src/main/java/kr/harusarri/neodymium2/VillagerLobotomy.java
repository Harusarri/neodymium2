package kr.harusarri.neodymium2;

import kr.harusarri.neodymium2.core.FastEntityTagSet;
import kr.harusarri.neodymium2.mixin.BrainMaintenanceAccess;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.schedule.Activity;

/** LibertyEngine's trader dormancy, with vanilla bookkeeping and reversible region/name exemptions. */
public final class VillagerLobotomy {
    private static long skipped,vanilla;
    private static final ThreadLocal<Integer> brainScope=ThreadLocal.withInitial(()->0);
    private VillagerLobotomy() {}
    public static boolean enabled(){var s=Neodymium2.snapshot().settings();return s.enabled()&&s.villagerLobotomy().enabled();}
    public static void enterBrainScope(){brainScope.set(brainScope.get()+1);}
    public static void exitBrainScope(){brainScope.set(brainScope.get()-1);}
    public static boolean ownsCurrentBrainCall(){return enabled()&&brainScope.get()>0;}
    public static String status(){return " · 주민 AI 절약 "+(enabled()?"ON":"OFF")+" · Brain 생략/정상 "+skipped+"/"+vanilla+" · 대기 주민 "+(Neodymium2.snapshot().settings().villagerLobotomy().idleVillagersEnabled()?"ON":"OFF")+IdleVillagerJobs.status();}
    private static boolean hasExemptTag(Villager v){
        var tags=v.entityTags();
        if(tags instanceof FastEntityTagSet fast){
            return fast.hasHotFlag(FastEntityTagSet.FLAG_EXCLUDE_EAR|FastEntityTagSet.FLAG_EXCLUDE_LOBOTOMIZATION);
        }
        return tags.contains("exclude_lobotomization")||tags.contains("exclude_ear");
    }
    public static final class State {
        private final WorkAtPoi workstation=new WorkAtPoi();
        private final VillagerGifts gifts=new VillagerGifts();
        private final IdleVillagerJobs jobs=new IdleVillagerJobs();
        private long awakeUntil,generation=-1,nextStationCheck,nextMaintenance;
        private GlobalPos cachedJob;
        private boolean stationValid,dormant;
        private long nextIdleRaidCheck;
        private boolean idleRaid;
        public boolean dormant(){return dormant;}
        public void wake(Villager v,int ticks){awakeUntil=Math.max(awakeUntil,v.level().getGameTime()+ticks);}
        private boolean validStation(Villager v,ServerLevel level,long now){
            var job=v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
            if(job==null||!job.dimension().equals(level.dimension())||!job.pos().closerToCenterThan(v.position(),1.73))return false;
            if(!job.equals(cachedJob)||now>=nextStationCheck){
                cachedJob=job;nextStationCheck=now+Neodymium2.snapshot().settings().villagerLobotomy().maintenanceIntervalTicks();
                var chunk=level.getChunkSource().getChunkNow(job.pos().getX()>>4,job.pos().getZ()>>4);
                stationValid=chunk!=null&&PoiTypes.forState(chunk.getBlockState(job.pos())).filter(v.getVillagerData().profession().value().heldJobSite()).isPresent();
                var raid=level.getRaidAt(v.blockPosition());if(raid!=null&&raid.isActive()&&!raid.isOver())stationValid=false;
            }
            return stationValid;
        }
        private boolean eligible(Villager v,ServerLevel level,long now){
            if(!enabled()||Neodymium2.regionProtects(v))return false;
            var cfg=Neodymium2.snapshot().settings().villagerLobotomy();
            if(v.hasCustomName())OutsideSpawnAndDensity.recordBlockedNametagBypass(v);
            if(hasExemptTag(v))return false;
            if(v.isNoAi()||v.isTrading()||v.isSleeping())return false;
            var profession=v.getVillagerData().profession();
            if(!cfg.idleVillagersEnabled()&&(v.isBaby()||v.getVillagerXp()<=0||profession.is(VillagerProfession.NONE)||profession.is(VillagerProfession.NITWIT)||profession.is(VillagerProfession.FARMER)))return false;
            if(profession.unwrapKey().map(k->!k.identifier().getNamespace().equals("minecraft")).orElse(true))return false;
            if(v.hurtTime>0){wake(v,cfg.wakeAfterHurtTicks());return false;}
            if(now<awakeUntil||!v.onGround()||v.isInWater()||v.isInLava()||v.isOnFire()||v.isPassenger()||v.isVehicle()||v.isLeashed()||v.getDeltaMovement().horizontalDistanceSqr()>0.0001)return false;
            var brain=v.getBrain();
            if(brain.hasMemoryValue(MemoryModuleType.HURT_BY)||brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)||brain.isActive(Activity.PANIC))return false;
            if(brain.hasMemoryValue(MemoryModuleType.BREED_TARGET)||brain.hasMemoryValue(MemoryModuleType.INTERACTION_TARGET)||(!v.isBaby()&&v.canBreed()))return false;
            if(!profession.is(VillagerProfession.NONE)&&!profession.is(VillagerProfession.NITWIT)&&v.getVillagerXp()<=0&&v.getOffers().isEmpty())return false;
            if(cfg.idleVillagersEnabled()&&(v.isBaby()||profession.is(VillagerProfession.NONE)||profession.is(VillagerProfession.NITWIT))){
                if(now>=nextIdleRaidCheck){nextIdleRaidCheck=now+20;var raid=level.getRaidAt(v.blockPosition());idleRaid=raid!=null&&raid.isActive()&&!raid.isOver();}
                if(idleRaid)return false;
                if(brain.hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE)||brain.hasMemoryValue(MemoryModuleType.JOB_SITE))return false;
                return true;
            }
            return validStation(v,level,now);
        }
        public boolean suppress(Villager v,ServerLevel level){
            long now=level.getGameTime();var snap=Neodymium2.snapshot();
            gifts.brainChanged(v);
            jobs.brainChanged(v);
            if(generation!=snap.generation()){generation=snap.generation();nextStationCheck=0;nextMaintenance=0;nextIdleRaidCheck=0;cachedJob=null;}
            if(!eligible(v,level,now)){
                if(workstation.getStatus()==Behavior.Status.RUNNING)workstation.doStop(level,v,now);
                dormant=false;
                vanilla++;return false;
            }
            if(!dormant){
                // The same vanilla gift object must survive region/name/reload transitions.
                for(var behavior:v.getBrain().getRunningBehaviors())if(!(behavior instanceof GiveGiftToHero))behavior.doStop(level,v,now);
                BrainTaskCache.invalidate(v.getBrain());
                v.getBrain().eraseMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
                v.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
                v.getNavigation().stop();dormant=true;
            }
            // Keep expiring memories on their real tick clock; do not freeze TTLs with sensors.
            if(!v.isBaby()&&v.getVillagerData().profession().is(VillagerProfession.NONE)&&!jobs.search(v,level,now)){
                dormant=false;vanilla++;return false;
            }
            ((BrainMaintenanceAccess)v.getBrain()).neo$forgetOutdatedMemories();
            if(workstation.getStatus()==Behavior.Status.RUNNING)workstation.tickOrStop(level,v,now);
            if(now>=nextMaintenance){
                nextMaintenance=now+snap.settings().villagerLobotomy().maintenanceIntervalTicks();
                v.getBrain().updateActivityFromSchedule(level.environmentAttributes(),now,v.position());
                if(!v.isBaby()&&v.getBrain().isActive(Activity.WORK)&&workstation.getStatus()==Behavior.Status.STOPPED){
                    nextStationCheck=0; // Recheck the actual block before any restock attempt.
                    if(validStation(v,level,now))workstation.tryStart(level,v,now);
                }
            }
            gifts.tick(v,level,now);
            skipped++;return true;
        }
    }
}
