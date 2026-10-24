package kr.harusarri.neodymium2;

import kr.harusarri.neodymium2.core.EntityFastStateAccess;
import kr.harusarri.neodymium2.core.FastEntityTagSet;
import kr.harusarri.neodymium2.core.VillagerDormantAccess;
import me.wesley1808.servercore.common.interfaces.activation_range.ActivationEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Multi-layered custom-JAR optimization suite (Pufferfish DAB, Gale/Petal Sensing Cache,
 * Paper/Purpur Pathfinding Backoff, Cramming Collision Elision, Item/XP Merge Throttling,
 * Dormant Outside Villager Travel Striding, and Static Decorative Tracker Striding)
 * strictly restricted to entities OUTSIDE vanillaSensitive regions.
 *
 * Inside vanillaSensitive regions, 100% pure Vanilla behavior is unconditionally preserved.
 */
public final class OutsideEntityOptimizer {
    private static final Set<EntityType<?>> PASSIVE_NO_COLLISION = resolveTypes(
        "minecraft:chicken", "minecraft:cow", "minecraft:pig", "minecraft:rabbit",
        "minecraft:sheep", "minecraft:mooshroom", "minecraft:tropical_fish",
        "minecraft:salmon", "minecraft:cod", "minecraft:squid", "minecraft:glow_squid",
        "minecraft:bat", "minecraft:armadillo", "minecraft:frog", "minecraft:tadpole",
        "minecraft:turtle", "minecraft:goat", "minecraft:sniffer", "minecraft:copper_golem",
        "minecraft:villager", "minecraft:wandering_trader", "minecraft:allay", "minecraft:happy_ghast"
    );

    private static final Set<EntityType<?>> TIGHT_AI_TYPES = resolveTypes(
        "minecraft:copper_golem", "minecraft:warden", "minecraft:armadillo",
        "minecraft:frog", "minecraft:tadpole", "minecraft:goat", "minecraft:sniffer",
        "minecraft:camel", "minecraft:allay", "minecraft:axolotl", "minecraft:piglin",
        "minecraft:hoglin", "minecraft:zoglin", "minecraft:breeze"
    );

    private static long protectedAiTicks, skippedAiTicks, protectedPushTicks, skippedPushTicks;
    private static long retainedSensingTicks, throttledPathSearches, throttledItemMerges, throttledXpScans;
    private static long skippedStaticTrackerUpdates, skippedDormantTravelTicks;

    private OutsideEntityOptimizer() {}

