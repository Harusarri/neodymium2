package de.z0rdak.yawp.data.region;

import de.z0rdak.yawp.core.region.IMarkableRegion;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Collection;
import java.util.Collections;

public class RegionDataManager {
    public static Collection<IMarkableRegion> getLocalsFor(ResourceKey<Level> dim) {
        return Collections.emptyList();
    }
}
