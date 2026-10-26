package kr.harusarri.neodymium2.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public interface EntityFastStateAccess {
    long neo$getProtGen();
    AABB neo$getProtBb();
    Level neo$getProtLevel();
    boolean neo$getProtResult();
    void neo$setProtCache(long gen, AABB bb, Level level, boolean result);

    int neo$getPlayerDistTick();
    double neo$getPlayerDistSq();
    void neo$setPlayerDistCache(int tick, double distSq);

    long neo$getYawpMobGriefGen();
    long neo$getYawpMobGriefPos();
    int neo$getYawpMobGriefUntilTick();
    boolean neo$isYawpMobGriefCancelled();
    void neo$setYawpMobGriefCache(long gen, long posLong, int untilTick, boolean cancelled);

    long neo$getSelectorNbtTick();
    ItemStack neo$getSelectorNbtSelectedItem();
    CompoundTag neo$getSelectorNbtTag();
    void neo$setSelectorNbtCache(long tick, ItemStack selectedItem, CompoundTag tag);

    int neo$getInteractAwakeUntilTick();
    void neo$setInteractAwakeUntilTick(int untilTick);
}
