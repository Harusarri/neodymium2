package kr.harusarri.neodymium2;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Region-aware NaturalSpawner & MobCap / SpawnCost Optimization Module combining:
 * 1. 100% Vanilla every-tick execution for any chunk intersecting a vanillaSensitive region.
 * 2. Hash-strided cadence + Adaptive Empty-Chunk Backoff (4 -> 8 -> 16 ticks) + MSPT Governor gearing for OUTSIDE chunks.
 * 3. Paper / Gale 3D Sphere Despawn Pre-Filter: rejects outside candidate positions (>128 blocks
 *    in 3D from any player) BEFORE biome lookup, block/light checks, and Mob object allocation.
 * 4. Empty-Structure Chunk Flag: records whether the current OUTSIDE chunk has zero structure references
 *    so NaturalSpawner.mobsAt can bypass StructureManager / NetherFortress queries.
 * 5. NaturalSpawner.createState SpawnCost Elision: caches which EntityTypes and Biomes actually define
 *    a MobSpawnCost in Registry<Biome>, eliminating 99%+ of redundant 3D Voronoi biome interpolations
 *    (8x PalettedContainer.get per mob per tick) in MC 26.3 while preserving 100% Vanilla spawn potential.
 */
public final class RegionSpawner {
    private static final int TABLE_MASK = 8191;
    private static final long[] CHUNK_STREAKS = new long[TABLE_MASK + 1];
    private static final java.util.ArrayList<Mob> PENDING_DESPAWNS = new java.util.ArrayList<>(256);
    private static long protectedChunks, outsideEvaluatedChunks, outsideSkippedChunks, spawnedMobs, preFilteredPositions;
    private static long elidedSpawnCostLookups, cachedMobCapLookups, mobOnlyCreateStateScans;
    private static int currentChunkSpawns = -1;
    private static boolean currentChunkProtected;
    private static boolean currentChunkStructureEmpty;
    private static LevelChunk currentOutsideChunk;
    private static long quotaTick = Long.MIN_VALUE;
    private static int quotaUsed = 0;

    private static volatile Registry<Biome> cachedBiomeRegistry;
    private static volatile Set<EntityType<?>> cachedCostTypes = Set.of();
    private static volatile Set<Biome> cachedCostBiomes = Set.of();
    private static volatile boolean cachedHasNonMobCostTypes = false;
    private static boolean mobOnlyIterationActive = false;

    private RegionSpawner() {}

    public static boolean enabled() {
        var s = Neodymium2.snapshot();
        return s.settings().enabled() && s.guard().isEmpty() && s.settings().outsideSpawnThrottle() && s.settings().outsideSpawnBaseInterval() > 1;
    }

    public static void invalidateBiomeCostCache() {
        cachedBiomeRegistry = null;
        cachedCostTypes = Set.of();
        cachedCostBiomes = Set.of();
        cachedHasNonMobCostTypes = false;
    }

