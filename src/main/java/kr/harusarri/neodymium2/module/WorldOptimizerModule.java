package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;

public final class WorldOptimizerModule implements NeodymiumModule {
    @Override public String id() { return "world-optimizer"; }
    @Override public String displayName() { return "외부 월드 엔진(MSPT 기어, 랜덤틱, 유체, 스컬크, 호퍼, 폭발, 블록엔티티 압축, 셀렉터) 최적화"; }
    @Override public String targetHooks() { return "Level, ServerLevel, EntityLookup, Entity, EntitySelector, PoiCompetitorScan, FlagEvaluator, HopperBlockEntity, SignBlock, BeehiveBlockEntity, BellBlockEntity, TrialSpawner, VaultBlockEntity, ServerExplosion, MicroTimingLoggerManager"; }
    @Override public boolean enabled() { return OutsideWorldOptimizer.enabled(); }
    @Override public void onServerStopped() { OutsideWorldOptimizer.resetStats(); }
    @Override public void resetStats() { OutsideWorldOptimizer.resetStats(); }
    @Override public String status() { return OutsideWorldOptimizer.status(); }
}
