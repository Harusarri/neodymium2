package kr.harusarri.neodymium2.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface DormantTickingWrapper {
    void neo$bindLevel(Level level);
    BlockPos neo$getLastKnownPos();
    boolean neo$isUnlinked();
    void neo$setUnlinked(boolean unlinked);
}
