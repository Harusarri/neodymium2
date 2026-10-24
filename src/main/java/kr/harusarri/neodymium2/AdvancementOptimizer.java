package kr.harusarri.neodymium2;

import net.minecraft.advancements.triggers.DistanceTrigger;
import net.minecraft.advancements.triggers.EnterBlockTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.advancements.triggers.UsingItemTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancement Criterion Trigger Optimization Module:
 * 1. Fake-Player Criterion Elision: Carpet fake players (subclasses of ServerPlayer such as
 *    EntityPlayerMPFake) never need advancement predicate/LootContext evaluation on every tick.
 * 2. Outside Passive Trigger Striding: For real ServerPlayers outside vanillaSensitive regions,
 *    high-frequency continuous triggers (PlayerTrigger/location, UsingItemTrigger, EnterBlockTrigger,
 *    DistanceTrigger) are strided to once every 4 ticks. Inside protected regions, 100% 1-tick
 *    Vanilla evaluation is preserved.
 */
public final class AdvancementOptimizer {
    private static long skippedFakePlayerTriggers;
    private static long throttledOutsideTriggers;
    private static long protectedTriggers;

    private AdvancementOptimizer() {}

    public static boolean enabled() {
        var s = Neodymium2.snapshot();
        return s.settings().enabled() && s.guard().isEmpty() && s.settings().outsideWorldTightening();
    }

    public static boolean shouldSkipTrigger(SimpleCriterionTrigger<?> trigger, ServerPlayer player) {
        if (!enabled() || player == null) return false;
        // Carpet fake players (EntityPlayerMPFake) subclass ServerPlayer; real players are exact ServerPlayer instances.
        if (player.getClass() != ServerPlayer.class) {
            skippedFakePlayerTriggers++;
            return true;
        }
        if (trigger instanceof PlayerTrigger
            || trigger instanceof UsingItemTrigger
            || trigger instanceof EnterBlockTrigger
            || trigger instanceof DistanceTrigger) {
            if (Neodymium2.regionProtects(player)) {
                protectedTriggers++;
                return false;
            }
            if (((player.getId() + player.tickCount) & 3) != 0) {
                throttledOutsideTriggers++;
                return true;
            }
        }
        return false;
    }

    public static long skippedFakePlayerTriggers() { return skippedFakePlayerTriggers; }
    public static long throttledOutsideTriggers() { return throttledOutsideTriggers; }
    public static long protectedTriggers() { return protectedTriggers; }

    public static void resetStats() {
        skippedFakePlayerTriggers = 0;
        throttledOutsideTriggers = 0;
        protectedTriggers = 0;
    }

    public static String status() {
        return " · 발전과제 최적화 " + (enabled() ? "ON" : "OFF")
            + "(봇차단/외부감속/보호 " + skippedFakePlayerTriggers + "/" + throttledOutsideTriggers + "/" + protectedTriggers + ")";
    }
}
