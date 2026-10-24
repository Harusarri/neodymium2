package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;

public final class EntityOptimizerModule implements NeodymiumModule {
    @Override public String id() { return "entity-optimizer"; }
    @Override public String displayName() { return "외부 엔티티 AI(DAB)/충돌/시야캐시/길찾기 최적화"; }
    @Override public String targetHooks() { return "Mob, LivingEntity, Sensing, PathNavigation, ItemEntity, ExperienceOrb, ServerEntity"; }
    @Override public boolean enabled() { return OutsideEntityOptimizer.enabled(); }
    @Override public void onServerStopped() { OutsideEntityOptimizer.resetStats(); }
    @Override public void resetStats() { OutsideEntityOptimizer.resetStats(); }
    @Override public String status() { return OutsideEntityOptimizer.status(); }
}
