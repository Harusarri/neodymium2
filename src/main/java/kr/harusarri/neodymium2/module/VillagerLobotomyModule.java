package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.VillagerLobotomy;

public final class VillagerLobotomyModule implements NeodymiumModule {
    @Override public String id() { return "villager-lobotomy"; }
    @Override public String displayName() { return "주민 AI 휴면(Lobotomy) & POI 리비전 그리드"; }
    @Override public String targetHooks() { return "Villager(customServerAiStep, startTrading), Brain, PoiManager, MerchantMenu, GiveGiftToHero"; }
    @Override public boolean enabled() { return VillagerLobotomy.enabled(); }
    @Override public String status() { return VillagerLobotomy.status(); }
}
