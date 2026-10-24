package kr.harusarri.neodymium2.core;

import net.minecraft.world.level.block.entity.TickingBlockEntity;

public interface LevelDormantTickersAccess {
    void neo$markHasSleepingToCompact();
    void neo$wakeSleepingTicker(TickingBlockEntity wrapper);
    void neo$unparkSleepingTicker(TickingBlockEntity wrapper);
    int neo$parkedSleepingCount();
}
