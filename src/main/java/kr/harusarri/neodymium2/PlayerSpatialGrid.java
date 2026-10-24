package kr.harusarri.neodymium2;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * Per-Tick Primitive Spatial Cache for Player Positions (Teschner et al., 2003).
 * Eliminates O(P * (M + C)) ServerPlayer list iteration and pointer-chasing across
 * chunk random-tick throttling, natural spawner range checks, and entity DAB AI scaling.
 */
public final class PlayerSpatialGrid {
    private static ServerLevel cachedLevel;
    private static int cachedTick = Integer.MIN_VALUE;
    private static int count;
    private static double[] px = new double[64];
    private static double[] py = new double[64];
    private static double[] pz = new double[64];
    private static int[] monsterCounts = new int[64];
    private static int[] tickSpawnedMonsters = new int[64];
    private static ItemStack[] mainHand = new ItemStack[64];
    private static ItemStack[] offHand = new ItemStack[64];

    private PlayerSpatialGrid() {}

    private static void ensureUpdated(ServerLevel level) {
        var server = level.getServer();
        int tick = server != null ? server.getTickCount() : -1;
        if (level == cachedLevel && tick == cachedTick && tick >= 0) {
            return;
        }
        List<ServerPlayer> players = level.players();
        int size = players.size();
        if (px.length < size) {
            int cap = Math.max(size, px.length * 2);
            px = new double[cap];
            py = new double[cap];
            pz = new double[cap];
            monsterCounts = new int[cap];
            tickSpawnedMonsters = new int[cap];
            mainHand = new ItemStack[cap];
            offHand = new ItemStack[cap];
        }
        int n = 0;
        for (int i = 0; i < size; i++) {
            ServerPlayer p = players.get(i);
            if (!p.isSpectator() && p.isAlive()) {
                px[n] = p.getX();
                py[n] = p.getY();
                pz[n] = p.getZ();
                monsterCounts[n] = 0;
                tickSpawnedMonsters[n] = 0;
                mainHand[n] = p.getMainHandItem();
                offHand[n] = p.getOffhandItem();
                n++;
            }
        }
        count = n;
        cachedLevel = level;
        cachedTick = tick;
    }

    public static void recordMonsterNearNearestPlayer(ServerLevel level, double x, double y, double z) {
        ensureUpdated(level);
        int n = count;
        if (n == 0) return;
        double[] xs = px, ys = py, zs = pz;
        double best = Double.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - x;
            double dy = ys[i] - y;
            double dz = zs[i] - z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < best) {
                best = d2;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0 && best <= 128.0 * 128.0) {
            monsterCounts[bestIdx]++;
        }
    }

    public static void recordOutsideMonsterSpawned(ServerLevel level, double x, double z) {
        ensureUpdated(level);
        int n = count;
        if (n == 0) return;
        double[] xs = px, zs = pz;
        double best = Double.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - x;
            double dz = zs[i] - z;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            monsterCounts[bestIdx]++;
            tickSpawnedMonsters[bestIdx]++;
        }
    }

    public static boolean canSpawnOutsideMonsterNear(ServerLevel level, double x, double z, int maxLiveMonsters, int maxPerTickMonsters) {
        ensureUpdated(level);
        int n = count;
        if (n == 0) return false;
        double[] xs = px, zs = pz;
        double best = Double.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - x;
            double dz = zs[i] - z;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                bestIdx = i;
            }
        }
        return bestIdx >= 0
            && monsterCounts[bestIdx] < maxLiveMonsters
            && tickSpawnedMonsters[bestIdx] < maxPerTickMonsters;
    }

    public static boolean hasFoodHoldingPlayerWithin(ServerLevel level, Animal animal, double maxDistSq) {
        ensureUpdated(level);
        int n = count;
        double x = animal.getX(), y = animal.getY(), z = animal.getZ();
        double[] xs = px, ys = py, zs = pz;
        ItemStack[] mh = mainHand, oh = offHand;
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

    public static boolean hasPlayerWithinHorizontal(ServerLevel level, double x, double z, double maxDistSq) {
        ensureUpdated(level);
        int n = count;
        double[] xs = px, zs = pz;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - x;
            double dz = zs[i] - z;
            if (dx * dx + dz * dz <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    public static double horizDistSqToNearestPlayer(ServerLevel level, double x, double z) {
        ensureUpdated(level);
        int n = count;
        double[] xs = px, zs = pz;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - x;
            double dz = zs[i] - z;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
            }
        }
        return best;
    }

    public static int nearestPlayerBlockY(ServerLevel level, double x, double z) {
        ensureUpdated(level);
        int n = count;
        if (n == 0) return Integer.MIN_VALUE;
        double[] xs = px, ys = py, zs = pz;
        double best = Double.MAX_VALUE;
        double bestY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - x;
            double dz = zs[i] - z;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                bestY = ys[i];
            }
        }
        return net.minecraft.util.Mth.floor(bestY);
    }

    public static double distSqToNearestPlayer(ServerLevel level, double x, double y, double z) {
        ensureUpdated(level);
        int n = count;
        double[] xs = px, ys = py, zs = pz;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - x;
            double dy = ys[i] - y;
            double dz = zs[i] - z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < best) {
                best = d2;
            }
        }
        return best;
    }

    public static void reset() {
        cachedLevel = null;
        cachedTick = Integer.MIN_VALUE;
        count = 0;
    }
}
