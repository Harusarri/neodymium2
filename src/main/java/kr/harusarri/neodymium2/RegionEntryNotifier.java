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
    private static final Component ACTION_BAR_TEXT = Component.empty()
        .append(Component.literal("해당 구역은 ").withStyle(ChatFormatting.GRAY))
        .append(Component.literal("바닐라 민감도 설정").withStyle(ChatFormatting.GREEN))
        .append(Component.literal("이 활성화되어 있습니다. 바닐라 로직을 위반하는 기술적 버그가 발견된다면 제보 부탁드립니다.").withStyle(ChatFormatting.GRAY));

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
                player.sendSystemMessage(ACTION_BAR_TEXT, true);
            } else if (!nowInside && wasInside) {
                INSIDE_PLAYERS.remove(uuid);
                SECOND_PULSE_TICK.remove(uuid);
            } else if (nowInside) {
                Integer pulse = SECOND_PULSE_TICK.get(uuid);
                if (pulse != null && tick >= pulse) {
                    SECOND_PULSE_TICK.remove(uuid);
                    player.sendSystemMessage(ACTION_BAR_TEXT, true);
                }
            }
        }
    }
}
