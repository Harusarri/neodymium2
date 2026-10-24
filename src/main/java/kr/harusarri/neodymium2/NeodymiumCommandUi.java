package kr.harusarri.neodymium2;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import kr.harusarri.neodymium2.api.NeodymiumApi;
import kr.harusarri.neodymium2.api.NeodymiumRegionProvider;
import kr.harusarri.neodymium2.module.ModuleRegistry;
import kr.harusarri.neodymium2.module.NeodymiumModule;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.text.NumberFormat;
import java.util.*;

/**
 * Modern, aligned, minimal chat UI and paginated Text GUI for /neodymium2 admin commands,
 * including standalone chunk protection commands and external RegionProvider integration.
 */
public final class NeodymiumCommandUi {
    private static final int PAGE_SIZE = 6;
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

    private NeodymiumCommandUi() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("neodymium2")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
            .executes(ctx -> sendStatus(ctx.getSource()))
            .then(Commands.literal("status")
                .executes(ctx -> sendStatus(ctx.getSource())))
            .then(Commands.literal("modules")
                .executes(ctx -> sendModules(ctx.getSource())))
            .then(Commands.literal("regions")
                .executes(ctx -> sendRegionsPage(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> sendRegionsPage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
            .then(Commands.literal("chunk")
                .executes(ctx -> sendRegionsPage(ctx.getSource(), 1))
                .then(Commands.literal("list")
                    .executes(ctx -> sendRegionsPage(ctx.getSource(), 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> sendRegionsPage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("toggle")
                    .executes(ctx -> toggleCurrentChunk(ctx.getSource())))
                .then(Commands.literal("add")
                    .executes(ctx -> addCurrentChunk(ctx.getSource(), 0))
                    .then(Commands.argument("arg1", IntegerArgumentType.integer(-1875000, 1875000))
                        .executes(ctx -> addCurrentChunk(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "arg1")))
                        .then(Commands.argument("arg2", IntegerArgumentType.integer(-1875000, 1875000))
                            .executes(ctx -> addChunkCoords(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "arg1"),
                                IntegerArgumentType.getInteger(ctx, "arg2")
                            ))
                            .then(Commands.argument("toChunkX", IntegerArgumentType.integer(-1875000, 1875000))
                                .then(Commands.argument("toChunkZ", IntegerArgumentType.integer(-1875000, 1875000))
                                    .executes(ctx -> addChunkRangeCoords(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "arg1"),
                                        IntegerArgumentType.getInteger(ctx, "arg2"),
                                        IntegerArgumentType.getInteger(ctx, "toChunkX"),
                                        IntegerArgumentType.getInteger(ctx, "toChunkZ")
                                    )))))))
                .then(Commands.literal("remove")
                    .executes(ctx -> removeCurrentChunk(ctx.getSource()))
                    .then(Commands.argument("chunkX", IntegerArgumentType.integer(-1875000, 1875000))
                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer(-1875000, 1875000))
                            .executes(ctx -> removeChunkCoords(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "chunkX"),
                                IntegerArgumentType.getInteger(ctx, "chunkZ")
                            )))))
                .then(Commands.literal("removeid")
                    .then(Commands.argument("dimension", StringArgumentType.string())
                        .then(Commands.argument("region", StringArgumentType.greedyString())
                            .executes(ctx -> removeChunkById(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "dimension"),
                                StringArgumentType.getString(ctx, "region")
                            ))))))
            .then(Commands.literal("manage")
                .then(Commands.argument("dimension", StringArgumentType.string())
                    .then(Commands.argument("region", StringArgumentType.greedyString())
                        .executes(ctx -> openRegionManageGui(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "dimension"),
                            StringArgumentType.getString(ctx, "region")
                        )))))
            .then(Commands.literal("nametags")
                .executes(ctx -> sendNametagsPage(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> sendNametagsPage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
            .then(Commands.literal("reload")
                .executes(ctx -> executeReload(ctx.getSource()))));
    }

    private static MutableComponent text(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(color);
    }

    private static MutableComponent button(String label, ChatFormatting color, String command, String hover) {
        return Component.literal(label).withStyle(style -> style
            .withColor(color)
            .withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static MutableComponent navBar(String activeTab) {
        MutableComponent bar = Component.empty();
        bar.append(button("[상태]", activeTab.equals("status") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 status", "/neodymium2 status"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button("[모듈]", activeTab.equals("modules") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 modules", "/neodymium2 modules"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button("[구역]", activeTab.equals("regions") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 regions 1", "/neodymium2 regions 1"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button("[이름표]", activeTab.equals("nametags") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 nametags 1", "/neodymium2 nametags 1"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button("[리로드]", ChatFormatting.GRAY,
            "/neodymium2 reload", "/neodymium2 reload"));
        return bar;
    }

    public static int sendStatus(CommandSourceStack source) {
        var s = Neodymium2.snapshot();
        MinecraftServer server = source.getServer();
        double mspt = server != null ? server.getAverageTickTimeNanos() / 1_000_000.0 : 0.0;
        double tps = mspt > 0.0 ? Math.min(20.0, 1000.0 / mspt) : 20.0;
        int gear = OutsideWorldOptimizer.msptGear(server);

        MutableComponent out = Component.empty();
        out.append(text("Neodymium 2  ", ChatFormatting.GOLD))
           .append(text(s.settings().enabled() ? "ON" : "OFF", s.settings().enabled() ? ChatFormatting.GREEN : ChatFormatting.RED))
           .append(text("  ·  ", ChatFormatting.DARK_GRAY))
           .append(text(String.format(Locale.US, "%.1f ms (%.1f TPS, G%d)", mspt, tps, gear), ChatFormatting.WHITE))
           .append(text("  ·  보호구역 ", ChatFormatting.GRAY))
           .append(text(s.regions().size() + "개\n", ChatFormatting.WHITE));

        if (!s.guard().isEmpty()) {
            out.append(text("  보호 대기      ", ChatFormatting.RED))
               .append(text(s.guard() + "\n", ChatFormatting.WHITE));
        }

        out.append(text("  스폰 최적화    ", ChatFormatting.GRAY))
           .append(text("실행 " + compact(RegionSpawner.outsideEvaluatedChunks())
               + "  ·  생략 " + compact(RegionSpawner.outsideSkippedChunks())
               + "  ·  캐시 " + compact(RegionSpawner.elidedSpawnCostLookups() + OutsideSpawnAndDensity.precookedReuses() + OutsideSpawnAndDensity.fastCollisionHits()) + "\n", ChatFormatting.WHITE));

        out.append(text("  엔티티 최적화  ", ChatFormatting.GRAY))
           .append(text("AI생략 " + compact(OutsideEntityOptimizer.skippedAiTicks())
               + "  ·  충돌생략 " + compact(OutsideEntityOptimizer.skippedPushTicks())
               + "  ·  과밀집감속 " + compact(OutsideSpawnAndDensity.overcrowdedThrottles()) + "\n", ChatFormatting.WHITE));

        out.append(text("  주민 / 이름표  ", ChatFormatting.GRAY))
           .append(text("주민AI " + (VillagerLobotomy.enabled() ? "ON" : "OFF")
               + "  ·  우회차단 " + compact(OutsideSpawnAndDensity.blockedNametagBypasses())
               + "  ·  진입안내 " + (RegionEntryNotifier.enabled() ? "ON" : "OFF") + "\n", ChatFormatting.WHITE));

        out.append(text("  월드 최적화    ", ChatFormatting.GRAY))
           .append(text("랜덤틱 " + compact(OutsideWorldOptimizer.throttledRandomTickChunks())
               + "  ·  유체 " + compact(OutsideWorldOptimizer.skippedFluidScans())
               + "  ·  호퍼 " + compact(OutsideWorldOptimizer.throttledHopperPolls()) + "\n", ChatFormatting.WHITE));

        out.append(navBar("status"));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    public static int sendModules(CommandSourceStack source) {
        List<NeodymiumModule> modules = ModuleRegistry.all();
        MutableComponent out = Component.empty();
        out.append(text("Neodymium 2 모듈 (" + (modules.size() + 1) + "개)\n", ChatFormatting.GOLD));

        for (NeodymiumModule m : modules) {
            boolean on = m.enabled();
            MutableComponent line = text("  ", ChatFormatting.GRAY)
                .append(text(on ? "ON   " : "OFF  ", on ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(text(m.displayName(), ChatFormatting.WHITE))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                    Component.literal("ID: " + m.id() + "\n타겟: " + m.targetHooks())
                )));
            out.append(line).append(text("\n", ChatFormatting.GRAY));
        }
        out.append(text("  ", ChatFormatting.GRAY))
           .append(text("ON   ", ChatFormatting.GREEN))
           .append(text("구역 외 LagFree 스폰 · 과밀집 감속 · 이름표 차단\n", ChatFormatting.WHITE));

        out.append(navBar("modules"));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    public static int executeReload(CommandSourceStack source) {
        boolean ok = Neodymium2.reload();
        if (ok) {
            source.sendSuccess(() -> text("Neodymium 2 설정을 다시 불러왔습니다.", ChatFormatting.GREEN), false);
            return sendStatus(source);
        } else {
            source.sendFailure(text("설정 오류: 기존 설정을 유지했습니다. 서버 로그를 확인하세요.", ChatFormatting.RED));
            return 0;
        }
    }

    private record RegionRow(
        String dimension,
        String regionId,
        String displayName,
        int cx,
        int cy,
        int cz,
        String boundsDesc,
        boolean standaloneChunk
    ) {}

    public static int sendRegionsPage(CommandSourceStack source, int requestedPage) {
        var snap = Neodymium2.snapshot();
        List<RegionPolicies.Ref> refs = new ArrayList<>(snap.regions());
        refs.sort(Comparator.comparing(RegionPolicies.Ref::dimension).thenComparing(RegionPolicies.Ref::region));

        NeodymiumRegionProvider provider = NeodymiumApi.getRegionProvider();
        boolean canManageExternal = provider != null && provider.canOpenManageGui();

        List<RegionRow> rows = new ArrayList<>(refs.size());
        for (RegionPolicies.Ref ref : refs) {
            NeodymiumRegionProvider.ResolvedRegion resolved = ref.resolve();
            String disp = cleanRegionName(ref.region());
            int cx = 0, cy = 80, cz = 0;
            String boundsDesc = "좌표 정보 없음";
            if (resolved != null) {
                if (resolved.displayName() != null && !resolved.displayName().isBlank()) {
                    disp = ref.isStandaloneChunk() ? resolved.displayName() : cleanRegionName(resolved.displayName());
                }
                cx = resolved.centerX();
                cy = resolved.centerY();
                cz = resolved.centerZ();
                if (resolved.boundsDescription() != null && !resolved.boundsDescription().isBlank()) {
                    boundsDesc = resolved.boundsDescription();
                }
            }
            rows.add(new RegionRow(ref.dimension(), ref.region(), disp, cx, cy, cz, boundsDesc, ref.isStandaloneChunk()));
        }

        int total = rows.size();
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 1, totalPages);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(total, start + PAGE_SIZE);

        MutableComponent out = Component.empty();
        out.append(text(String.format(Locale.US, "바닐라 보호 구역  %d / %d 페이지  (총 %d개)  ", page, totalPages, total), ChatFormatting.GOLD));
        if (source.getPlayer() != null) {
            out.append(button("[+현위치 청크 토글]", ChatFormatting.GREEN,
                "/neodymium2 chunk toggle",
                "현재 서 있는 청크의 바닐라 민감도 보호를 켜거나 끕니다.\n명령어: /neodymium2 chunk add [반경|x z]"));
        }
        out.append(text("\n", ChatFormatting.GRAY));

        if (total == 0) {
            out.append(text("  지정된 바닐라 보호 구역이 없습니다. (/neodymium2 chunk add 로 청크 보호 가능)\n", ChatFormatting.GRAY));
        } else {
            for (int i = start; i < end; i++) {
                RegionRow row = rows.get(i);
                String dimShort = formatDim(row.dimension());
                String tpCmd = "/execute in " + row.dimension() + " run tp @s " + row.cx() + " " + row.cy() + " " + row.cz();
                String hoverInfo = "ID: " + row.regionId() + "\n범위: " + row.boundsDesc() + "\n클릭: " + tpCmd;

                out.append(text(String.format(Locale.US, "  %02d  ", i + 1), ChatFormatting.GRAY))
                   .append(button("[이동]", ChatFormatting.YELLOW, tpCmd, hoverInfo));

                if (row.standaloneChunk()) {
                    String removeCmd = "/neodymium2 chunk removeid \"" + row.dimension() + "\" " + row.regionId();
                    String removeHover = "청크 보호 해제\n대상: " + row.displayName() + "\n클릭: 즉시 보호 해제";
                    out.append(text(" ", ChatFormatting.DARK_GRAY))
                       .append(button("[해제]", ChatFormatting.RED, removeCmd, removeHover));
                } else if (canManageExternal) {
                    String manageCmd = "/neodymium2 manage \"" + row.dimension() + "\" " + row.regionId();
                    String manageHover = "구역 관리 탭 열기\n구역: " + row.displayName() + " (" + row.regionId() + ")\n클릭: 구역 관리 GUI 열기";
                    out.append(text(" ", ChatFormatting.DARK_GRAY))
                       .append(button("[관리]", ChatFormatting.AQUA, manageCmd, manageHover));
                }

                out.append(text("  " + dimShort + "   ", ChatFormatting.GRAY))
                   .append(text(row.displayName(), ChatFormatting.WHITE))
                   .append(text(String.format(Locale.US, "  (%d, %d, %d)\n", row.cx(), row.cy(), row.cz()), ChatFormatting.GRAY));
            }
        }

        out.append(paginationBar("/neodymium2 regions ", page, totalPages))
           .append(text("   |   ", ChatFormatting.DARK_GRAY))
           .append(navBar("regions"));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    public static int toggleCurrentChunk(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text("콘솔에서는 /neodymium2 chunk add <chunkX> <chunkZ> 또는 remove <chunkX> <chunkZ> 를 사용하세요.", ChatFormatting.RED));
            return 0;
        }
        String dim = player.level().dimension().identifier().toString();
        BlockPos pos = player.blockPosition();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        RegionPolicies.Ref exact = RegionPolicies.Ref.ofChunk(dim, cx, cz);
        if (Neodymium2.sensitive(exact)) {
            return removeChunkCoords(source, cx, cz);
        }
        return addChunkCoords(source, cx, cz);
    }

    public static int addCurrentChunk(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text("콘솔에서는 /neodymium2 chunk add <chunkX> <chunkZ> 를 사용하세요.", ChatFormatting.RED));
            return 0;
        }
        if (radius < 0 || radius > 16) {
            source.sendFailure(text("청크 반경은 0 ~ 16 사이여야 합니다.", ChatFormatting.RED));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        if (radius == 0) {
            return addChunkCoords(source, cx, cz);
        }
        return addChunkRangeCoords(source, cx - radius, cz - radius, cx + radius, cz + radius);
    }

    public static int addChunkCoords(CommandSourceStack source, int chunkX, int chunkZ) {
        String dim = source.getLevel().dimension().identifier().toString();
        try {
            RegionPolicies.Ref ref = RegionPolicies.Ref.ofChunk(dim, chunkX, chunkZ);
            Neodymium2.setSensitive(ref, true);
            source.sendSuccess(() -> text(
                String.format(Locale.US, "청크 [%d, %d] (%s) 바닐라 민감도 보호를 활성화했습니다.", chunkX, chunkZ, formatDim(dim).trim()),
                ChatFormatting.GREEN
            ), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text("청크 보호를 저장하지 못했습니다: " + ex.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    public static int addChunkRangeCoords(CommandSourceStack source, int cx1, int cz1, int cx2, int cz2) {
        String dim = source.getLevel().dimension().identifier().toString();
        try {
            RegionPolicies.Ref ref = RegionPolicies.Ref.ofChunkRange(dim, cx1, cz1, cx2, cz2);
            Neodymium2.setSensitive(ref, true);
            int minCx = Math.min(cx1, cx2), maxCx = Math.max(cx1, cx2);
            int minCz = Math.min(cz1, cz2), maxCz = Math.max(cz1, cz2);
            source.sendSuccess(() -> text(
                String.format(Locale.US, "청크 범위 [%d,%d ~ %d,%d] (%s) 바닐라 민감도 보호를 활성화했습니다.",
                    minCx, minCz, maxCx, maxCz, formatDim(dim).trim()),
                ChatFormatting.GREEN
            ), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text("청크 범위 보호를 저장하지 못했습니다: " + ex.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    public static int removeCurrentChunk(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text("콘솔에서는 /neodymium2 chunk remove <chunkX> <chunkZ> 를 사용하세요.", ChatFormatting.RED));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        return removeChunkCoords(source, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int removeChunkCoords(CommandSourceStack source, int chunkX, int chunkZ) {
        String dim = source.getLevel().dimension().identifier().toString();
        List<RegionPolicies.Ref> matching = new ArrayList<>();
        for (RegionPolicies.Ref ref : Neodymium2.snapshot().regions()) {
            if (ref.coversChunk(dim, chunkX, chunkZ)) {
                matching.add(ref);
            }
        }
        if (matching.isEmpty()) {
            source.sendFailure(text(String.format(Locale.US, "청크 [%d, %d] 에 지정된 단독 청크 보호가 없습니다.", chunkX, chunkZ), ChatFormatting.RED));
            return 0;
        }
        try {
            for (RegionPolicies.Ref ref : matching) {
                Neodymium2.setSensitive(ref, false);
            }
            source.sendSuccess(() -> text(
                String.format(Locale.US, "청크 [%d, %d] 관련 바닐라 보호(%d개)를 해제했습니다.", chunkX, chunkZ, matching.size()),
                ChatFormatting.YELLOW
            ), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text("청크 보호를 해제하지 못했습니다: " + ex.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    public static int removeChunkById(CommandSourceStack source, String dimension, String regionId) {
        try {
            RegionPolicies.Ref ref = new RegionPolicies.Ref(dimension, regionId);
            if (!Neodymium2.sensitive(ref)) {
                source.sendFailure(text("이미 해제되었거나 존재하지 않는 보호 항목입니다: " + regionId, ChatFormatting.RED));
                return 0;
            }
            Neodymium2.setSensitive(ref, false);
            source.sendSuccess(() -> text("보호 구역 [" + regionId + "] 을(를) 해제했습니다.", ChatFormatting.YELLOW), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text("보호 해제 실패: " + ex.getMessage(), ChatFormatting.RED));
            return 0;
        }
    }

    public static int openRegionManageGui(CommandSourceStack source, String dimension, String regionId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text("플레이어만 구역 관리 GUI를 열 수 있습니다.", ChatFormatting.RED));
            return 0;
        }
        NeodymiumRegionProvider provider = NeodymiumApi.getRegionProvider();
        if (provider == null || !provider.canOpenManageGui()) {
            source.sendFailure(text("연결된 외부 구역 관리 GUI 제공자(YAWPManager 등)가 없습니다.", ChatFormatting.RED));
            return 0;
        }
        if (!provider.openManageGui(player, dimension, regionId)) {
            source.sendFailure(text("해당 구역을 찾을 수 없거나 관리 권한이 없습니다: " + regionId, ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private record NametagGroupKey(String dimension, String locationKey, String customName, String entityTypeName) {}

    private static final class NametagGroup {
        final String dimension;
        final String regionDisplay;
        final String regionRawId;
        final String customName;
        final String entityTypeName;
        final boolean protectedRegion;
        final boolean isExemptNameAttempt;
        int count;
        int sampleX, sampleY, sampleZ;

        NametagGroup(String dimension, String regionDisplay, String regionRawId, String customName, String entityTypeName,
                     boolean protectedRegion, boolean isExemptNameAttempt, int x, int y, int z) {
            this.dimension = dimension;
            this.regionDisplay = regionDisplay;
            this.regionRawId = regionRawId;
            this.customName = customName;
            this.entityTypeName = entityTypeName;
            this.protectedRegion = protectedRegion;
            this.isExemptNameAttempt = isExemptNameAttempt;
            this.sampleX = x;
            this.sampleY = y;
            this.sampleZ = z;
        }
    }

    public static int sendNametagsPage(CommandSourceStack source, int requestedPage) {
        MinecraftServer server = source.getServer();
        String exemptName = Neodymium2.snapshot().settings().villagerLobotomy().exemptName();
        NeodymiumRegionProvider provider = NeodymiumApi.getRegionProvider();
        Map<NametagGroupKey, NametagGroup> groups = new LinkedHashMap<>();
        int totalNamedEntities = 0;
        int totalBlockedExempt = 0;
        int totalProtectedExempt = 0;

        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                String dim = level.dimension().identifier().toString();
                for (Entity entity : level.getAllEntities()) {
                    if (entity == null || entity instanceof Player || !entity.hasCustomName()) continue;
                    Component cn = entity.getCustomName();
                    if (cn == null) continue;
                    String nameStr = cn.getString();
                    boolean isExemptAttempt = !exemptName.isEmpty() && exemptName.equals(nameStr);
                    boolean isProtected = Neodymium2.regionProtects(entity);
                    totalNamedEntities++;
                    if (isExemptAttempt) {
                        if (isProtected) totalProtectedExempt++;
                        else totalBlockedExempt++;
                    }

                    BlockPos pos = entity.blockPosition();
                    String locKey;
                    String regionDisp;
                    String rawId;
                    NeodymiumRegionProvider.RegionAtPos extRegion = null;
                    if (provider != null) {
                        try {
                            extRegion = provider.findRegionAt(level, pos);
                        } catch (Throwable ignored) {}
                    }
                    if (extRegion != null) {
                        rawId = extRegion.regionId();
                        locKey = "region:" + rawId;
                        regionDisp = cleanRegionName(extRegion.displayName());
                    } else {
                        int cx = pos.getX() >> 4;
                        int cz = pos.getZ() >> 4;
                        rawId = "chunk:" + cx + "," + cz;
                        locKey = "chunk:" + (cx >> 1) + ":" + (cz >> 1);
                        regionDisp = isProtected ? String.format(Locale.US, "청크 [%d, %d]", cx, cz) : "야생";
                    }

                    String typeName = entity.getType().getDescription().getString();
                    NametagGroupKey key = new NametagGroupKey(dim, locKey, nameStr, typeName);
                    NametagGroup grp = groups.computeIfAbsent(key, k -> new NametagGroup(
                        dim, regionDisp, rawId, nameStr, typeName, isProtected, isExemptAttempt,
                        pos.getX(), pos.getY(), pos.getZ()
                    ));
                    grp.count++;
                }
            }
        }

        List<NametagGroup> list = new ArrayList<>(groups.values());
        list.sort((a, b) -> {
            boolean aBlocked = a.isExemptNameAttempt && !a.protectedRegion;
            boolean bBlocked = b.isExemptNameAttempt && !b.protectedRegion;
            if (aBlocked != bBlocked) return aBlocked ? -1 : 1;
            if (a.isExemptNameAttempt != b.isExemptNameAttempt) return a.isExemptNameAttempt ? -1 : 1;
            return Integer.compare(b.count, a.count);
        });

        int totalGroups = list.size();
        int totalPages = Math.max(1, (totalGroups + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 1, totalPages);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(totalGroups, start + PAGE_SIZE);

        MutableComponent out = Component.empty();
        out.append(text(String.format(Locale.US, "이름표 엔티티  %d / %d 페이지  (총 %d마리 · 차단 %d · 보호 %d)\n",
            page, totalPages, totalNamedEntities, totalBlockedExempt, totalProtectedExempt), ChatFormatting.GOLD));

        if (totalGroups == 0) {
            out.append(text("  로드된 청크 내에 이름표가 부착된 엔티티가 없습니다.\n", ChatFormatting.GRAY));
        } else {
            for (int i = start; i < end; i++) {
                NametagGroup g = list.get(i);
                MutableComponent stateCol;
                if (g.protectedRegion) {
                    stateCol = text("보호   ", ChatFormatting.GREEN);
                } else if (g.isExemptNameAttempt) {
                    stateCol = text("차단   ", ChatFormatting.RED);
                } else {
                    stateCol = text("일반   ", ChatFormatting.GRAY);
                }

                String tpCmd = "/execute in " + g.dimension + " run tp @s " + g.sampleX + " " + g.sampleY + " " + g.sampleZ;
                String hoverInfo = String.format(Locale.US, "위치: (%d, %d, %d)\n구역 ID: %s\n클릭: %s",
                    g.sampleX, g.sampleY, g.sampleZ, g.regionRawId, tpCmd);

                out.append(text(String.format(Locale.US, "  %02d  ", i + 1), ChatFormatting.GRAY))
                   .append(button("[이동]", ChatFormatting.YELLOW, tpCmd, hoverInfo))
                   .append(text("  ", ChatFormatting.GRAY))
                   .append(stateCol)
                   .append(text("\"" + g.customName + "\"  ", ChatFormatting.WHITE))
                   .append(text(g.entityTypeName + " x" + g.count + "   ", ChatFormatting.GRAY))
                   .append(text(g.regionDisplay + "\n", ChatFormatting.GRAY));
            }
        }

        out.append(paginationBar("/neodymium2 nametags ", page, totalPages))
           .append(text("   |   ", ChatFormatting.DARK_GRAY))
           .append(navBar("nametags"));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static MutableComponent paginationBar(String baseCommand, int page, int totalPages) {
        MutableComponent bar = Component.empty();
        if (page > 1) {
            bar.append(button("[<]", ChatFormatting.YELLOW, baseCommand + (page - 1), (page - 1) + "페이지"));
        } else {
            bar.append(text("[<]", ChatFormatting.DARK_GRAY));
        }
        bar.append(text(String.format(Locale.US, "  %d / %d  ", page, totalPages), ChatFormatting.WHITE));
        if (page < totalPages) {
            bar.append(button("[>]", ChatFormatting.YELLOW, baseCommand + (page + 1), (page + 1) + "페이지"));
        } else {
            bar.append(text("[>]", ChatFormatting.DARK_GRAY));
        }
        return bar;
    }

    private static String cleanRegionName(String name) {
        if (name == null || name.isBlank()) return "미지정 구역";
        if (name.startsWith("area_") || name.startsWith("region-")) return "일반 구역";
        return name;
    }

    private static String formatDim(String dim) {
        if ("minecraft:overworld".equals(dim)) return "오버월드";
        if ("minecraft:the_nether".equals(dim)) return "네더    ";
        if ("minecraft:the_end".equals(dim)) return "엔드    ";
        return dim;
    }

    private static String compact(long v) {
        if (v >= 1_000_000L) return String.format(Locale.US, "%.1fM", v / 1_000_000.0);
        if (v >= 10_000L) return String.format(Locale.US, "%.1fK", v / 1_000.0);
        return NUM.format(v);
    }
}
