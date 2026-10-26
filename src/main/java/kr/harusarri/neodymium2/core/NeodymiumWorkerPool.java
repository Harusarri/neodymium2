package kr.harusarri.neodymium2.core;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.PlayerSpatialGrid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pufferfish / Petal-inspired Multi-Threaded Pre-Tick Worker Pool.
 * Executes lock-free parallel pre-computation of per-entity BVH region protection (SpatialIndex)
 * and nearest-player 3D spatial grid distances at the start of NaturalSpawner.createState,
 * while keeping 100% single-threaded Vanilla tick order on the main Server thread.
 */
public final class NeodymiumWorkerPool {
    private static final int PARALLELISM = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 6);
    private static final int MIN_PARALLEL_MOBS = 256;
    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);

    private static final ForkJoinPool POOL = new ForkJoinPool(
        PARALLELISM,
        pool -> {
            ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            t.setName("Neodymium2-Worker-" + THREAD_ID.getAndIncrement());
            t.setDaemon(true);
            return t;
        },
        null,
        false
    );

    private static Object[] mobBuffer = new Object[4096];
    private static ServerLevel lastProcessedLevel;
    private static int lastProcessedTick = Integer.MIN_VALUE;
    private static long parallelPrecomputedEntities;

    private NeodymiumWorkerPool() {}

    public static void parallelPrecomputeMobs(ServerLevel level, Collection<?> mobs) {
        int size = mobs.size();
        if (size < MIN_PARALLEL_MOBS) return;
        var snap = Neodymium2.snapshot();
        if (!snap.settings().enabled() || !snap.guard().isEmpty()) return;

        int tick = PlayerSpatialGrid.ensureUpdated(level);
        if (tick < 0 || (level == lastProcessedLevel && tick == lastProcessedTick)) {
            return;
        }
        lastProcessedLevel = level;
        lastProcessedTick = tick;

        if (mobBuffer.length < size) {
            mobBuffer = new Object[Math.max(size, mobBuffer.length * 2)];
        }
        Object[] buf = mobs.toArray(mobBuffer);
        String dimKey = level.dimension().identifier().toString();
        SpatialIndex index = snap.index();
        boolean hasRegions = index.size() > 0;
        long gen = snap.generation();

        int tasksCount = Math.min(PARALLELISM, (size + 255) >>> 8);
        if (tasksCount <= 1) {
            processSlice(buf, 0, size, level, dimKey, index, hasRegions, gen, tick);
            parallelPrecomputedEntities += size;
            return;
        }

        int chunkSize = (size + tasksCount - 1) / tasksCount;
        ForkJoinTask<?>[] submitted = new ForkJoinTask<?>[tasksCount - 1];
        int submittedCount = 0;
        for (int t = 0; t < tasksCount - 1; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(size, start + chunkSize);
            if (start >= end) break;
            submitted[submittedCount++] = POOL.submit(() ->
                processSlice(buf, start, end, level, dimKey, index, hasRegions, gen, tick)
            );
        }
        int mainStart = (tasksCount - 1) * chunkSize;
        if (mainStart < size) {
            processSlice(buf, mainStart, size, level, dimKey, index, hasRegions, gen, tick);
        }
        for (int i = 0; i < submittedCount; i++) {
            submitted[i].join();
        }
        parallelPrecomputedEntities += size;
    }

    private static void processSlice(
        Object[] buf,
        int start,
        int end,
        ServerLevel level,
        String dimKey,
        SpatialIndex index,
        boolean hasRegions,
        long gen,
        int tick
    ) {
        for (int i = start; i < end; i++) {
            Object obj = buf[i];
            if (!(obj instanceof Entity entity) || !(entity instanceof EntityFastStateAccess access)) {
                continue;
            }
            if (access.neo$getPlayerDistTick() != tick) {
                double d2 = PlayerSpatialGrid.computeDistSqSnapshot(entity.getX(), entity.getY(), entity.getZ());
                access.neo$setPlayerDistCache(tick, d2);
            }
            if (hasRegions && !entity.isPassenger() && !entity.isVehicle()) {
                AABB bb = entity.getBoundingBox();
                if (access.neo$getProtGen() != gen || access.neo$getProtBb() != bb || access.neo$getProtLevel() != level) {
                    boolean hit = index.contains(dimKey, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
                    access.neo$setProtCache(gen, bb, level, hit);
                }
            }
        }
    }

    public static long parallelPrecomputedEntities() {
        return parallelPrecomputedEntities;
    }

    public static void reset() {
        lastProcessedLevel = null;
        lastProcessedTick = Integer.MIN_VALUE;
        parallelPrecomputedEntities = 0;
    }
}