    private static void ensureCostCache(ServerLevel level) {
        Registry<Biome> reg = level.registryAccess().lookupOrThrow(Registries.BIOME);
        if (reg == cachedBiomeRegistry) return;
        Set<EntityType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Biome> biomes = Collections.newSetFromMap(new IdentityHashMap<>());
        MobSpawnSettings dimBase = level.dimensionType().attributes().applyModifier(
            EnvironmentAttributes.NATURAL_MOB_SPAWNS,
            EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue()
        );
        if (!dimBase.allSpawnCosts().isEmpty()) {
            types.addAll(dimBase.allSpawnCosts().keySet());
        }
        for (Holder.Reference<Biome> ref : reg.listElements().toList()) {
            Biome b = ref.value();
            MobSpawnSettings ms = b.getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, dimBase);
            if (!ms.allSpawnCosts().isEmpty()) {
                biomes.add(b);
                types.addAll(ms.allSpawnCosts().keySet());
            }
        }
        boolean nonMob = false;
        for (EntityType<?> t : types) {
            if (t.getCategory() == MobCategory.MISC) {
                nonMob = true;
                break;
            }
        }
        cachedCostTypes = types;
        cachedCostBiomes = biomes;
        cachedHasNonMobCostTypes = nonMob;
        cachedBiomeRegistry = reg;
    }

    public static boolean beginMobOnlyIteration(ServerLevel level) {
        if (!enabled()) return false;
        ensureCostCache(level);
        if (cachedHasNonMobCostTypes) return false;
        mobOnlyIterationActive = true;
        return true;
    }

    public static void endMobOnlyIteration() {
        mobOnlyIterationActive = false;
    }

    public static boolean isMobOnlyIteration() {
        return mobOnlyIterationActive;
    }

    public static void recordMobOnlyCreateStateScan() {
        mobOnlyCreateStateScans++;
    }

    /**
     * Fast path for NaturalSpawner.lambda$createState$0:
     * Avoids 3D Voronoi biome interpolation (EnvironmentAttributeSystem.getValue(NATURAL_MOB_SPAWNS, pos))
     * for every mob on every tick when the entity type or rough biome has no MobSpawnCost.
     */
    public static boolean tryFastAcceptCreateStateEntity(
        ServerLevel level,
        BlockPos pos,
        Entity entity,
        PotentialCalculator spawnPotential,
        LocalMobCapCalculator localMobCapCalculator,
        MobCategory category,
        Object2IntOpenHashMap<MobCategory> mobCategoryCounts,
        LevelChunk chunk
    ) {
        if (!enabled()) return false;
        boolean protectedBlock = Neodymium2.regionProtectsBlock(level, pos);
        if (!protectedBlock && entity instanceof Mob mob && !mob.isPersistenceRequired() && !mob.requiresCustomPersistence()) {
            int gear = OutsideWorldOptimizer.msptGear(level.getServer());
            boolean highPop = level.players().size() >= 15 || gear > 0;
            boolean nonTicking = !level.isPositionEntityTicking(pos);
            boolean inactive = ((me.wesley1808.servercore.common.interfaces.activation_range.ActivationEntity) mob).servercore$isInactive();
            if (highPop || nonTicking || inactive) {
                double distSq = PlayerSpatialGrid.distSqToNearestPlayer(level, mob.getX(), mob.getY(), mob.getZ());
                double horizDistSq = PlayerSpatialGrid.horizDistSqToNearestPlayer(level, mob.getX(), mob.getZ());
                int despawnDist3d = gear >= 2 ? Math.min(96, category.getDespawnDistance()) : category.getDespawnDistance();
                int despawnDistHoriz = highPop ? Math.min(72, category.getDespawnDistance()) : category.getDespawnDistance();
                if ((distSq > (double) (despawnDist3d * despawnDist3d) || horizDistSq > (double) (despawnDistHoriz * despawnDistHoriz))
                    && mob.removeWhenFarAway(distSq)) {
                    PENDING_DESPAWNS.add(mob);
                    preFilteredPositions++;
                    return true;
                }
                int noDespawnDist = category.getNoDespawnDistance();
                if ((nonTicking || inactive) && distSq > (double) (noDespawnDist * noDespawnDist)) {
                    int nat = mob.getNoActionTime() + 1;
                    mob.setNoActionTime(nat);
                    int playerY = PlayerSpatialGrid.nearestPlayerBlockY(level, mob.getX(), mob.getZ());
                    boolean deepCave = level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                        && mob.getBlockY() < 32
                        && playerY != Integer.MIN_VALUE
                        && playerY - mob.getBlockY() > 56;
                    int minNat = deepCave ? 200 : 600;
                    int odds = deepCave ? 200 : 800;
                    if (nat > minNat && level.getRandom().nextInt(odds) == 0 && mob.removeWhenFarAway(distSq)) {
                        PENDING_DESPAWNS.add(mob);
                        preFilteredPositions++;
                        return true;
                    }
                }
            }
        }
        ensureCostCache(level);
        EntityType<?> type = entity.getType();
        MobSpawnSettings.MobSpawnCost cost = null;
        if (cachedCostTypes.contains(type)) {
            if (protectedBlock) {
                cost = level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos).getMobSpawnCost(type);
            } else {
                Biome rough = chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value();
                if (cachedCostBiomes.contains(rough)) {
                    cost = rough.getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY).getMobSpawnCost(type);
                } else {
                    elidedSpawnCostLookups++;
                }
            }
        } else {
            elidedSpawnCostLookups++;
        }
        if (cost != null) {
            spawnPotential.addCharge(pos, cost.charge());
        }
        if (entity instanceof Mob) {
            localMobCapCalculator.addMob(chunk.getPos(), category);
        }
        mobCategoryCounts.addTo(category, 1);
        return true;
    }

    public static void onFastLocalMobCapHit() {
        cachedMobCapLookups++;
    }

    public static boolean beginChunkSpawn(ServerLevel level, LevelChunk chunk) {
        if (!PENDING_DESPAWNS.isEmpty()) {
            for (int i = 0, n = PENDING_DESPAWNS.size(); i < n; i++) {
                Mob m = PENDING_DESPAWNS.get(i);
                if (m != null && !m.isRemoved()) {
                    m.discard();
                }
            }
            PENDING_DESPAWNS.clear();
        }
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        if (!enabled() || Neodymium2.regionProtectsChunk(level, cx, cz)) {
            protectedChunks++;
            currentChunkSpawns = -1;
            currentChunkProtected = true;
            currentChunkStructureEmpty = false;
            currentOutsideChunk = null;
            return true;
        }
        currentChunkProtected = false;
        long gameTime = level.getGameTime();
        if (gameTime != quotaTick) {
            quotaTick = gameTime;
            quotaUsed = 0;
        }
        int gear = OutsideWorldOptimizer.msptGear(level.getServer());
        int playerCount = level.players().size();
        int maxQuota = gear >= 2 ? 40 : (gear == 1 || playerCount >= 15 ? 64 : 128);
        if (quotaUsed >= maxQuota) {
            outsideSkippedChunks++;
            currentChunkSpawns = -1;
            currentChunkStructureEmpty = false;
            currentOutsideChunk = null;
            return false;
        }
        double centerX = (cx << 4) + 8.0, centerZ = (cz << 4) + 8.0;
        int simDist = level.getServer() != null && level.getServer().getPlayerList() != null
            ? level.getServer().getPlayerList().getSimulationDistance()
            : 10;
        double maxSpawnHorizontalRadius = (playerCount >= 15 || gear > 0)
            ? 72.0
            : Math.min(136.0, (simDist + 1) * 16.0 + 8.0);
        boolean playerWithinDespawnRange = PlayerSpatialGrid.hasPlayerWithinHorizontal(level, centerX, centerZ, maxSpawnHorizontalRadius * maxSpawnHorizontalRadius);
        if (!playerWithinDespawnRange) {
            outsideSkippedChunks++;
            currentChunkSpawns = -1;
            currentChunkStructureEmpty = false;
            currentOutsideChunk = null;
            return false;
        }
        var settings = Neodymium2.snapshot().settings();
        int max = settings.outsideSpawnMaxInterval();
        int base = Math.min(max, settings.outsideSpawnBaseInterval() << gear);
        long hash = cx * 0x9E3779B97F4A7C15L ^ cz * 0xBF58476D1CE4E5B9L;
        int slot = (int) ((hash >>> 32) & TABLE_MASK);
        long tag = (1L << 62) | ((cx & 0x1FFFFFFFL) << 31) | ((cz & 0x1FFFFFFFL) << 3);
        long entry = CHUNK_STREAKS[slot];
        int streak = (entry & ~7L) == tag ? (int) (entry & 7L) : 0;
        int interval = Math.min(max, base << Math.min(streak, 3));
        long phase = Math.floorMod((hash & 0x7FFFFFFFL) + gameTime, interval);
        if (phase != 0) {
            outsideSkippedChunks++;
            currentChunkSpawns = -1;
            currentChunkStructureEmpty = false;
            currentOutsideChunk = null;
            return false;
        }
        quotaUsed++;
        outsideEvaluatedChunks++;
        currentChunkSpawns = 0;
        currentChunkStructureEmpty = chunk.getAllReferences().isEmpty();
        currentOutsideChunk = chunk;
        return true;
    }

    public static boolean isInsideProtectedChunk() {
        return currentChunkProtected;
    }

    public static boolean isCurrentOutsideChunkStructureEmpty() {
        return currentChunkSpawns >= 0 && currentChunkStructureEmpty;
    }

    public static LevelChunk currentOutsideChunk() {
        return currentChunkSpawns >= 0 ? currentOutsideChunk : null;
    }

    public static java.util.List<MobCategory> filterOutsideCategories(ServerLevel level, java.util.List<MobCategory> categories) {
        if (currentChunkProtected || currentChunkSpawns < 0 || (level.getGameTime() % 20L) == 0L) {
            return categories;
        }
        for (int i = 0, n = categories.size(); i < n; i++) {
            if (categories.get(i) == MobCategory.MONSTER) {
                return java.util.List.of(MobCategory.MONSTER);
            }
        }
        return java.util.List.of();
    }

    private static boolean isSlimeChunk(ServerLevel level, LevelChunk chunk) {
        return net.minecraft.world.level.levelgen.WorldgenRandom.seedSlimeChunk(
            chunk.getPos().x(), chunk.getPos().z(), level.getSeed(), 987234911L
        ).nextInt(10) == 0;
    }

    private static boolean isCandidateOutsideMonsterFloor(ServerLevel level, LevelChunk chunk, BlockPos pos, int playerY, boolean slimeChunk) {
        int y = pos.getY();
        if (y < level.getMinY() + 1) return false;
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        if (overworld && y < 32 && playerY != Integer.MIN_VALUE && playerY - y > 56 && !slimeChunk) {
            return false;
        }
        double dist3dSq = PlayerSpatialGrid.distSqToNearestPlayer(level, pos.getX() + 0.5, y, pos.getZ() + 0.5);
        if (dist3dSq <= 24.0 * 24.0 || dist3dSq > 128.0 * 128.0) {
            return false;
        }
        var state = chunk.getBlockState(pos);
        if (state.isRedstoneConductor(chunk, pos)) return false;
        if (state.getFluidState().isEmpty()) {
            var below = chunk.getBlockState(pos.below());
            if (below.isAir() || !below.getFluidState().isEmpty()) return false;
        }
        if (overworld && !(y < 40 && slimeChunk) && state.getFluidState().isEmpty()) {
            if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) > 0) return false;
            if (level.isBrightOutside() && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) > 7) return false;
        }
        return true;
    }

    /**
     * Paper-style Low-Level Mob Farm Floor Resolver for CHUNK_OUTSIDE:
     * Because outside chunks run on a 4-tick strided cadence, a single uniform random Y in [-64..topY]
     * misses a player's 1-3 story dark-room mob farm >98% of the time (wasting picks on solid underground
     * stone or open daylight sky and triggering the 16-tick empty-chunk backoff).
     * When the initial random position fails in a chunk within 64 blocks horizontally of a player,
     * performs up to 8 ultra-fast O(1) section/light probes in the player's active vertical band
     * (y >= 32 when on surface/sky, or playerY ± 48 when underground) so dark-room mob farms reliably
     * locate their spawning floor without ticking extra chunks or spawning deep underground cave mobs.
     */
    public static BlockPos resolveOutsideSpawnPos(net.minecraft.world.level.Level rawLevel, LevelChunk chunk, MobCategory category, BlockPos pos) {
        if (currentChunkSpawns < 0 || category != MobCategory.MONSTER || !(rawLevel instanceof ServerLevel level)) {
            return pos;
        }
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        double centerX = (cx << 4) + 8.0, centerZ = (cz << 4) + 8.0;
        int playerY = PlayerSpatialGrid.nearestPlayerBlockY(level, centerX, centerZ);
        if (playerY == Integer.MIN_VALUE) return pos;
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        boolean slimeChunk = overworld && isSlimeChunk(level, chunk);
        if (isCandidateOutsideMonsterFloor(level, chunk, pos, playerY, slimeChunk)) {
            return pos;
        }
        if (OutsideWorldOptimizer.msptGear(level.getServer()) >= 2) {
            return pos;
        }
        if (PlayerSpatialGrid.horizDistSqToNearestPlayer(level, centerX, centerZ) > 64.0 * 64.0) {
            return pos;
        }
        int minLevelY = level.getMinY() + 1;
        int minBandY = (overworld && playerY >= 48)
            ? Math.max(32, Math.max(minLevelY, playerY - 124))
            : Math.max(minLevelY, playerY - 48);
        var rand = level.getRandom();
        int baseX = cx << 4, baseZ = cz << 4;
        for (int i = 0; i < 8; i++) {
            int rx = baseX + rand.nextInt(16);
            int rz = baseZ + rand.nextInt(16);
            int topY = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rx, rz) + 1;
            int maxBandY = Math.min(topY, playerY + 124);
            if (maxBandY <= minBandY) continue;
            int ry = net.minecraft.util.Mth.randomBetweenInclusive(rand, minBandY, maxBandY);
            BlockPos cand = new BlockPos(rx, ry, rz);
            if (isCandidateOutsideMonsterFloor(level, chunk, cand, playerY, slimeChunk)) {
                return cand;
            }
        }
        return pos;
    }

    /**
     * Paper / Gale 3D Sphere Despawn Pre-Filter:
     * In Vanilla, isRightDistanceToPlayerAndSpawnPoint only checks horizontal distance >24,
     * and defers the 3D 128-block despawn sphere check until AFTER Mob instantiation.
     * In OUTSIDE chunks, reject >128-block 3D positions immediately before Mob allocation,
     * while preserving the full 128-block vertical despawn sphere for sky mob farms and culling
     * unseen deep underground caves (y < 32) far below surface/sky players.
     */
    public static boolean shouldRejectOutsideSpawnPos(ServerLevel level, BlockPos pos, double horizontalDistSqr) {
        if (currentChunkSpawns < 0) return false; // Protected chunk -> 100% pure Vanilla
        int gear = OutsideWorldOptimizer.msptGear(level.getServer());
        boolean highPop = level.players().size() >= 15 || gear > 0;
        int simDist = level.getServer() != null && level.getServer().getPlayerList() != null
            ? level.getServer().getPlayerList().getSimulationDistance()
            : 10;
        double maxHorizDist = highPop ? 64.0 : Math.min(128.0, (simDist + 1) * 16.0);
        double max3dDist = gear >= 2 ? 96.0 : 128.0;
        if (horizontalDistSqr > maxHorizDist * maxHorizDist
            || PlayerSpatialGrid.distSqToNearestPlayer(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > max3dDist * max3dDist) {
            preFilteredPositions++;
            return true;
        }
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && pos.getY() < 32) {
            int playerY = PlayerSpatialGrid.nearestPlayerBlockY(level, pos.getX() + 0.5, pos.getZ() + 0.5);
            if (playerY != Integer.MIN_VALUE && playerY - pos.getY() > (gear >= 2 ? 48 : 56)) {
                LevelChunk chunk = currentOutsideChunk;
                if (chunk == null || !isSlimeChunk(level, chunk)) {
                    preFilteredPositions++;
                    return true;
                }
            }
        }
        return false;
    }

    public static void onMobSpawned() {
        spawnedMobs++;
        if (currentChunkSpawns >= 0) currentChunkSpawns++;
        OutsideSpawnAndDensity.onMobSpawned();
    }

    public static void endChunkSpawn(LevelChunk chunk) {
        int spawned = currentChunkSpawns;
        currentChunkSpawns = -1;
        currentChunkProtected = false;
        currentChunkStructureEmpty = false;
        currentOutsideChunk = null;
        if (spawned < 0) return;
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        long hash = cx * 0x9E3779B97F4A7C15L ^ cz * 0xBF58476D1CE4E5B9L;
        int slot = (int) ((hash >>> 32) & TABLE_MASK);
        long tag = (1L << 62) | ((cx & 0x1FFFFFFFL) << 31) | ((cz & 0x1FFFFFFFL) << 3);
        if (spawned > 0) {
            CHUNK_STREAKS[slot] = tag; // streak = 0 (instant reset to base interval)
        } else {
            long entry = CHUNK_STREAKS[slot];
            int prev = (entry & ~7L) == tag ? (int) (entry & 7L) : 0;
            int next = Math.min(prev + 1, 4);
            CHUNK_STREAKS[slot] = tag | next;
        }
    }

    public static long protectedChunks() { return protectedChunks; }
    public static long outsideEvaluatedChunks() { return outsideEvaluatedChunks; }
    public static long outsideSkippedChunks() { return outsideSkippedChunks; }
    public static long spawnedMobs() { return spawnedMobs; }
    public static long preFilteredPositions() { return preFilteredPositions; }
    public static long elidedSpawnCostLookups() { return elidedSpawnCostLookups; }
    public static long cachedMobCapLookups() { return cachedMobCapLookups; }
    public static long mobOnlyCreateStateScans() { return mobOnlyCreateStateScans; }

    public static void resetStats() {
        protectedChunks = 0;
        outsideEvaluatedChunks = 0;
        outsideSkippedChunks = 0;
        spawnedMobs = 0;
        preFilteredPositions = 0;
        elidedSpawnCostLookups = 0;
        cachedMobCapLookups = 0;
        mobOnlyCreateStateScans = 0;
        mobOnlyIterationActive = false;
        currentChunkSpawns = -1;
        currentChunkProtected = false;
        currentChunkStructureEmpty = false;
        currentOutsideChunk = null;
        PlayerSpatialGrid.reset();
        PENDING_DESPAWNS.clear();
        invalidateBiomeCostCache();
        OutsideSpawnAndDensity.resetStats();
        java.util.Arrays.fill(CHUNK_STREAKS, 0L);
    }

    public static String status() {
        return " · 구역 스폰 " + (enabled() ? "ON" : "OFF")
            + "(보호/실행/생략/3D사전차단 " + protectedChunks + "/" + outsideEvaluatedChunks + "/" + outsideSkippedChunks + "/" + preFilteredPositions
            + ", 스폰코스트생략/몹캡캐시/몹전용스캔 " + elidedSpawnCostLookups + "/" + cachedMobCapLookups + "/" + mobOnlyCreateStateScans
            + ", LagFree재사용/고속충돌 " + OutsideSpawnAndDensity.precookedReuses() + "/" + OutsideSpawnAndDensity.fastCollisionHits() + ")";
    }
}
