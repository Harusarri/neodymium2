package kr.harusarri.neodymium2;

import kr.harusarri.neodymium2.core.ChunkTraversal;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** All state is accessed on the server thread. Only four projectile types are retained. */
public final class ProjectileCleanup {
    private static final Map<ServerLevel,Set<Entity>> tracked=new IdentityHashMap<>();
    private static long removed,boundaryRemoved,checks;
    private ProjectileCleanup() {}
    public static void initialize() {
        ServerEntityEvents.ENTITY_LOAD.register((e,l)->{if(target(e))tracked.computeIfAbsent(l,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(e);});
        ServerEntityEvents.ENTITY_UNLOAD.register((e,l)->{
            // A visibility downgrade also emits UNLOAD, before the entity is saved.
            // Keep that reference until the pre-entity-tick sweep can discard it safely.
            if(e.isRemoved()){var entities=tracked.get(l);if(entities!=null)entities.remove(e);}
        });
        ServerLevelEvents.UNLOAD.register((s,l)->tracked.remove(l));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->tracked.clear());
    }
    public static boolean target(Entity e) {
        var t=e.getType();return t==EntityTypes.WITHER_SKULL||t==EntityTypes.FIREBALL||t==EntityTypes.WIND_CHARGE||t==EntityTypes.BREEZE_WIND_CHARGE;
    }
    public static boolean enabled() {var s=Neodymium2.snapshot().settings();return s.enabled()&&s.lazyChunkProjectileCleanup();}
    public static boolean applies(Entity e) {return enabled()&&target(e)&&e.level() instanceof ServerLevel;}
    public static boolean allowed(ServerLevel level,int x,int z) {
        checks++;
        // Read the existing entity-ticking ticket range. No chunk lookup/generation.
        // Pending chunks with an active ticket are not mistaken for lazy chunks during load.
        return level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.pack(x,z));
    }
    public static boolean discardIfInactive(Entity e) {
        if(!e.isRemoved()&&applies(e)&&!allowed((ServerLevel)e.level(),e.chunkPosition().x(),e.chunkPosition().z())){discard(e,false);return true;}
        return e.isRemoved();
    }
    public static double boundary(Entity e,Vec3 movement) {
        if(!applies(e))return Double.POSITIVE_INFINITY;
        return ChunkTraversal.firstBlocked(e.getX(),e.getZ(),movement.x,movement.z,(x,z)->allowed((ServerLevel)e.level(),x,z));
    }
    public static void discard(Entity e,boolean boundary) {if(!e.isRemoved()){removed++;if(boundary)boundaryRemoved++;e.discard();}}
    public static void sweep(ServerLevel level) {
        var entities=tracked.get(level);if(entities==null||entities.isEmpty())return;
        ArrayList<Entity> doomed=null;
        for(var it=entities.iterator();it.hasNext();) {
            Entity e=it.next();
            if(e.isRemoved()||e.level()!=level){it.remove();continue;}
            if(enabled()&&!allowed(level,e.chunkPosition().x(),e.chunkPosition().z())) {
                if(doomed==null)doomed=new ArrayList<>();doomed.add(e);
            }
        }
        // Discard invokes lifecycle callbacks; never mutate a set while iterating it.
        if(doomed!=null)for(Entity e:doomed)discard(e,false);
    }
    public static long removedCount(){return removed;}
    public static int trackedCount(){return tracked.values().stream().mapToInt(Set::size).sum();}
    public static String status(){return " · 레이지 투사체 정리 "+(enabled()?"ON":"OFF")+" · 삭제 "+removed+"(경계 "+boundaryRemoved+") · 추적 "+trackedCount()+" · 청크 조회 "+checks;}
}
