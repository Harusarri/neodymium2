package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.ProjectileCleanup;

public final class ProjectileCleanupModule implements NeodymiumModule {
    @Override public String id() { return "projectile-cleanup"; }
    @Override public String displayName() { return "레이지 청크 투사체 경계 정리"; }
    @Override public String targetHooks() { return "Entity(move), ServerLevel(tick)"; }
    @Override public boolean enabled() { return ProjectileCleanup.enabled(); }
    @Override public void onInitialize() { ProjectileCleanup.initialize(); }
    @Override public String status() { return ProjectileCleanup.status(); }
}
