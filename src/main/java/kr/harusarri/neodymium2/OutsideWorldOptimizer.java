package kr.harusarri.neodymium2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;

/**
 * Deep World & Non-Entity Engine Optimization Suite strictly restricted to CHUNK_OUTSIDE
 * (or 100% Vanilla-identical algorithmic replacements):
 * 1. Adaptive MSPT Governor: dynamically gears down CHUNK_OUTSIDE spawn, AI, and distant random-tick
 *    cadence when server rolling MSPT exceeds 18ms (Gear 1) or 28ms (Gear 2), while keeping
 *    vanillaSensitive regions at 100% 1-tick pure Vanilla regardless of server load.
 * 2. Stationary Entity 3D Fluid Scan Elision: skips redundant 3D block fluid box scans for stationary
 *    dry-ground mobs/items outside protected regions on 3 out of 4 ticks.
 * 3. Distant Outside Chunk RandomTick & Precipitation Striding: throttles random ticks on distant
 *    outside chunks (>48m from players) and cuts outside weather/precipitation block checks by 75%.
 * 4. High-Frequency Mob Sculk GameEvent Filtering: filters 75% of non-player mob movement vibrations
 *    (STEP, SWIM, FLAP, HIT_GROUND) outside protected regions.
 * 5. Outside Idle Hopper Poll Throttling & TrialSpawner/Vault Player-Scan Backoff.
 * 6. Outside ServerExplosion.getSeenPercent 3D Raycast Cache & Empty-Structure NaturalSpawner.mobsAt
 *    Short-Circuit.
 * 7. Active-Set <lithium_sleeping> BlockEntity Compaction, PoiCompetitorScan Stream Elision,
 *    YAWP FlagEvaluator Position Cache, Selector Tag/NBT Fast-Path, and MicroTiming No-Op Bypass.
 */
public final class OutsideWorldOptimizer {
    private static final int EXPLOSION_CACHE_MASK = 255;
    private static final long[] EXPLOSION_KEYS = new long[EXPLOSION_CACHE_MASK + 1];
    private static final float[] EXPLOSION_VALUES = new float[EXPLOSION_CACHE_MASK + 1];

    private static int manualGearOverride = -1;
    private static int cachedGearTick = -1;
    private static int cachedGear = 0;
    private static int blockEntityTickEpochCounter = 0;
    private static int currentBlockEntityTickEpoch = 0;
    private static String currentRequiredSelectorTag;
    private static long gear1Evaluations, gear2Evaluations;
    private static long protectedChunkTicks, throttledRandomTickChunks, throttledPrecipitations;
    private static long protectedGameEvents, filteredGameEvents;
    private static long protectedFluidScans, skippedFluidScans;
    private static long protectedHopperPolls, throttledHopperPolls, throttledTrialVaultTicks;
    private static long protectedExplosionRays, cachedExplosionRays;
    private static long shortCircuitedStructureSpawns;
    private static long fastTypeLookups, cachedBlockTickChunks, skippedIdleBlockEntityTicks;
    private static long fastTagLookups, fastSelectorNbtChecks, fastPoiCompetitorScans;
    private static long cachedYawpFlagEvals, compactedSleepingBlockEntities, skippedMicroTimingCalls;
    private static long restingGravityShortCircuits, fastBrainMemoryLookups, fastTagBitLookups;
    private static long skippedInWallChecks, memoizedSelectorHits, entityTopologyRevision;

    private OutsideWorldOptimizer() {}

    public static boolean enabled() {
        var s = Neodymium2.snapshot();
        return s.settings().enabled() && s.guard().isEmpty() && s.settings().outsideWorldTightening();
    }

    public static void setManualGearOverride(int gear) {
        manualGearOverride = gear;
    }

    public static String currentRequiredSelectorTag() {
        return currentRequiredSelectorTag;
    }

    public static String pushRequiredSelectorTag(String tag) {
        String prev = currentRequiredSelectorTag;
        currentRequiredSelectorTag = tag;
        return prev;
    }

    public static void popRequiredSelectorTag(String prev) {
        currentRequiredSelectorTag = prev;
    }

