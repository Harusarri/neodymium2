package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.AdvancementOptimizer;

public final class AdvancementOptimizerModule implements NeodymiumModule {
    @Override public String id() { return "advancement-optimizer"; }
    @Override public String displayName() { return "발전과제 트리거 최적화(FakePlayer 차단 & 외부 패시브 트리거 감속)"; }
    @Override public String targetHooks() { return "SimpleCriterionTrigger(trigger)"; }
    @Override public boolean enabled() { return AdvancementOptimizer.enabled(); }
    @Override public void onServerStopped() { AdvancementOptimizer.resetStats(); }
    @Override public void resetStats() { AdvancementOptimizer.resetStats(); }
    @Override public String status() { return AdvancementOptimizer.status(); }
}
