package kr.harusarri.neodymium2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Region-aware LagFreeSpawning (fast block-shape collision check + precooked Mob instance cache)
 * and Overcrowded Chunk Entity Density AI Throttler, strictly restricted to CHUNK_OUTSIDE
 * (vanillaSensitive: false).
 *
 * Inside vanillaSensitive: true regions, 100% pure Vanilla behavior is unconditionally preserved.
 */
public final class OutsideSpawnAndDensity {
    public static final int OVERCROWD_THRESHOLD = 24;
    private static final int DENSITY_MASK = 4095;
    private static final long[] CHUNK_DENSITY = new long[DENSITY_MASK + 1];
    private static final Map<Level, Map<EntityType<?>, Mob>> PRECOOKED_MOBS = new WeakHashMap<>();
    private static ServerLevel lastOutsideLevel;
    private static EntityType<?> lastOutsideType;

    private static long fastCollisionHits;
    private static long precookedReuses;
    private static long overcrowdedThrottles;
    private static long blockedNametagBypasses;

    private OutsideSpawnAndDensity() {}

    /**
     * Outside-only fast AABB block collision check (Carpet lagFreeSpawning equivalent).
     * Bypasses expensive VoxelShape stream creation when candidate blocks are air (Shapes.empty())
     * or full cubes (Shapes.block()), while running 100% pure Vanilla inside protected chunks.
     */
    public static boolean doesNotCollide(ServerLevel level, AABB bb, Operation<Boolean> original) {
        if (!RegionSpawner.enabled() || RegionSpawner.isInsideProtectedChunk()) {
            return original.call(level, bb);
        }
        int minX = Mth.floor(bb.minX);
        int maxX = Mth.ceil(bb.maxX);
        int minY = Mth.floor(bb.minY);
        int maxY = Mth.ceil(bb.maxY);
        int minZ = Mth.floor(bb.minZ);
        int maxZ = Mth.ceil(bb.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                pos.set(x, minY - 1, z);
                BlockState below = level.getBlockState(pos);
                if (below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS) || below.getBlock() instanceof FenceGateBlock) {
                    return original.call(level, bb);
                }
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (shape == Shapes.empty()) {
                        continue;
                    }
                    if (shape == Shapes.block()) {
                        fastCollisionHits++;
                        return false;
                    }
                    return original.call(level, bb);
                }
            }
        }
        fastCollisionHits++;
        return true;
    }

    /**
     * Outside-only precooked Mob cache (Carpet lagFreeSpawning equivalent).
     * Reuses unspawned Mob instances per (ServerLevel, EntityType) when spawn rules or obstruction
     * checks fail in CHUNK_OUTSIDE, while running 100% pure Vanilla inside protected chunks.
     */
    public static Mob getMobForSpawn(ServerLevel level, EntityType<?> type, Operation<Mob> original) {
        if (!RegionSpawner.enabled() || RegionSpawner.isInsideProtectedChunk()) {
            lastOutsideLevel = null;
            lastOutsideType = null;
            return original.call(level, type);
        }
        lastOutsideLevel = level;
        lastOutsideType = type;
        Map<EntityType<?>, Mob> map = PRECOOKED_MOBS.computeIfAbsent(level, k -> new IdentityHashMap<>());
        Mob cached = map.get(type);
        if (cached != null && !cached.isRemoved() && cached.level() == level) {
            precookedReuses++;
            return cached;
        }
        Mob fresh = original.call(level, type);
        if (fresh != null) {
            map.put(type, fresh);
        }
        return fresh;
    }

    public static void onMobSpawned() {
        if (lastOutsideLevel != null && lastOutsideType != null) {
            Map<EntityType<?>, Mob> map = PRECOOKED_MOBS.get(lastOutsideLevel);
            if (map != null) {
                map.remove(lastOutsideType);
            }
            lastOutsideType = null;
        }
    }

    /**
     * O(1) per-chunk entity density tracker for CHUNK_OUTSIDE.
     * Tracks rolling entity evaluations per chunk across ticks and returns the effective entity count
     * in the entity's chunk.
     */
    public static int recordAndGetChunkDensity(Entity entity, int serverTick) {
        BlockPos pos = entity.blockPosition();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        int slot = (int) (((cx * 0x9E3779B97F4A7C15L ^ cz * 0xBF58476D1CE4E5B9L) >>> 32) & DENSITY_MASK);
        long posTag = ((long) (cx & 0x3FFFF) << 46) | ((long) (cz & 0x3FFFF) << 28);
        int tick12 = serverTick & 0xFFF;
        long entry = CHUNK_DENSITY[slot];
        int prevCount = 0;
        int currCount = 1;
        if ((entry & 0xFFFFFFFFF0000000L) == posTag) {
            int entryTick = (int) ((entry >>> 16) & 0xFFFL);
            int oldPrev = (int) ((entry >>> 8) & 0xFFL);
            int oldCurr = (int) (entry & 0xFFL);
            if (entryTick == tick12) {
                prevCount = oldPrev;
                currCount = Math.min(255, oldCurr + 1);
            } else if (((tick12 - entryTick) & 0xFFF) <= 4) {
                prevCount = oldCurr;
                currCount = 1;
            }
        }
        CHUNK_DENSITY[slot] = posTag | ((long) tick12 << 16) | ((long) prevCount << 8) | currCount;
        int density = Math.max(prevCount, currCount);
        if (density > OVERCROWD_THRESHOLD) {
            overcrowdedThrottles++;
        }
        return density;
    }

    public static void recordBlockedNametagBypass(Entity entity) {
        if (entity.hasCustomName()) {
            String exempt = Neodymium2.snapshot().settings().villagerLobotomy().exemptName();
            if (!exempt.isEmpty() && exempt.equals(entity.getCustomName().getString())) {
                blockedNametagBypasses++;
            }
        }
    }

    public static long fastCollisionHits() { return fastCollisionHits; }
    public static long precookedReuses() { return precookedReuses; }
    public static long overcrowdedThrottles() { return overcrowdedThrottles; }
    public static long blockedNametagBypasses() { return blockedNametagBypasses; }

    public static void resetStats() {
        fastCollisionHits = 0;
        precookedReuses = 0;
        overcrowdedThrottles = 0;
        blockedNametagBypasses = 0;
        PRECOOKED_MOBS.clear();
        Arrays.fill(CHUNK_DENSITY, 0L);
    }
}
