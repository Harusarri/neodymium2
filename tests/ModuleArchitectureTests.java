import kr.harusarri.neodymium2.*;
import kr.harusarri.neodymium2.module.*;
import java.util.*;

public class ModuleArchitectureTests {
    static int checks;
    static void check(boolean v, String msg) {
        if (!v) throw new AssertionError(msg);
        checks++;
    }

    public static void main(String[] args) {
        List<NeodymiumModule> modules = ModuleRegistry.all();
        check(modules.size() == 6, "Expected 6 registered modules");
        List<String> expectedIds = List.of(
            "spawner",
            "entity-optimizer",
            "world-optimizer",
            "projectile-cleanup",
            "villager-lobotomy",
            "advancement-optimizer"
        );
        for (String id : expectedIds) {
            var m = ModuleRegistry.find(id);
            check(m.isPresent(), "Module present: " + id);
            check(!m.get().displayName().isBlank(), "DisplayName non-blank: " + id);
            check(!m.get().targetHooks().isBlank(), "TargetHooks non-blank: " + id);
            check(!m.get().status().isBlank(), "Status non-blank: " + id);
        }
        ModuleRegistry.resetAllStats();
        check(RegionSpawner.elidedSpawnCostLookups() == 0 && RegionSpawner.cachedMobCapLookups() == 0 && RegionSpawner.mobOnlyCreateStateScans() == 0, "Spawner stats reset");
        check(OutsideEntityOptimizer.skippedStaticTrackerUpdates() == 0, "Entity optimizer static tracker stats reset");
        check(OutsideWorldOptimizer.fastTypeLookups() == 0 && OutsideWorldOptimizer.cachedBlockTickChunks() == 0 && OutsideWorldOptimizer.skippedIdleBlockEntityTicks() == 0, "World optimizer new stats reset");
        check(OutsideWorldOptimizer.fastTagLookups() == 0 && OutsideWorldOptimizer.fastSelectorNbtChecks() == 0 && OutsideWorldOptimizer.fastPoiCompetitorScans() == 0 && OutsideWorldOptimizer.cachedYawpFlagEvals() == 0 && OutsideWorldOptimizer.compactedSleepingBlockEntities() == 0, "World optimizer rc6 stats reset");
        OutsideWorldOptimizer.recordFastTypeLookup();
        OutsideWorldOptimizer.recordCachedBlockTickChunk();
        OutsideWorldOptimizer.recordFastTagLookup();
        OutsideWorldOptimizer.recordFastSelectorNbtCheck();
        OutsideWorldOptimizer.recordFastPoiCompetitorScan();
        OutsideWorldOptimizer.recordCachedYawpFlagEval();
        OutsideWorldOptimizer.recordCompactedSleepingBlockEntities(5);
        RegionSpawner.recordMobOnlyCreateStateScan();
        check(OutsideWorldOptimizer.fastTypeLookups() == 1 && OutsideWorldOptimizer.cachedBlockTickChunks() == 1 && RegionSpawner.mobOnlyCreateStateScans() == 1, "New telemetry counters increment");
        check(OutsideWorldOptimizer.fastTagLookups() == 1 && OutsideWorldOptimizer.fastSelectorNbtChecks() == 1 && OutsideWorldOptimizer.fastPoiCompetitorScans() == 1 && OutsideWorldOptimizer.cachedYawpFlagEvals() == 1 && OutsideWorldOptimizer.compactedSleepingBlockEntities() == 5, "rc6 telemetry counters increment");

        var fastTags = new kr.harusarri.neodymium2.core.FastEntityTagSet(Set.of());
        check(fastTags.isEmpty() && !fastTags.contains("exclude_ear") && fastTags.hotFlags() == 0, "FastEntityTagSet empty fast-path");
        check(fastTags.add("exclude_ear") && fastTags.contains("exclude_ear") && fastTags.hasHotFlag(kr.harusarri.neodymium2.core.FastEntityTagSet.FLAG_EXCLUDE_EAR), "FastEntityTagSet hot flag set");
        check(fastTags.remove("exclude_ear") && !fastTags.contains("exclude_ear") && !fastTags.hasHotFlag(kr.harusarri.neodymium2.core.FastEntityTagSet.FLAG_EXCLUDE_EAR), "FastEntityTagSet hot flag cleared");

        ModuleRegistry.resetAllStats();
        check(AdvancementOptimizer.skippedFakePlayerTriggers() == 0 && AdvancementOptimizer.throttledOutsideTriggers() == 0, "Advancement stats reset");
        String desc = ModuleRegistry.describeModules();
        for (String id : expectedIds) {
            check(desc.contains("[" + id + "]"), "describeModules contains " + id);
        }
        check(desc.contains("EntityLookup") && desc.contains("ServerEntity") && desc.contains("SignBlock") && desc.contains("PoiCompetitorScan") && desc.contains("EntitySelector"), "describeModules contains new hooks");
        String combined = ModuleRegistry.combinedStatus();
        check(combined.contains("구역 스폰") && combined.contains("발전과제 최적화") && combined.contains("타입인덱스/청크틱캐시/유휴블록엔티티생략") && combined.contains("태그·NBT셀렉터/POI경쟁/YAWP캐시"), "combinedStatus includes all modules and new telemetry");
        System.out.println("NEO_MODULE_ARCH_PASS checks=" + checks);
    }
}
