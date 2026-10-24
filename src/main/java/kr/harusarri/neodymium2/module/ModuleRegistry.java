package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.Neodymium2;
import java.util.*;

/**
 * Central registry for all Neodymium 2 optimization modules.
 * Coordinates initialization, config reload propagation, server shutdown cleanup,
 * telemetry resets, and module inspection commands.
 */
public final class ModuleRegistry {
    private static final List<NeodymiumModule> MODULES = List.of(
        new SpawnerModule(),
        new EntityOptimizerModule(),
        new WorldOptimizerModule(),
        new ProjectileCleanupModule(),
        new VillagerLobotomyModule(),
        new AdvancementOptimizerModule()
    );

    private ModuleRegistry() {}

    public static List<NeodymiumModule> all() {
        return MODULES;
    }

    public static Optional<NeodymiumModule> find(String id) {
        for (NeodymiumModule m : MODULES) {
            if (m.id().equalsIgnoreCase(id)) return Optional.of(m);
        }
        return Optional.empty();
    }

    public static void initializeAll() {
        for (NeodymiumModule m : MODULES) {
            m.onInitialize();
        }
    }

    public static void reloadAll(Neodymium2.Snapshot snapshot) {
        for (NeodymiumModule m : MODULES) {
            m.onReload(snapshot);
        }
    }

    public static void serverStoppedAll() {
        for (NeodymiumModule m : MODULES) {
            m.onServerStopped();
        }
    }

    public static void resetAllStats() {
        for (NeodymiumModule m : MODULES) {
            m.resetStats();
        }
    }

    public static String combinedStatus() {
        StringBuilder sb = new StringBuilder(256);
        for (NeodymiumModule m : MODULES) {
            sb.append(m.status());
        }
        return sb.toString();
    }

    public static String describeModules() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("[Neodymium 2 모듈 아키텍처 (총 ").append(MODULES.size()).append("개 서브시스템)]");
        for (NeodymiumModule m : MODULES) {
            sb.append("\n- [").append(m.id()).append("] ")
              .append(m.displayName()).append(" (").append(m.enabled() ? "ON" : "OFF").append(")")
              .append(" | 타겟: ").append(m.targetHooks());
        }
        return sb.toString();
    }
}