    private static Set<EntityType<?>> resolveTypes(String... ids) {
        Set<EntityType<?>> set = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (String id : ids) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).ifPresent(set::add);
            }
        } catch (Throwable ignored) {
        }
        return Collections.unmodifiableSet(set);
    }

    public static boolean enabled() {
        var s = Neodymium2.snapshot();
        return s.settings().enabled() && s.guard().isEmpty() && s.settings().outsideEntityTightening();
    }

    public static boolean isExempt(Entity entity) {
        if (entity instanceof Player
            || entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
            || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
            || entity instanceof net.minecraft.world.entity.monster.ElderGuardian) {
            return true;
        }
        if (entity.hasCustomName()) {
            OutsideSpawnAndDensity.recordBlockedNametagBypass(entity);
        }
        var tags = entity.entityTags();
        if (tags instanceof FastEntityTagSet fast) {
            return fast.hasHotFlag(FastEntityTagSet.FLAG_EXCLUDE_EAR | FastEntityTagSet.FLAG_EXCLUDE_LOBOTOMIZATION);
        }
        return tags.contains("exclude_ear") || tags.contains("exclude_lobotomization");
    }

    public static void wakeOnPlayerInteract(Mob mob) {
        if (mob instanceof EntityFastStateAccess access) {
            access.neo$setInteractAwakeUntilTick(Math.max(access.neo$getInteractAwakeUntilTick(), mob.tickCount + 200));
        }
        if (mob instanceof ActivationEntity ae) {
            ae.servercore$setInactive(false);
            if (mob.level() instanceof ServerLevel level && level.getServer() != null) {
                int serverTick = level.getServer().getTickCount();
                ae.servercore$setActivatedTick(Math.max(ae.servercore$getActivatedTick(), serverTick + 200));
                ae.servercore$setActivatedImmunityTick(Math.max(ae.servercore$getActivatedImmunityTick(), serverTick + 200));
            }
        }
    }

    public static boolean isInteractiveOrBreeding(Mob mob) {
        if (mob instanceof EntityFastStateAccess access && mob.tickCount < access.neo$getInteractAwakeUntilTick()) {
            return true;
        }
        if (mob instanceof Villager v && (v.isTrading() || (!v.isBaby() && v.canBreed()))) {
            return true;
        }
        if (mob instanceof Animal animal) {
            if (animal.isInLove() || animal.isPanicking() || animal.getLoveCause() != null) {
                return true;
            }
            if (mob.level() instanceof ServerLevel level && PlayerSpatialGrid.hasFoodHoldingPlayerWithin(level, animal, 100.0)) {
                return true;
            }
        }
        var brain = mob.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.BREED_TARGET)
            || brain.hasMemoryValue(MemoryModuleType.TEMPTING_PLAYER)
            || brain.hasMemoryValue(MemoryModuleType.IS_PREGNANT)
            || brain.hasMemoryValue(MemoryModuleType.INTERACTION_TARGET)
            || brain.hasMemoryValue(MemoryModuleType.LIKED_PLAYER);
    }

    private static boolean isCombatOrInteractive(Mob mob) {
        if (mob.hurtTime > 0 || !mob.isAlive() || mob.getRemainingFireTicks() > 0 || mob.isVehicle() || mob.isPassenger() || mob.isLeashed() || mob.getTarget() != null) {
            return true;
        }
        if (mob instanceof net.minecraft.world.entity.monster.Creeper c && c.isIgnited()) return true;
        return isInteractiveOrBreeding(mob);
    }

    /**
     * Paper / Low-Level Mob Farm Monster Activation:
     * Keeps monsters (Enemy) in the player's active mob-farm column (<= 48m horizontal, <= 128m 3D,
     * excluding deep caves y < 32 far below the player) active in EAR so water canals, drop chutes,
     * trapdoor walk-off, and despawn timers function reliably without waking deep underground caves.
     */
    public static boolean isActiveMobFarmMonster(Mob mob) {
        if (!(mob instanceof net.minecraft.world.entity.monster.Enemy) || !enabled()) return false;
        if (!(mob.level() instanceof ServerLevel level) || level.players().isEmpty()) return false;
        int gear = OutsideWorldOptimizer.msptGear(level.getServer());
        if (gear >= 2) return false;
        double maxHoriz = (gear == 1 || level.players().size() >= 20) ? 40.0 : 48.0;
        double horizDistSq = PlayerSpatialGrid.horizDistSqToNearestPlayer(level, mob.getX(), mob.getZ());
        if (horizDistSq > maxHoriz * maxHoriz) return false;
        double dist3dSq = PlayerSpatialGrid.distSqToNearestPlayer(level, mob.getX(), mob.getY(), mob.getZ());
        if (dist3dSq > 128.0 * 128.0) return false;
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && mob.getBlockY() < 32) {
            int playerY = PlayerSpatialGrid.nearestPlayerBlockY(level, mob.getX(), mob.getZ());
            if (playerY - mob.getBlockY() > 56) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pufferfish DAB (Dynamic Activation of Brain & GoalSelector):
     * Scales AI tick interval by distance-squared gradient from the nearest player:
     * interval = clamp(base + floor(distSq / 64.0), 4, 20)
     */
    public static boolean shouldSkipAi(Mob mob) {
        if (!enabled() || !(mob.level() instanceof ServerLevel level)) return false;
        if (mob instanceof Villager || mob instanceof WanderingTrader || isExempt(mob)) return false;
        if (Neodymium2.regionProtects(mob)) {
            protectedAiTicks++;
            return false;
        }
        if (isCombatOrInteractive(mob)) return false;
        if (mob.isOnPortalCooldown() || (mob.portalProcess != null && mob.portalProcess.isInsidePortalThisTick())) return false;
        if (mob instanceof ActivationEntity ae && ae.servercore$isInactive()) return false;

        int tick = (int) level.getGameTime();
        int density = OutsideSpawnAndDensity.recordAndGetChunkDensity(mob, tick);
        int gear = OutsideWorldOptimizer.msptGear(level.getServer());
        double distSq = Math.min(16384.0, PlayerSpatialGrid.distSqToNearestPlayer(level, mob.getX(), mob.getY(), mob.getZ()));
        if (density <= 32 && gear == 0 && distSq <= 48.0 * 48.0) {
            // Within 48m under normal MSPT: preserve 1-tick buoyancy, swimming, and flight goals
            // for nearby lava mobs, aquatic monsters (Drowned/Guardian), and flying mobs.
            var cat = mob.getType().getCategory();
            boolean passiveFishOrSquid = cat == net.minecraft.world.entity.MobCategory.WATER_AMBIENT
                || cat == net.minecraft.world.entity.MobCategory.WATER_CREATURE
                || cat == net.minecraft.world.entity.MobCategory.UNDERGROUND_WATER_CREATURE;
            if (mob.isInLava()
                || (!passiveFishOrSquid && (mob.isInWater() || !mob.onGround() || mob.isNoGravity()))) {
                if (mob instanceof net.minecraft.world.entity.monster.Enemy && mob.getNoActionTime() > 60 && mob.tickCount < 1200) {
                    mob.setNoActionTime(0);
                }
                return false;
            }
        }
        int interval;
        if (mob instanceof net.minecraft.world.entity.monster.Enemy && isActiveMobFarmMonster(mob)) {
            // Prevent Vanilla RandomStrollGoal freeze (noActionTime >= 100 when 3D distance > 32m)
            // for up to 60 seconds after spawn so monsters in classic dark-room mob towers continuously
            // pathfind/wander into water canals even when the player stands 32-48m below the platforms.
            if (mob.getNoActionTime() > 60 && mob.tickCount < 1200) {
                mob.setNoActionTime(0);
            }
            if (density <= 32 && gear == 0 && level.players().size() < 25) {
                return false;
            }
            if (!mob.getNavigation().isDone() || mob.isInWater() || mob.isInLava()) {
                return false;
            }
            interval = density > 48 ? 8 : 2;
        } else if (density > OutsideSpawnAndDensity.OVERCROWD_THRESHOLD) {
            interval = 20;
        } else {
            boolean heavyBrain = TIGHT_AI_TYPES.contains(mob.getType());
            int base = (heavyBrain ? 8 : 4) << gear;
            int shift = PASSIVE_NO_COLLISION.contains(mob.getType()) ? 5 : 6;
            interval = Math.min(20, base + ((int) distSq >> shift));
        }
        if (Math.floorMod(mob.getId() + mob.tickCount, interval) != 0) {
            skippedAiTicks++;
            return true;
        }
        return false;
    }

    /**
     * Gale / Petal Line-of-Sight Raycast Cache Retention:
     * Retains Sensing.seen / unseen raycast results for 8 ticks outside protected regions.
     */
    public static boolean shouldRetainSensingCache(Mob mob) {
        if (!enabled() || !(mob.level() instanceof ServerLevel)) return false;
        if (isExempt(mob) || isCombatOrInteractive(mob) || Neodymium2.regionProtects(mob)) return false;
        if (((mob.getId() + mob.tickCount) & 7) != 0) {
            retainedSensingTicks++;
            return true;
        }
        return false;
    }

    /**
     * Paper / Purpur Non-Combat A* Pathfinding Backoff:
     * Limits full A* PathFinder recomputations to at most once every 20 ticks for non-combat
     * wandering mobs outside protected regions.
     */
    public static boolean shouldThrottlePathSearch(Mob mob, int ticksSinceLastSearch) {
        if (ticksSinceLastSearch < 0 || ticksSinceLastSearch >= 20) return false;
        if (!enabled() || !(mob.level() instanceof ServerLevel)) return false;
        if (mob instanceof Villager || mob instanceof WanderingTrader || isExempt(mob) || isCombatOrInteractive(mob) || isActiveMobFarmMonster(mob) || Neodymium2.regionProtects(mob)) return false;
        throttledPathSearches++;
        return true;
    }

    public static boolean shouldSkipPushEntities(LivingEntity entity) {
        if (!enabled() || !(entity.level() instanceof ServerLevel)) return false;
        if (!(entity instanceof Mob mob) || isExempt(entity)) return false;
        if (Neodymium2.regionProtects(entity)) {
            protectedPushTicks++;
            return false;
        }
        if (entity.hurtTime > 0 || entity.isVehicle() || entity.isPassenger() || isInteractiveOrBreeding(mob) || isActiveMobFarmMonster(mob)) return false;
        if (PASSIVE_NO_COLLISION.contains(entity.getType())) {
            skippedPushTicks++;
            return true;
        }
        if (((entity.getId() + entity.tickCount) & 3) != 0) {
            skippedPushTicks++;
            return true;
        }
        return false;
    }

    public static boolean shouldSkipDoPush(LivingEntity self, Entity other) {
        if (!enabled() || !(self.level() instanceof ServerLevel)) return false;
        if (other instanceof Player || !PASSIVE_NO_COLLISION.contains(self.getType()) || isExempt(self)) return false;
        if (self instanceof Mob mob && isInteractiveOrBreeding(mob)) return false;
        if (Neodymium2.regionProtects(self)) return false;
        return self.hurtTime == 0 && !self.isVehicle() && !self.isPassenger();
    }

    /**
     * Dormant Outside Villager Travel Striding:
     * Skips 3 out of 4 redundant LivingEntity.travel(Vec3) -> Entity.move(MoverType.SELF) -> Shapes.collide
     * and Level.findSupportingBlock evaluations for stationary dry-ground dormant villagers outside
     * vanillaSensitive regions.
     */
    public static boolean shouldSkipDormantVillagerTravel(LivingEntity self, Vec3 travelVector) {
        if (!(self instanceof VillagerDormantAccess v) || !v.neo$isDormantVillager() || !enabled()) {
            return false;
        }
        if (!self.onGround() || self.hurtTime > 0 || self.isInWater() || self.isInLava() || self.isPassenger() || self.isVehicle()) {
            return false;
        }
        if (travelVector.lengthSqr() > 1.0E-7 || self.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6) {
            return false;
        }
        if (((self.getId() + self.tickCount) & 3) != 0) {
            skippedDormantTravelTicks++;
            return true;
        }
        return false;
    }

    public static boolean shouldThrottleItemMerge(ItemEntity item) {
        if (!enabled() || !(item.level() instanceof ServerLevel)) return false;
        if (isExempt(item) || Neodymium2.regionProtects(item)) return false;
        if (((item.getId() + item.tickCount) & 7) != 0) {
            throttledItemMerges++;
            return true;
        }
        return false;
    }

    public static boolean shouldThrottleXpOrbScan(ExperienceOrb orb) {
        if (!enabled() || !(orb.level() instanceof ServerLevel)) return false;
        if (isExempt(orb) || Neodymium2.regionProtects(orb)) return false;
        if (((orb.getId() + orb.tickCount) & 3) != 0) {
            throttledXpScans++;
            return true;
        }
        return false;
    }

    /**
     * Stationary Clean Decorative & Hologram Tracker Striding:
     * Skips redundant per-tick ServerEntity.sendChanges() evaluations on 15 out of 16 ticks
     * for stationary, unrotated, passenger-less, clean (!entityData.isDirty()) Display,
     * Interaction, HangingEntity (ItemFrame/GlowItemFrame/Painting), and outside ArmorStand entities.
     * Any metadata change (isDirty()), position/rotation change, or passenger change is synced immediately
     * on the exact tick it occurs.
     */
    public static boolean shouldSkipStaticTrackerSendChanges(Entity entity, int trackerTickCount, List<Entity> lastPassengers) {
        if (!(entity instanceof net.minecraft.world.entity.Display)
            && !(entity instanceof net.minecraft.world.entity.Interaction)
            && !(entity instanceof net.minecraft.world.entity.decoration.HangingEntity)
            && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            return false;
        }
        if (!enabled() || !(entity.level() instanceof ServerLevel)) return false;
        if (entity.needsSync || entity.syncPosition || entity.getEntityData().isDirty()) return false;
        if (!entity.getPassengers().isEmpty() || (lastPassengers != null && !lastPassengers.isEmpty()) || entity.isPassenger()) {
            return false;
        }
        if (entity.getX() != entity.xOld || entity.getY() != entity.yOld || entity.getZ() != entity.zOld
            || entity.getYRot() != entity.yRotO || entity.getXRot() != entity.xRotO
            || entity.getDeltaMovement().lengthSqr() > 1.0E-7) {
            return false;
        }
        if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame && (trackerTickCount % 10 == 0)) {
            return false;
        }
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand && Neodymium2.regionProtects(entity)) {
            return false;
        }
        if (((entity.getId() + trackerTickCount) & 15) != 0) {
            skippedStaticTrackerUpdates++;
            return true;
        }
        return false;
    }

    /**
     * Petal / Leaf Stationary Living-Entity Dirty-Driven Tracker Gate:
     * For stationary, unrotated, passenger-less Mobs in CHUNK_OUTSIDE whose SynchedEntityData
     * and Attributes are clean (!isDirty() && getAttributesToSync().isEmpty()), skips redundant
     * per-tick ServerEntity.sendChanges() evaluations on 3 out of 4 ticks.
     * Any position, rotation, head rotation, velocity, metadata, attribute, or hurt change runs
     * immediately on the exact tick it occurs (0-tick sync latency).
     */
    public static boolean shouldSkipStationaryMobTrackerSendChanges(
        Entity entity,
        int trackerTickCount,
        List<Entity> lastPassengers,
        Vec3 lastSentPos,
        byte lastSentYRot,
        byte lastSentXRot,
        byte lastSentYHeadRot,
        Vec3 lastSentMovement,
        boolean wasOnGround,
        boolean trackDelta
    ) {
        if (!(entity instanceof Mob mob) || !enabled() || !(mob.level() instanceof ServerLevel)) {
            return false;
        }
        if (isExempt(mob) || mob.hurtTime > 0 || mob.isLeashed() || isInteractiveOrBreeding(mob)) {
            return false;
        }
        if (mob.needsSync || mob.syncPosition || mob.getEntityData().isDirty()) {
            return false;
        }
        if (!mob.getPassengers().isEmpty() || (lastPassengers != null && !lastPassengers.isEmpty()) || mob.isPassenger()) {
            return false;
        }
        if (!mob.getAttributes().getAttributesToSync().isEmpty()) {
            return false;
        }
        if (trackerTickCount % ServerEntity.FORCED_POS_UPDATE_PERIOD == 0) {
            return false;
        }
        if (lastSentPos == null || !mob.position().equals(lastSentPos) || mob.onGround() != wasOnGround) {
            return false;
        }
        if (Mth.packDegrees(mob.getYRot()) != lastSentYRot
            || Mth.packDegrees(mob.getXRot()) != lastSentXRot
            || Mth.packDegrees(mob.getYHeadRot()) != lastSentYHeadRot) {
            return false;
        }
        if (trackDelta && (lastSentMovement == null || mob.getDeltaMovement().distanceToSqr(lastSentMovement) > 1.0E-6)) {
            return false;
        }
        if (Neodymium2.regionProtects(mob)) {
            return false;
        }
        if (((mob.getId() + trackerTickCount) & 3) != 0) {
            skippedStaticTrackerUpdates++;
            return true;
        }
        return false;
    }

    public static long protectedAiTicks() { return protectedAiTicks; }
    public static long skippedAiTicks() { return skippedAiTicks; }
    public static long protectedPushTicks() { return protectedPushTicks; }
    public static long skippedPushTicks() { return skippedPushTicks; }
    public static long retainedSensingTicks() { return retainedSensingTicks; }
    public static long throttledPathSearches() { return throttledPathSearches; }
    public static long throttledItemMerges() { return throttledItemMerges; }
    public static long throttledXpScans() { return throttledXpScans; }
    public static long skippedStaticTrackerUpdates() { return skippedStaticTrackerUpdates; }
    public static long skippedDormantTravelTicks() { return skippedDormantTravelTicks; }

    public static void resetStats() {
        protectedAiTicks = 0;
        skippedAiTicks = 0;
        protectedPushTicks = 0;
        skippedPushTicks = 0;
        retainedSensingTicks = 0;
        throttledPathSearches = 0;
        throttledItemMerges = 0;
        throttledXpScans = 0;
        skippedStaticTrackerUpdates = 0;
        skippedDormantTravelTicks = 0;
    }

    public static String status() {
        return " · 외부 엔티티 최적화 " + (enabled() ? "ON" : "OFF")
            + "(AI보호/생략 " + protectedAiTicks + "/" + skippedAiTicks
            + ", 충돌보호/생략 " + protectedPushTicks + "/" + skippedPushTicks
            + ", 시야캐시/길찾기/아이템·경험치/정적트래커/휴면이동절약 " + retainedSensingTicks + "/" + throttledPathSearches + "/" + (throttledItemMerges + throttledXpScans) + "/" + skippedStaticTrackerUpdates + "/" + skippedDormantTravelTicks + ")";
    }
}
