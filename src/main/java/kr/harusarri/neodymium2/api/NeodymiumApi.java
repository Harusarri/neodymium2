package kr.harusarri.neodymium2.api;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.RegionPolicies;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Public API for integrating external region/protection managers (such as YAWPManager) with Neodymium 2,
 * or controlling protected regions and chunks programmatically.
 */
public final class NeodymiumApi {
    private static volatile NeodymiumRegionProvider provider;

    private NeodymiumApi() {}

    /**
     * Registers an external region provider (e.g., YAWPManager) and refreshes the spatial index
     * if the server is already running.
     */
    public static void registerRegionProvider(NeodymiumRegionProvider regionProvider) {
        provider = regionProvider;
        Neodymium2.onProviderRegistered();
    }

    public static NeodymiumRegionProvider getRegionProvider() {
        return provider;
    }

    /**
     * Returns whether Neodymium 2 global optimization is enabled in config.json.
     */
    public static boolean isEnabled() {
        return Neodymium2.snapshot().settings().enabled();
    }

    /**
     * Returns whether a region (either standalone chunk ID or external provider region ID)
     * has vanilla sensitivity (protection) enabled.
     */
    public static boolean isVanillaSensitive(String dimension, String regionId) {
        return Neodymium2.sensitive(new RegionPolicies.Ref(dimension, regionId));
    }

    /**
     * Enables or disables vanilla sensitivity (protection) for a region and persists regions.json atomically.
     * Must be called on the server thread.
     */
    public static void setVanillaSensitive(String dimension, String regionId, boolean enabled) throws Exception {
        Neodymium2.setSensitive(new RegionPolicies.Ref(dimension, regionId), enabled);
    }

    /**
     * Notifies Neodymium 2 that one or more external regions changed bounds or level state was reset,
     * scheduling a spatial index rebuild at the start of the next server tick.
     */
    public static void onRegionMutated() {
        Neodymium2.invalidate();
    }

    /**
     * Notifies Neodymium 2 that an external region was renamed so its policy in regions.json is migrated.
     */
    public static void onRegionRenamed(String dimension, String oldRegionId, String newRegionId) {
        if (oldRegionId != null && newRegionId != null && !oldRegionId.equals(newRegionId)) {
            Neodymium2.renamed(
                new RegionPolicies.Ref(dimension, oldRegionId),
                new RegionPolicies.Ref(dimension, newRegionId)
            );
        }
    }

    /**
     * Notifies Neodymium 2 that an external region was deleted so its policy in regions.json is cleaned up.
     */
    public static void onRegionRemoved(String dimension, String regionId) {
        Neodymium2.removed(new RegionPolicies.Ref(dimension, regionId));
    }

    /**
     * Queries whether the given entity is currently inside a vanilla-sensitive protected region.
     */
    public static boolean isProtected(Entity entity) {
        return Neodymium2.regionProtects(entity);
    }

    /**
     * Queries whether the given block position is inside a vanilla-sensitive protected region.
     */
    public static boolean isProtectedBlock(Level level, BlockPos pos) {
        return Neodymium2.regionProtectsBlock(level, pos);
    }

    /**
     * Queries whether the given chunk intersects any vanilla-sensitive protected region.
     */
    public static boolean isProtectedChunk(Level level, int chunkX, int chunkZ) {
        return Neodymium2.regionProtectsChunk(level, chunkX, chunkZ);
    }
}
