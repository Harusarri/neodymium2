package kr.harusarri.neodymium2.module;

import kr.harusarri.neodymium2.Neodymium2;

/**
 * Modular subsystem contract for Neodymium 2.
 * Isolates each optimization domain (Spawner, Entity AI/Physics, World Engine,
 * Projectile Cleanup, Villager Lobotomy, Advancement Criteria) behind a uniform
 * lifecycle and telemetry interface to simplify Minecraft version upgrades.
 */
public interface NeodymiumModule {
    /** Unique machine-readable module identifier (e.g., "spawner", "advancement-optimizer"). */
    String id();

    /** Human-readable Korean/English display name for status & diagnostics. */
    String displayName();

    /** Summary of the target Minecraft classes/hooks isolated by this module for version updates. */
    String targetHooks();

    /** Returns true when this module is currently active under the loaded Snapshot. */
    boolean enabled();

    /** Called once during ModInitializer.onInitialize(). */
    default void onInitialize() {}

    /** Called whenever Neodymium 2 reloads config.json / regions.json or datapacks. */
    default void onReload(Neodymium2.Snapshot snapshot) {}

    /** Called when the MinecraftServer stops. */
    default void onServerStopped() {}

    /** Resets module telemetry counters and transient caches. */
    default void resetStats() {}

    /** Concise status fragment appended to `/neodymium2 status`. */
    String status();
}