    /**
     * Adaptive MSPT Governor:
     * Uses a per-tick cached 8-tick trimmed mean (excluding the single highest outlier tick)
     * so one-off GC/command spikes do not false-trigger gearing while sustained load reacts within 8 ticks.
     * Gear 0 (<18ms): standard tight intervals.
     * Gear 1 (18..28ms): 2x tighter outside intervals.
     * Gear 2 (>=28ms): 4x tighter outside intervals.
     * Protected vanillaSensitive regions NEVER consult msptGear and always run 100% 1-tick Vanilla.
     */
    public static int msptGear(MinecraftServer server) {
        if (!enabled()) return 0;
        if (manualGearOverride >= 0) {
            if (manualGearOverride == 1) gear1Evaluations++;
            else if (manualGearOverride >= 2) gear2Evaluations++;
            return Math.min(manualGearOverride, 2);
        }
        if (server == null) return 0;
        int tick = server.getTickCount();
        if (tick < 200) return 0;
        if (tick != cachedGearTick) {
            long[] nanos = server.getTickTimesNanos();
            if (nanos == null || nanos.length == 0) return 0;
            long sum = 0L, max = 0L;
            for (int i = 1; i <= 8; i++) {
                long v = nanos[Math.floorMod(tick - i, nanos.length)];
                sum += v;
                if (v > max) max = v;
            }
            double mspt = ((sum - max) / 7.0) / 1_000_000.0;
            cachedGear = mspt >= 28.0 ? 2 : (mspt >= 18.0 ? 1 : 0);
            cachedGearTick = tick;
        }
        if (cachedGear == 1) gear1Evaluations++;
        else if (cachedGear == 2) gear2Evaluations++;
        return cachedGear;
    }

    /**
     * Distant CHUNK_OUTSIDE RandomTick Striding:
     * Preserves 100% Vanilla randomTickSpeed inside vanillaSensitive chunks.
     * In CHUNK_OUTSIDE, when no player is within 48 blocks horizontally (or when MSPT gear > 0),
     * strides random tick evaluation across ticks.
     */
    public static int adjustOutsideRandomTickSpeed(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        if (randomTickSpeed <= 0 || !enabled()) return randomTickSpeed;
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        if (Neodymium2.regionProtectsChunk(level, cx, cz)) {
            protectedChunkTicks++;
            return randomTickSpeed;
        }
        double centerX = (cx << 4) + 8.0, centerZ = (cz << 4) + 8.0;
        boolean playerNear = PlayerSpatialGrid.hasPlayerWithinHorizontal(level, centerX, centerZ, 2304.0); // 48 blocks horizontal radius
        int gear = msptGear(level.getServer());
        if (!playerNear || gear > 0) {
            int stride = (!playerNear ? 4 : 2) << gear; // 4, 8, or 16 ticks far; 2, 4, or 8 ticks near under load
            long hash = cx * 0x9E3779B97F4A7C15L ^ cz * 0xBF58476D1CE4E5B9L;
            if ((((hash >>> 32) + level.getGameTime()) & (stride - 1)) != 0) {
                throttledRandomTickChunks++;
                return 0;
            }
        }
        return randomTickSpeed;
    }

    public static boolean shouldSkipOutsidePrecipitation(ServerLevel level, BlockPos pos) {
        if (!enabled()) return false;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (Neodymium2.regionProtectsChunk(level, cx, cz)) return false;
        long hash = cx * 31L + cz;
        if (((hash + level.getGameTime()) & 3L) != 0) {
            throttledPrecipitations++;
            return true;
        }
        return false;
    }

    /**
     * Gale / Leaf High-Frequency Mob Sculk GameEvent Filtering:
     * Preserves 100% Vanilla gameEvents inside vanillaSensitive regions and for all Players,
     * Villagers, "춘식이", projectiles, and block/redstone actions.
     * Filters 3 out of 4 high-frequency movement vibrations (STEP, SWIM, FLAP, HIT_GROUND)
     * emitted by ordinary mobs in CHUNK_OUTSIDE.
     */
    public static boolean shouldSkipOutsideGameEvent(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
        if (!enabled() || context == null) return false;
        Entity source = context.sourceEntity();
        if (!(source instanceof Mob mob) || OutsideEntityOptimizer.isExempt(mob)) return false;
        if (event != GameEvent.STEP && event != GameEvent.SWIM && event != GameEvent.FLAP && event != GameEvent.HIT_GROUND) {
            return false;
        }
        if (Neodymium2.regionProtectsVec(level, pos.x, pos.y, pos.z) || Neodymium2.regionProtects(mob)) {
            protectedGameEvents++;
            return false;
        }
        if (((mob.getId() + mob.tickCount) & 3) != 0) {
            filteredGameEvents++;
            return true;
        }
        return false;
    }

