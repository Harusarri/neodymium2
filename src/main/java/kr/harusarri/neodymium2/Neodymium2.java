package kr.harusarri.neodymium2;

import kr.harusarri.neodymium2.core.*;
import kr.harusarri.neodymium2.module.ModuleRegistry;
import me.wesley1808.servercore.common.interfaces.activation_range.ActivationEntity;
import me.wesley1808.servercore.common.config.data.activation_range.ActivationType;
import me.wesley1808.servercore.common.config.impl.activation_range.ActivationTypeImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import org.slf4j.*;
import java.nio.file.*;
import java.util.*;

public final class Neodymium2 implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("Neodymium2");
    public record Snapshot(Settings settings,Set<RegionPolicies.Ref> regions,SpatialIndex index,Set<EntityType<?>> types,ActivationType profile,long generation,String guard) {}
    public static Path directory=resolveDefaultDirectory();
    private static Path resolveDefaultDirectory(){try{return FabricLoader.getInstance().getConfigDir().resolve("neodymium2");}catch(Throwable ignored){return Path.of("config","neodymium2");}}
    private static volatile Snapshot current=new Snapshot(Settings.defaults(),Set.of(),new SpatialIndex(Map.of()),Set.of(),new ActivationTypeImpl(24,20,60,false,false),0,"not started");
    private static MinecraftServer server;
    private static final Map<Level,String> dimensionKeys=new IdentityHashMap<>();
    private static Level lastDimLevel;
    private static String lastDimKey;
    private static boolean dirty;
    private static final ArrayDeque<Runnable> deferred=new ArrayDeque<>();
    private static long queries,protectedDecisions,reclaimedDecisions;
    public static Snapshot snapshot(){return current;}
    public static void invalidate(){dirty=true;}
    public static void defer(Runnable r){deferred.add(r);dirty=true;}
    public static void onProviderRegistered(){if(server!=null){if(server.isSameThread())reload();else invalidate();}}
    @Override public void onInitialize(){
        ModuleRegistry.initializeAll();
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;reload();});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{server=null;dimensionKeys.clear();lastDimLevel=null;lastDimKey=null;deferred.clear();ModuleRegistry.serverStoppedAll();current=new Snapshot(Settings.defaults(),Set.of(),new SpatialIndex(Map.of()),Set.of(),current.profile,current.generation+1,"stopped");});
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s,r,success)->{if(success)s.execute(Neodymium2::reload);});
        ServerTickEvents.START_SERVER_TICK.register(s->{
            int n=deferred.size();for(int i=0;i<n;i++)try{deferred.remove().run();}catch(RuntimeException e){guard("region change failed",e);}
            if(dirty){dirty=false;rebuild();}
            RegionEntryNotifier.tick(s);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,env)->NeodymiumCommandUi.register(dispatcher));
        LOG.info("Neodymium 2 initialized ({} modules registered); config is inactive by default; production ServerCore configuration is never edited.", ModuleRegistry.all().size());
    }
    private static Snapshot compile(Settings cfg,Set<RegionPolicies.Ref> regions)throws Exception {
        var index=RegionPolicies.compile(regions,cfg.regionMarginBlocks());Set<EntityType<?>> types=new HashSet<>();
        for(String id:cfg.reclaimEntityTypes()){var type=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));if(type==null)throw new IllegalArgumentException("Unknown entity type "+id);types.add(type);}
        String guard=cfg.enabled()&&cfg.requireProtectedRegion()&&index.size()==0?"no protected regions":"";
        return new Snapshot(cfg,Set.copyOf(regions),index,Set.copyOf(types),new ActivationTypeImpl(cfg.activationRange(),cfg.inactiveTickInterval(),cfg.wakeupIntervalSeconds(),false,false),current.generation+1,guard);
    }
    public static boolean reload(){
        try{
            Path config=directory.resolve("config.json"),regions=directory.resolve("regions.json");
            if(!Files.exists(config))AtomicFiles.write(config,Settings.defaults().json());
            if(!Files.exists(regions))AtomicFiles.write(regions,RegionPolicies.json(Set.of()));
            var next=compile(Settings.parse(AtomicFiles.read(config,65536)),RegionPolicies.parse(AtomicFiles.read(regions,1048576)));
            current=next;dirty=false;deferred.clear();dimensionKeys.clear();lastDimLevel=null;lastDimKey=null;ModuleRegistry.reloadAll(next);LOG.info("Reloaded: {}",status());return true;
        }catch(Exception ex){
            if(current.generation==0){var d=Settings.defaults();current=new Snapshot(new Settings(true,Set.of(),d.activationRange(),d.inactiveTickInterval(),d.wakeupIntervalSeconds(),d.regionMarginBlocks(),true),Set.of(),new SpatialIndex(Map.of()),Set.of(),current.profile,1,"initial configuration invalid");}
            LOG.error("Rejected Neodymium 2 reload; previous valid settings retained (initial failure protects normal ticks)",ex);return false;
        }
    }
    private static void rebuild(){try{current=compile(current.settings,current.regions);}catch(Exception ex){guard("protected region unavailable",ex);}}
    private static void guard(String reason,Exception ex){var s=current;if(!s.guard.equals(reason))LOG.error("Neodymium 2 protects normal ticks until region state is valid: "+reason,ex);current=new Snapshot(s.settings,s.regions,s.index,s.types,s.profile,s.generation+1,reason);}
    public static String status(){var s=current;return "네오디윰 2 · "+(s.settings.enabled()?"ON":"OFF")+" · 보호 구역 "+s.regions.size()+" · 세대 "+s.generation+(s.guard.isEmpty()?"":" · 보호 대기: "+s.guard)+" · 조회/보호/최적화 "+queries+"/"+protectedDecisions+"/"+reclaimedDecisions+ModuleRegistry.combinedStatus();}
    public static boolean sensitive(RegionPolicies.Ref ref){return current.regions.contains(ref);}
    /** Build and persist the complete candidate before exposing any change to ticking. */
    public static void setSensitive(RegionPolicies.Ref ref,boolean enabled)throws Exception {
        assertServerThread();if(ref.resolve()==null)throw new IllegalArgumentException("Region no longer exists");var refs=new HashSet<>(current.regions);if(enabled)refs.add(ref);else refs.remove(ref);
        Snapshot next=compile(current.settings,refs);AtomicFiles.write(directory.resolve("regions.json"),RegionPolicies.json(refs));current=next;
    }
    public static void renamed(RegionPolicies.Ref oldRef,RegionPolicies.Ref newRef){
        if(!current.regions.contains(oldRef))return;
        // YAWP updates its name index after MarkedRegion.rename returns.
        defer(()->{if(newRef.resolve()==null){guard("renamed region unresolved",new IllegalStateException(newRef.toString()));return;}try{var refs=new HashSet<>(current.regions);refs.remove(oldRef);refs.add(newRef);var next=compile(current.settings,refs);AtomicFiles.write(directory.resolve("regions.json"),RegionPolicies.json(refs));current=next;}catch(Exception ex){guard("rename persistence failed",ex);}});
    }
    public static void assertServerThread(){if(server==null||!server.isSameThread())throw new IllegalStateException("Region policies must change on the server thread");}
    private static boolean selected(Entity entity){
        var s=current;
        if(!s.settings.enabled()||!s.guard.isEmpty())return false;
        if(!s.types.contains(entity.getType())&&!(s.settings.outsideEntityTightening()&&entity instanceof Mob&&!OutsideEntityOptimizer.isExempt(entity)))return false;
        var tags=entity.entityTags();
        if(tags instanceof FastEntityTagSet fast)return !fast.hasHotFlag(FastEntityTagSet.FLAG_EXCLUDE_EAR);
        return !tags.contains("exclude_ear");
    }
    public static boolean mayReclaim(Entity entity){
        if(!selected(entity))return false;var type=((ActivationEntity)entity).servercore$getActivationType();
        // Preserve explicit upstream always-active profiles; all supported types are ordinary mobs.
        return type.activationRange()>0&&type.tickInterval()!=0&&type.tickInterval()!=1;
    }
    public static boolean excluded(boolean original,Entity entity){if(original&&mayReclaim(entity)){reclaimedDecisions++;return false;}return original;}
    public static ActivationType profile(ActivationType original,Entity entity){return mayReclaim(entity)?current.profile:original;}
    public static double searchRange(double original){var s=current;if(!s.settings.enabled()||!s.guard.isEmpty())return original;if(s.settings.outsideEntityTightening())return Math.min(original,Math.max(24.0,s.settings.activationRange()));return Math.max(original,s.settings.activationRange());}
    public static double searchVerticalRange(double original){var s=current;return s.settings.enabled()&&s.guard.isEmpty()&&s.settings.outsideEntityTightening()?Math.min(original,Math.max(32.0,s.settings.activationRange()+8.0)):original;}
    private static String dimensionKey(Level level){
        if(level==lastDimLevel){String k=lastDimKey;if(k!=null)return k;}
        String k=dimensionKeys.get(level);
        if(k==null){k=level.dimension().identifier().toString();dimensionKeys.put(level,k);}
        lastDimLevel=level;lastDimKey=k;return k;
    }
    private static boolean intersects(Entity e){queries++;var bb=e.getBoundingBox();return current.index.contains(dimensionKey(e.level()),bb.minX,bb.minY,bb.minZ,bb.maxX,bb.maxY,bb.maxZ);}
    private static boolean vehicleProtected(Entity e,int depth){if(depth>16)return true;if(intersects(e))return true;if(!e.isVehicle())return false;for(Entity p:e.getPassengers())if(vehicleProtected(p,depth+1))return true;return false;}
    public static boolean regionProtects(Entity entity){
        var s=current;if(!s.settings.enabled())return false;if(dirty||!s.guard.isEmpty())return true;
        if(s.index.size()==0){queries++;return false;}
        if(!entity.isPassenger()&&!entity.isVehicle()&&entity instanceof EntityFastStateAccess access){
            var bb=entity.getBoundingBox();
            var lvl=entity.level();
            if(access.neo$getProtGen()==s.generation&&access.neo$getProtBb()==bb&&access.neo$getProtLevel()==lvl){
                queries++;
                boolean cached=access.neo$getProtResult();
                if(cached)protectedDecisions++;
                return cached;
            }
            boolean hit=intersects(entity);
            access.neo$setProtCache(s.generation,bb,lvl,hit);
            if(hit)protectedDecisions++;
            return hit;
        }
        Entity root=entity.isPassenger()?entity.getRootVehicle():entity;
        boolean hit=vehicleProtected(root,0);if(hit)protectedDecisions++;return hit;
    }
    public static boolean regionProtectsChunk(Level level,int chunkX,int chunkZ){
        var s=current;if(!s.settings.enabled())return false;if(dirty||!s.guard.isEmpty())return true;
        if(s.index.size()==0)return false;
        return s.index.chunkState(dimensionKey(level),chunkX,chunkZ)!=SpatialIndex.CHUNK_OUTSIDE;
    }
    public static boolean regionProtectsBlock(Level level,BlockPos pos){
        var s=current;if(!s.settings.enabled())return false;if(dirty||!s.guard.isEmpty())return true;
        queries++;
        if(s.index.size()==0)return false;
        double x=pos.getX()+0.5,y=pos.getY()+0.5,z=pos.getZ()+0.5;
        boolean hit=s.index.contains(dimensionKey(level),x,y,z,x,y,z);
        if(hit)protectedDecisions++;
        return hit;
    }
    public static boolean regionProtectsVec(Level level,double x,double y,double z){
        var s=current;if(!s.settings.enabled())return false;if(dirty||!s.guard.isEmpty())return true;
        queries++;
        if(s.index.size()==0)return false;
        boolean hit=s.index.contains(dimensionKey(level),x,y,z,x,y,z);
        if(hit)protectedDecisions++;
        return hit;
    }
    public static boolean forceActive(Entity entity){
        var s=current;
        if(!s.settings.enabled()||!s.guard.isEmpty())return false;
        if(!(entity instanceof Mob)){
            if(((ActivationEntity)entity).servercore$isExcluded()&&!selected(entity))return false;
        }
        if(regionProtects(entity))return true;
        if(entity.hasCustomName())OutsideSpawnAndDensity.recordBlockedNametagBypass(entity);
        if(entity instanceof net.minecraft.world.entity.npc.villager.Villager&&!OutsideEntityOptimizer.enabled())return VillagerLobotomy.enabled();
        if(entity instanceof Mob mob&&(OutsideEntityOptimizer.isInteractiveOrBreeding(mob)||OutsideEntityOptimizer.isActiveMobFarmMonster(mob)))return true;
        return false;
    }
    public static void removed(RegionPolicies.Ref ref){
        if(!current.regions.contains(ref))return;
        defer(()->{if(ref.resolve()!=null)return;try{var refs=new HashSet<>(current.regions);refs.remove(ref);var next=compile(current.settings,refs);AtomicFiles.write(directory.resolve("regions.json"),RegionPolicies.json(refs));current=next;}catch(Exception ex){guard("delete persistence failed",ex);}});
    }
}
