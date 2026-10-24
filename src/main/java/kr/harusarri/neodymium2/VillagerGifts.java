package kr.harusarri.neodymium2;

import kr.harusarri.neodymium2.mixin.BrainMaintenanceAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import java.util.*;

/** Tick the existing vanilla gift behaviors, preserving their cooldowns across policy changes. */
public final class VillagerGifts {
    private Brain<?> brain;
    private Sensor<Villager> players;
    private final Map<Activity,GiveGiftToHero> gifts=new HashMap<>();
    private long nextScan;
    private boolean nearby,running;
    public void brainChanged(Villager v){
        if(brain!=null&&brain!=v.getBrain()){brain=null;players=null;gifts.clear();running=false;}
    }
    @SuppressWarnings("unchecked")
    private void bind(Villager v){
        if(brain==v.getBrain())return;
        brain=v.getBrain();gifts.clear();running=false;
        var access=(BrainMaintenanceAccess)brain;
        players=(Sensor<Villager>)access.neo$sensors().get(SensorType.NEAREST_PLAYERS);
        for(var activities:access.neo$behaviors().values())for(var entry:activities.entrySet())
            for(var behavior:entry.getValue())if(behavior instanceof GiveGiftToHero gift)gifts.put(entry.getKey(),gift);
    }
    public void tick(Villager v,ServerLevel level,long now){
        if(now>=nextScan){
            nextScan=now+20;nearby=false;
            for(var p:level.players())if(p.isAlive()&&!p.isSpectator()&&p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)&&v.distanceToSqr(p)<=256){nearby=true;break;}
            if(!nearby){
                // These sensor observations have no TTL. Do not retain a departed player
                // (and its connection) indefinitely while the ordinary sensors are asleep.
                v.getBrain().eraseMemory(MemoryModuleType.NEAREST_PLAYERS);
                v.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
                v.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
            }
        }
        if(!nearby&&!running)return;
        bind(v);
        if(nearby&&players!=null)players.tick(level,v);
        else v.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        running=false;boolean changed=false;
        for(var entry:gifts.entrySet()){
            var gift=entry.getValue();
            var before=gift.getStatus();
            if(gift.getStatus()==Behavior.Status.RUNNING)gift.tickOrStop(level,v,now);
            if(nearby&&brain.isActive(entry.getKey())&&gift.getStatus()==Behavior.Status.STOPPED)gift.tryStart(level,v,now);
            running|=gift.getStatus()==Behavior.Status.RUNNING;
            changed|=before!=gift.getStatus();
        }
        if(changed)BrainTaskCache.invalidate(brain);
    }
}
