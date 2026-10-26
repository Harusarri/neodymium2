package kr.harusarri.neodymium2;

import kr.harusarri.neodymium2.core.EntityFastStateAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import java.util.Arrays;
import java.util.List;

/**
 * Per-Tick Primitive Spatial Hash Grid for Player Positions (Teschner et al., 2003).
 * Uses 128x128-block spatial hash cells (>> 7) when player count > 16 so 3x3 cell neighborhood
 * queries are 100% mathematically exact for all radii <= 128.0 blocks while reducing O(P) scans
 * to O(1), and supports lock-free parallel read access from NeodymiumWorkerPool.
 */
public final class PlayerSpatialGrid {
    private static final int BUCKET_MASK = 255;
    private static final int[] bucketHeads = new int[BUCKET_MASK + 1];

    private static ServerLevel cachedLevel;
    private static int cachedTick = Integer.MIN_VALUE;
    private static int count;
    private static double[] px = new double[64];
    private static double[] py = new double[64];
    private static double[] pz = new double[64];
    private static ServerPlayer[] playerRefs = new ServerPlayer[64];
    private static ItemStack[] mainHand = new ItemStack[64];
    private static ItemStack[] offHand = new ItemStack[64];
    private static long[] playerCellKeys = new long[64];
    private static int[] nextInBucket = new int[64];

    private PlayerSpatialGrid() {}

    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private static int bucketIndex(long cellKey) {
        long h = cellKey * 0x9E3779B97F4A7C15L;
        return (int) ((h ^ (h >>> 32)) & BUCKET_MASK);
    }

    public static int ensureUpdated(ServerLevel level) {
        var server = level.getServer();
        int tick = server != null ? server.getTickCount() : -1;
        if (level == cachedLevel && tick == cachedTick && tick >= 0) {
            return tick;
        }
        List<ServerPlayer> players = level.players();
        int size = players.size();
        if (px.length < size) {
            int cap = Math.max(size, px.length * 2);
            px = new double[cap];
            py = new double[cap];
            pz = new double[cap];
            playerRefs = new ServerPlayer[cap];
            mainHand = new ItemStack[cap];
            offHand = new ItemStack[cap];
            playerCellKeys = new long[cap];
            nextInBucket = new int[cap];
        }
        Arrays.fill(bucketHeads, -1);
        int n = 0;
        for (int i = 0; i < size; i++) {
            ServerPlayer p = players.get(i);
            if (!p.isSpectator() && p.isAlive()) {
                double x = p.getX(), y = p.getY(), z = p.getZ();
                px[n] = x;
                py[n] = y;
                pz[n] = z;
                playerRefs[n] = p;
                mainHand[n] = p.getMainHandItem();
                offHand[n] = p.getOffhandItem();
                long cKey = packCell(Mth.floor(x) >> 7, Mth.floor(z) >> 7);
                playerCellKeys[n] = cKey;
                int b = bucketIndex(cKey);
                nextInBucket[n] = bucketHeads[b];
                bucketHeads[b] = n;
                n++;
            }
        }
        count = n;
        cachedLevel = level;
        cachedTick = tick;
        return tick;
    }