    /**
     * Paper / Pufferfish Stationary Entity 3D Fluid Interaction Elision:
     * Skips 3 out of 4 redundant 3D block fluid box scans for stationary dry-ground Mobs and
     * ItemEntities in CHUNK_OUTSIDE.
     */
    public static boolean shouldSkipOutsideFluidScan(Entity entity) {
        if (!enabled() || !(entity.level() instanceof ServerLevel)) return false;
        if (!(entity instanceof Mob) && !(entity instanceof ItemEntity)) return false;
        if (OutsideEntityOptimizer.isExempt(entity)) return false;
        if (!entity.onGround() || entity.isInWater() || entity.isInLava() || entity.isVehicle() || entity.isPassenger()) {
            return false;
        }
        if (entity instanceof LivingEntity living && living.hurtTime > 0) return false;
        if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) return false;
        if (Neodymium2.regionProtects(entity)) {
            protectedFluidScans++;
            return false;
        }
        if (((entity.getId() + entity.tickCount) & 3) != 0) {
            skippedFluidScans++;
            return true;
        }
        return false;
    }

    /**
     * Canvas / Leaf Resting-Gravity 3D Voxel-Collision & Supporting-Block Short-Circuit:
     * When a stationary dry-ground Mob in CHUNK_OUTSIDE is resting at the exact same Vec3
     * with only downward resting gravity (movement.x == 0, movement.z == 0, -0.1 <= movement.y <= 0)
     * and the supporting BlockState below its feet has not changed, short-circuits Entity.move(SELF)
     * on 7 out of 8 ticks (skipping Shapes.collide 3D voxel sweep and Level.findSupportingBlock).
     * Inside vanillaSensitive regions or on any block/movement/damage change, 100% pure Vanilla runs immediately.
     */
    public static boolean shouldShortCircuitRestingMove(Mob mob, BlockPos supportPos, BlockState supportState) {
        if (!enabled() || supportPos == null || supportState == null) return false;
        Level level = mob.level();
        if (!(level instanceof ServerLevel)) return false;
        if (OutsideEntityOptimizer.isExempt(mob) || mob.hurtTime > 0 || mob.isInWater() || mob.isInLava() || mob.isVehicle() || mob.isPassenger()) {
            return false;
        }
        if (level.getBlockState(supportPos) != supportState) {
            return false;
        }
        if (Neodymium2.regionProtects(mob)) {
            return false;
        }
        restingGravityShortCircuits++;
        return true;
    }

    /**
     * Pufferfish / Gale Stationary LivingEntity.isInWall 3D Scan Short-Circuit:
     * When a stationary Mob in CHUNK_OUTSIDE was not in a wall on the previous check, has not moved,
     * and the BlockState at its eye position is unchanged, skips BlockPos.betweenClosedStream(aabb)
     * + Shapes.joinIsNotEmpty on 7 out of 8 ticks.
     */
    public static boolean shouldSkipInWallScan(Mob mob, BlockPos eyeBlockPos, BlockState eyeBlockState) {
        if (!enabled() || eyeBlockPos == null || eyeBlockState == null) return false;
        Level level = mob.level();
        if (!(level instanceof ServerLevel)) return false;
        if (OutsideEntityOptimizer.isExempt(mob) || mob.hurtTime > 0 || mob.isVehicle() || mob.isPassenger()) {
            return false;
        }
        if (level.getBlockState(eyeBlockPos) != eyeBlockState) {
            return false;
        }
        if (Neodymium2.regionProtects(mob)) {
            return false;
        }
        skippedInWallChecks++;
        return true;
    }

    /**
     * Outside Idle Hopper Polling Throttle:
     * Without touching cooldownTime (preserving 100% compatibility with Lithium's hopper sleeping),
     * skips 3 out of 4 ticks for non-sleeping idle hoppers (cooldownTime <= 0) in CHUNK_OUTSIDE.
     * Inside vanillaSensitive regions, 100% 1-tick Vanilla polling is preserved.
     */
    public static boolean shouldSkipIdleHopperPoll(Level level, BlockPos pos, int cooldownTime) {
        if (cooldownTime > 0 || !enabled() || !(level instanceof ServerLevel sLevel)) return false;
        if (Neodymium2.regionProtectsBlock(level, pos)) {
            protectedHopperPolls++;
            return false;
        }
        int gear = msptGear(sLevel.getServer());
        boolean nearPlayer = sLevel.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16.0);
        long mask = ((nearPlayer ? 4L : 8L) << gear) - 1L;
        if (((pos.asLong() + level.getGameTime()) & mask) != 0) {
            throttledHopperPolls++;
            return true;
        }
        return false;
    }

    /**
     * Outside TrialSpawner & Vault PlayerDetector Scan Backoff:
     * Throttles TrialSpawner.tickServer and VaultBlockEntity$Server.tick to once every 10 ticks
     * in CHUNK_OUTSIDE when no player is within 16 blocks.
     */
    public static boolean shouldThrottleOutsideTrialOrVault(ServerLevel level, BlockPos pos) {
        if (!enabled()) return false;
        if (Neodymium2.regionProtectsBlock(level, pos)) return false;
        if (level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16.0)) {
            return false;
        }
        if (Math.floorMod(pos.asLong() + level.getGameTime(), 10) != 0) {
            throttledTrialVaultTicks++;
            return true;
        }
        return false;
    }

    /**
     * Paper / Lithium ServerExplosion.getSeenPercent 3D Raycast Cache:
     * Inside vanillaSensitive regions (or for Players/exempt entities), runs 100% uncached Vanilla
     * raycasts so TNT cannons and blast chambers preserve exact sub-pixel physics.
     * In CHUNK_OUTSIDE, caches exposure per (tick, quantized explosion source, entity blockPos, bounding box size).
     */
    public static float getSeenPercent(Vec3 source, Entity entity, Operation<Float> original) {
        if (!enabled() || !(entity.level() instanceof ServerLevel level) || OutsideEntityOptimizer.isExempt(entity)) {
            return original.call(source, entity);
        }
        if (Neodymium2.regionProtects(entity) || Neodymium2.regionProtectsVec(level, source.x, source.y, source.z)) {
            protectedExplosionRays++;
            return original.call(source, entity);
        }
        long tick = level.getGameTime();
        long sx = Math.round(source.x * 16.0);
        long sy = Math.round(source.y * 16.0);
        long sz = Math.round(source.z * 16.0);
        long srcHash = sx * 0x9E3779B97F4A7C15L ^ sy * 0xBF58476D1CE4E5B9L ^ sz * 0x94D049BB133111EBL;
        long blockKey = entity.blockPosition().asLong();
        long sizeKey = ((long) Float.floatToRawIntBits(entity.getBbWidth()) << 32)
                     | (Float.floatToRawIntBits(entity.getBbHeight()) & 0xFFFFFFFFL);
        long key = (1L << 63) | ((tick * 0x9E3779B97F4A7C15L ^ srcHash ^ (blockKey * 0xBF58476D1CE4E5B9L) ^ sizeKey) & 0x7FFFFFFFFFFFFFFFL);
        int slot = (int) ((key ^ (key >>> 32)) & EXPLOSION_CACHE_MASK);
        if (EXPLOSION_KEYS[slot] == key) {
            cachedExplosionRays++;
            return EXPLOSION_VALUES[slot];
        }
        float seen = original.call(source, entity);
        EXPLOSION_KEYS[slot] = key;
        EXPLOSION_VALUES[slot] = seen;
        return seen;
    }

    /**
     * Empty-Structure NaturalSpawner.mobsAt Short-Circuit:
     * In CHUNK_OUTSIDE, when a chunk has zero structure references (chunk.getAllReferences().isEmpty()),
     * bypasses isInNetherFortressBounds and StructureManager.getAllStructuresAt and directly returns
     * the biome's spawn list.
     */
    public static WeightedList<MobSpawnSettings.SpawnerData> tryShortCircuitMobsAt(ServerLevel level, MobCategory category, BlockPos pos) {
        if (!enabled()) return null;
        LevelChunk outsideChunk = RegionSpawner.currentOutsideChunk();
        if (outsideChunk != null && RegionSpawner.isCurrentOutsideChunkStructureEmpty()) {
            shortCircuitedStructureSpawns++;
            var base = level.dimensionType().attributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            var rough = outsideChunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value();
            return rough.getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, base).getMobsToSpawn(category);
        }
        if (RegionSpawner.isInsideProtectedChunk()) return null;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (Neodymium2.regionProtectsChunk(level, cx, cz)) return null;
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk != null && chunk.getAllReferences().isEmpty()) {
            shortCircuitedStructureSpawns++;
            var base = level.dimensionType().attributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            var rough = chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value();
            return rough.getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, base).getMobsToSpawn(category);
        }
        return null;
    }

    public static long gear1Evaluations() { return gear1Evaluations; }
    public static long gear2Evaluations() { return gear2Evaluations; }
    public static long protectedChunkTicks() { return protectedChunkTicks; }
    public static long throttledRandomTickChunks() { return throttledRandomTickChunks; }
    public static long throttledPrecipitations() { return throttledPrecipitations; }
    public static long protectedGameEvents() { return protectedGameEvents; }
    public static long filteredGameEvents() { return filteredGameEvents; }
    public static long protectedFluidScans() { return protectedFluidScans; }
    public static long skippedFluidScans() { return skippedFluidScans; }
    public static long protectedHopperPolls() { return protectedHopperPolls; }
    public static long throttledHopperPolls() { return throttledHopperPolls; }
    public static long throttledTrialVaultTicks() { return throttledTrialVaultTicks; }
    public static long protectedExplosionRays() { return protectedExplosionRays; }
    public static long cachedExplosionRays() { return cachedExplosionRays; }
    public static long shortCircuitedStructureSpawns() { return shortCircuitedStructureSpawns; }
    public static long fastTypeLookups() { return fastTypeLookups; }
    public static long cachedBlockTickChunks() { return cachedBlockTickChunks; }
    public static long skippedIdleBlockEntityTicks() { return skippedIdleBlockEntityTicks; }
    public static long fastTagLookups() { return fastTagLookups; }
    public static long fastSelectorNbtChecks() { return fastSelectorNbtChecks; }
    public static long fastPoiCompetitorScans() { return fastPoiCompetitorScans; }
    public static long cachedYawpFlagEvals() { return cachedYawpFlagEvals; }
    public static long compactedSleepingBlockEntities() { return compactedSleepingBlockEntities; }
    public static long skippedMicroTimingCalls() { return skippedMicroTimingCalls; }
    public static long restingGravityShortCircuits() { return restingGravityShortCircuits; }
    public static long fastBrainMemoryLookups() { return fastBrainMemoryLookups; }
    public static long fastTagBitLookups() { return fastTagBitLookups; }
    public static long skippedInWallChecks() { return skippedInWallChecks; }
    public static long memoizedSelectorHits() { return memoizedSelectorHits; }
    public static long entityTopologyRevision() { return entityTopologyRevision; }

    public static void recordRestingGravityShortCircuit() {
        restingGravityShortCircuits++;
    }

    public static void recordFastBrainMemoryLookup() {
        fastBrainMemoryLookups++;
    }

    public static void recordFastTagBitLookup() {
        fastTagBitLookups++;
    }

    public static void recordSkippedInWallCheck() {
        skippedInWallChecks++;
    }

    public static void recordMemoizedSelectorHit() {
        memoizedSelectorHits++;
    }

    public static void bumpEntityTopologyRevision() {
        entityTopologyRevision++;
    }

    public static void recordFastTypeLookup() {
        fastTypeLookups++;
    }

    public static void recordFastTagLookup() {
        fastTagLookups++;
    }

    public static void recordFastSelectorNbtCheck() {
        fastSelectorNbtChecks++;
    }

    public static void recordFastPoiCompetitorScan() {
        fastPoiCompetitorScans++;
    }

    public static void recordCachedYawpFlagEval() {
        cachedYawpFlagEvals++;
    }

    public static void recordCompactedSleepingBlockEntities(int count) {
        compactedSleepingBlockEntities += count;
        skippedIdleBlockEntityTicks += count;
    }

    public static void recordSkippedMicroTimingCall() {
        skippedMicroTimingCalls++;
    }

    public static void recordCachedBlockTickChunk() {
        cachedBlockTickChunks++;
    }

    public static void recordSkippedIdleBlockEntityTick() {
        skippedIdleBlockEntityTicks++;
    }

    public static int beginBlockEntityTickPass() {
        int next = ++blockEntityTickEpochCounter;
        if (next == 0) {
            next = ++blockEntityTickEpochCounter;
        }
        currentBlockEntityTickEpoch = next;
        return next;
    }

    public static void endBlockEntityTickPass() {
        currentBlockEntityTickEpoch = 0;
    }

    public static int currentBlockEntityTickEpoch() {
        return currentBlockEntityTickEpoch;
    }

    public static boolean shouldSkipEmptyBeehiveTick(boolean isEmpty) {
        if (isEmpty && enabled()) {
            skippedIdleBlockEntityTicks++;
            return true;
        }
        return false;
    }

    public static boolean shouldSkipIdleBellTick(boolean shaking, boolean resonating) {
        if (!shaking && !resonating && enabled()) {
            skippedIdleBlockEntityTicks++;
            return true;
        }
        return false;
    }

    public static void resetStats() {
        manualGearOverride = -1;
        cachedGearTick = -1;
        cachedGear = 0;
        currentRequiredSelectorTag = null;
        gear1Evaluations = 0;
        gear2Evaluations = 0;
        protectedChunkTicks = 0;
        throttledRandomTickChunks = 0;
        throttledPrecipitations = 0;
        protectedGameEvents = 0;
        filteredGameEvents = 0;
        protectedFluidScans = 0;
        skippedFluidScans = 0;
        protectedHopperPolls = 0;
        throttledHopperPolls = 0;
        throttledTrialVaultTicks = 0;
        protectedExplosionRays = 0;
        cachedExplosionRays = 0;
        shortCircuitedStructureSpawns = 0;
        fastTypeLookups = 0;
        cachedBlockTickChunks = 0;
        skippedIdleBlockEntityTicks = 0;
        fastTagLookups = 0;
        fastSelectorNbtChecks = 0;
        fastPoiCompetitorScans = 0;
        cachedYawpFlagEvals = 0;
        compactedSleepingBlockEntities = 0;
        skippedMicroTimingCalls = 0;
        restingGravityShortCircuits = 0;
        fastBrainMemoryLookups = 0;
        fastTagBitLookups = 0;
        skippedInWallChecks = 0;
        memoizedSelectorHits = 0;
        Arrays.fill(EXPLOSION_KEYS, 0L);
        Arrays.fill(EXPLOSION_VALUES, 0.0f);
    }

    public static String status() {
        return " · 외부 월드엔진 최적화 " + (enabled() ? "ON" : "OFF")
            + "(청크랜덤틱/날씨절약 " + throttledRandomTickChunks + "/" + throttledPrecipitations
            + ", 유체/진동/질식/중력충돌생략 " + skippedFluidScans + "/" + filteredGameEvents + "/" + skippedInWallChecks + "/" + restingGravityShortCircuits
            + ", 호퍼/스포너/폭발캐시/구조물단락 " + throttledHopperPolls + "/" + throttledTrialVaultTicks + "/" + cachedExplosionRays + "/" + shortCircuitedStructureSpawns
            + ", 타입인덱스/청크틱캐시/유휴블록엔티티생략 " + fastTypeLookups + "/" + cachedBlockTickChunks + "/" + skippedIdleBlockEntityTicks
            + ", 태그·NBT셀렉터/POI경쟁/YAWP캐시 " + (fastTagLookups + fastSelectorNbtChecks + memoizedSelectorHits) + "/" + fastPoiCompetitorScans + "/" + cachedYawpFlagEvals
            + ", Brain배열/TagKey인라인 " + fastBrainMemoryLookups + "/" + fastTagBitLookups + ")";
    }
}
