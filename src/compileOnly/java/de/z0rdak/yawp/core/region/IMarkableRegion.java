package de.z0rdak.yawp.core.region;

import net.minecraft.core.BlockPos;

public interface IMarkableRegion extends IProtectedRegion {
    boolean contains(BlockPos pos);
    int getPriority();
}