    /**
     * Lock-free thread-safe read of the current tick's spatial grid snapshot.
     * Must only be called after ensureUpdated(level) has been invoked on the main thread for this tick.
     */
    public static double computeDistSqSnapshot(double x, double y, double z) {
        int n = count;
        if (n <= 0) return Double.MAX_VALUE;
        double[] xs = px, ys = py, zs = pz;
        if (n <= 16) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double dx = xs[i] - x;
                double dy = ys[i] - y;
                double dz = zs[i] - z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) best = d2;
            }
            return best;
        }
        int cx = Mth.floor(x) >> 7;
        int cz = Mth.floor(z) >> 7;
        long[] keys = playerCellKeys;
        int[] next = nextInBucket;
        int[] heads = bucketHeads;
        double best = Double.MAX_VALUE;
        for (int dxCell = -1; dxCell <= 1; dxCell++) {
            for (int dzCell = -1; dzCell <= 1; dzCell++) {
                long targetKey = packCell(cx + dxCell, cz + dzCell);
                int idx = heads[bucketIndex(targetKey)];
                while (idx != -1) {
                    if (keys[idx] == targetKey) {
                        double dx = xs[idx] - x;
                        double dy = ys[idx] - y;
                        double dz = zs[idx] - z;
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 < best) best = d2;
                    }
                    idx = next[idx];
                }
            }
        }
        return best;
    }

    public static double distSqToNearestPlayer(ServerLevel level, Entity entity) {
        int tick = ensureUpdated(level);
        if (tick >= 0 && entity instanceof EntityFastStateAccess access) {
            if (access.neo$getPlayerDistTick() == tick) {
                return access.neo$getPlayerDistSq();
            }
            double d2 = computeDistSqSnapshot(entity.getX(), entity.getY(), entity.getZ());
            access.neo$setPlayerDistCache(tick, d2);
            return d2;
        }
        return computeDistSqSnapshot(entity.getX(), entity.getY(), entity.getZ());
    }

    public static double distSqToNearestPlayer(ServerLevel level, double x, double y, double z) {
        ensureUpdated(level);
        return computeDistSqSnapshot(x, y, z);
    }

    public static boolean hasFoodHoldingPlayerWithin(ServerLevel level, Animal animal, double maxDistSq) {
        ensureUpdated(level);
        int n = count;
        if (n <= 0) return false;
        double x = animal.getX(), y = animal.getY(), z = animal.getZ();
        double[] xs = px, ys = py, zs = pz;
        ItemStack[] mh = mainHand, oh = offHand;
        if (n <= 16 || maxDistSq > 16384.0) {
            for (int i = 0; i < n; i++) {
                double dx = xs[i] - x;
                double dy = ys[i] - y;
                double dz = zs[i] - z;
                if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                    ItemStack m = mh[i];
                    if (m != null && !m.isEmpty() && animal.isFood(m)) return true;
                    ItemStack o = oh[i];
                    if (o != null && !o.isEmpty() && animal.isFood(o)) return true;
                }
            }
            return false;
        }
        int cx = Mth.floor(x) >> 7;
        int cz = Mth.floor(z) >> 7;
        long[] keys = playerCellKeys;
        int[] next = nextInBucket;
        int[] heads = bucketHeads;
        for (int dxCell = -1; dxCell <= 1; dxCell++) {
            for (int dzCell = -1; dzCell <= 1; dzCell++) {
                long targetKey = packCell(cx + dxCell, cz + dzCell);
                int idx = heads[bucketIndex(targetKey)];
                while (idx != -1) {
                    if (keys[idx] == targetKey) {
                        double dx = xs[idx] - x;
                        double dy = ys[idx] - y;
                        double dz = zs[idx] - z;
                        if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                            ItemStack m = mh[idx];
                            if (m != null && !m.isEmpty() && animal.isFood(m)) return true;
                            ItemStack o = oh[idx];
                            if (o != null && !o.isEmpty() && animal.isFood(o)) return true;
                        }
                    }
                    idx = next[idx];
                }
            }
        }
        return false;
    }

    public static boolean hasPlayerWithinHorizontal(ServerLevel level, double x, double z, double maxDistSq) {
        ensureUpdated(level);
        int n = count;
        if (n <= 0) return false;
        double[] xs = px, zs = pz;
        if (n <= 16 || maxDistSq > 16384.0) {
            for (int i = 0; i < n; i++) {
                double dx = xs[i] - x;
                double dz = zs[i] - z;
                if (dx * dx + dz * dz <= maxDistSq) {
                    return true;
                }
            }
            return false;
        }
        int cx = Mth.floor(x) >> 7;
        int cz = Mth.floor(z) >> 7;
        long[] keys = playerCellKeys;
        int[] next = nextInBucket;
        int[] heads = bucketHeads;
        for (int dxCell = -1; dxCell <= 1; dxCell++) {
            for (int dzCell = -1; dzCell <= 1; dzCell++) {
                long targetKey = packCell(cx + dxCell, cz + dzCell);
                int idx = heads[bucketIndex(targetKey)];
                while (idx != -1) {
                    if (keys[idx] == targetKey) {
                        double dx = xs[idx] - x;
                        double dz = zs[idx] - z;
                        if (dx * dx + dz * dz <= maxDistSq) {
                            return true;
                        }
                    }
                    idx = next[idx];
                }
            }
        }
        return false;
    }

    public static ServerPlayer findNearestPlayerForSpawn(ServerLevel level, double x, double y, double z) {
        ensureUpdated(level);
        int n = count;
        if (n <= 0) return null;
        double[] xs = px, ys = py, zs = pz;
        ServerPlayer[] refs = playerRefs;
        if (n <= 16) {
            double best = Double.MAX_VALUE;
            ServerPlayer bestPlayer = null;
            for (int i = 0; i < n; i++) {
                double dx = xs[i] - x;
                double dy = ys[i] - y;
                double dz = zs[i] - z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) {
                    best = d2;
                    bestPlayer = refs[i];
                }
            }
            return bestPlayer;
        }
        int cx = Mth.floor(x) >> 7;
        int cz = Mth.floor(z) >> 7;
        long[] keys = playerCellKeys;
        int[] next = nextInBucket;
        int[] heads = bucketHeads;
        double best = Double.MAX_VALUE;
        ServerPlayer bestPlayer = null;
        for (int dxCell = -1; dxCell <= 1; dxCell++) {
            for (int dzCell = -1; dzCell <= 1; dzCell++) {
                long targetKey = packCell(cx + dxCell, cz + dzCell);
                int idx = heads[bucketIndex(targetKey)];
                while (idx != -1) {
                    if (keys[idx] == targetKey) {
                        double dx = xs[idx] - x;
                        double dy = ys[idx] - y;
                        double dz = zs[idx] - z;
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 < best) {
                            best = d2;
                            bestPlayer = refs[idx];
                        }
                    }
                    idx = next[idx];
                }
            }
        }
        return bestPlayer;
    }

    public static void reset() {
        cachedLevel = null;
        cachedTick = Integer.MIN_VALUE;
        count = 0;
        Arrays.fill(bucketHeads, -1);
    }
}
