package kr.harusarri.neodymium2.api;

import kr.harusarri.neodymium2.core.SpatialIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * SPI implemented by external region managers (such as YAWPManager) to supply custom named regions,
 * spatial bounds, position-to-region lookups, and optional GUI management callbacks to Neodymium 2.
 */
public interface NeodymiumRegionProvider {
    record ResolvedRegion(
        String dimension,
        String regionId,
        String displayName,
        SpatialIndex.Box bounds,
        int centerX,
        int centerY,
        int centerZ,
        String boundsDescription
    ) {}

    record RegionAtPos(
        String regionId,
        String displayName
    ) {}

    /**
     * Resolves an external region by dimension key (e.g. "minecraft:overworld") and region ID.
     * Returns null if the region does not exist in this provider.
     */
    ResolvedRegion resolve(String dimension, String regionId);

    /**
     * Optional lookup of an external region containing the given position (used for /neodymium2 nametags display).
     * Returns null if no external region covers this position.
     */
    default RegionAtPos findRegionAt(ServerLevel level, BlockPos pos) {
        return null;
    }

    /**
     * Returns true if this provider supports opening an in-game management GUI for its regions.
     */
    default boolean canOpenManageGui() {
        return false;
    }

    /**
     * Opens the provider's management GUI for the specified region on behalf of the player.
     * Returns true if the GUI was opened successfully.
     */
    default boolean openManageGui(ServerPlayer player, String dimension, String regionId) {
        return false;
    }
}
