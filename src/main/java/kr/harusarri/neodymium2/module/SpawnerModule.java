package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.RegionSpawner;

public final class SpawnerModule implements NeodymiumModule {
    @Override public String id() { return "spawner"; }
    @Override public String displayName() { return "구역 자연 스폰 & MobCap/SpawnCost 고속화"; }
    @Override public String targetHooks() { return "NaturalSpawner(createState, spawnForChunk, isRightDistanceToPlayerAndSpawnPoint, mobsAt), LocalMobCapCalculator(getPlayersNear), EntityLookup(getAllEntities)"; }
    @Override public boolean enabled() { return RegionSpawner.enabled(); }
    @Override public void onReload(Neodymium2.Snapshot snapshot) { RegionSpawner.invalidateBiomeCostCache(); }
    @Override public void onServerStopped() { RegionSpawner.resetStats(); }
    @Override public void resetStats() { RegionSpawner.resetStats(); }
    @Override public String status() { return RegionSpawner.status(); }
}
