package kr.harusarri.neodymium2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Displays an Action Bar notification when a player enters a vanillaSensitive: true protected region.
 * Controlled by "regionEntryActionBar" (true/false) in config.json and updated on /neodymium2 reload.
 */
public final class RegionEntryNotifier {
    private static final Component ACTION_BAR_EN = buildActionBar(NeodymiumI18n.EN_US);
    private static final Component ACTION_BAR_KO = buildActionBar(NeodymiumI18n.KO_KR);

    private static Component buildActionBar(String lang) {
        return Component.empty()
            .append(Component.literal(NeodymiumI18n.getByLang(lang, "actionbar.prefix")).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(NeodymiumI18n.getByLang(lang, "actionbar.highlight")).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(NeodymiumI18n.getByLang(lang, "actionbar.suffix")).withStyle(ChatFormatting.GRAY));
    }

    private static Component actionBarFor(ServerPlayer player) {
        return NeodymiumI18n.KO_KR.equals(NeodymiumI18n.resolveLang(player)) ? ACTION_BAR_KO : ACTION_BAR_EN;
    }

    private static volatile boolean enabled = true;
    private static final Set<UUID> INSIDE_PLAYERS = new HashSet<>();
    private static final Map<UUID, Integer> SECOND_PULSE_TICK = new HashMap<>();

    private RegionEntryNotifier() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            INSIDE_PLAYERS.clear();
            SECOND_PULSE_TICK.clear();
        }
    }

    public static void reset() {
        INSIDE_PLAYERS.clear();
        SECOND_PULSE_TICK.clear();
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        int tick = server.getTickCount();
        if ((tick % 10) != 0) return;
        if (!enabled) {
            if (!INSIDE_PLAYERS.isEmpty()) {
                INSIDE_PLAYERS.clear();
                SECOND_PULSE_TICK.clear();
            }
            return;
        }
        var snap = Neodymium2.snapshot();
        if (!snap.settings().enabled() || !snap.guard().isEmpty() || snap.index().size() == 0) {
            INSIDE_PLAYERS.clear();
            SECOND_PULSE_TICK.clear();
            return;
        }
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            INSIDE_PLAYERS.clear();
            SECOND_PULSE_TICK.clear();
            return;
        }
        for (int i = 0, n = players.size(); i < n; i++) {
            ServerPlayer player = players.get(i);
            if (player == null || player.isRemoved()) continue;
            UUID uuid = player.getUUID();
            boolean nowInside = Neodymium2.regionProtects(player);
            boolean wasInside = INSIDE_PLAYERS.contains(uuid);
            if (nowInside && !wasInside) {
                INSIDE_PLAYERS.add(uuid);
                SECOND_PULSE_TICK.put(uuid, tick + 40);
                player.sendSystemMessage(actionBarFor(player), true);
            } else if (!nowInside && wasInside) {
                INSIDE_PLAYERS.remove(uuid);
                SECOND_PULSE_TICK.remove(uuid);
            } else if (nowInside) {
                Integer pulse = SECOND_PULSE_TICK.get(uuid);
                if (pulse != null && tick >= pulse) {
                    SECOND_PULSE_TICK.remove(uuid);
                    player.sendSystemMessage(actionBarFor(player), true);
                }
            }
        }
    }
}
