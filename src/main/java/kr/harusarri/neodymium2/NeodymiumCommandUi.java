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
 * including standalone chunk protection commands, external RegionProvider integration,
 * and automatic client-language localization (en_us / ko_kr).
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
            .then(Commands.literal("lang")
                .executes(ctx -> executeLang(ctx.getSource(), null))
                .then(Commands.argument("mode", StringArgumentType.word())
                    .executes(ctx -> executeLang(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
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

    private static MutableComponent navBar(String activeTab, String lang) {
        MutableComponent bar = Component.empty();
        bar.append(button(NeodymiumI18n.getByLang(lang, "nav.status"), activeTab.equals("status") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 status", "/neodymium2 status"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button(NeodymiumI18n.getByLang(lang, "nav.modules"), activeTab.equals("modules") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 modules", "/neodymium2 modules"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button(NeodymiumI18n.getByLang(lang, "nav.regions"), activeTab.equals("regions") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 regions 1", "/neodymium2 regions 1"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button(NeodymiumI18n.getByLang(lang, "nav.nametags"), activeTab.equals("nametags") ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
            "/neodymium2 nametags 1", "/neodymium2 nametags 1"));
        bar.append(text("  ", ChatFormatting.DARK_GRAY));
        bar.append(button(NeodymiumI18n.getByLang(lang, "nav.reload"), ChatFormatting.GRAY,
            "/neodymium2 reload", "/neodymium2 reload"));
        return bar;
    }

    public static int executeLang(CommandSourceStack source, String requestedMode) {
        String lang = NeodymiumI18n.resolveLang(source);
        if (requestedMode == null || requestedMode.isBlank()) {
            source.sendSuccess(() -> text(
                NeodymiumI18n.trByLang(lang, "lang.set", NeodymiumI18n.mode(), lang),
                ChatFormatting.GREEN
            ), false);
            return 1;
        }
        if (!NeodymiumI18n.setMode(requestedMode)) {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "lang.usage"), ChatFormatting.RED));
            return 0;
        }
        String updatedLang = NeodymiumI18n.resolveLang(source);
        source.sendSuccess(() -> text(
            NeodymiumI18n.trByLang(updatedLang, "lang.set", NeodymiumI18n.mode(), updatedLang),
            ChatFormatting.GREEN
        ), false);
        return 1;
    }

    public static int sendStatus(CommandSourceStack source) {
        String lang = NeodymiumI18n.resolveLang(source);
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
           .append(text(NeodymiumI18n.getByLang(lang, "status.protected_regions"), ChatFormatting.GRAY))
           .append(text(NeodymiumI18n.trByLang(lang, "status.protected_regions_val", s.regions().size()), ChatFormatting.WHITE));

        if (!s.guard().isEmpty()) {
            out.append(text(NeodymiumI18n.getByLang(lang, "status.guard_label"), ChatFormatting.RED))
               .append(text(s.guard() + "\n", ChatFormatting.WHITE));
        }

        out.append(text(NeodymiumI18n.getByLang(lang, "status.spawn_label"), ChatFormatting.GRAY))
           .append(text(NeodymiumI18n.trByLang(lang, "status.spawn_val",
               compact(RegionSpawner.outsideEvaluatedChunks()),
               compact(RegionSpawner.outsideSkippedChunks()),
               compact(RegionSpawner.elidedSpawnCostLookups() + OutsideSpawnAndDensity.precookedReuses() + OutsideSpawnAndDensity.fastCollisionHits())
           ), ChatFormatting.WHITE));

        out.append(text(NeodymiumI18n.getByLang(lang, "status.entity_label"), ChatFormatting.GRAY))
           .append(text(NeodymiumI18n.trByLang(lang, "status.entity_val",
               compact(OutsideEntityOptimizer.skippedAiTicks()),
               compact(OutsideEntityOptimizer.skippedPushTicks()),
               compact(OutsideSpawnAndDensity.overcrowdedThrottles())
           ), ChatFormatting.WHITE));

        out.append(text(NeodymiumI18n.getByLang(lang, "status.villager_label"), ChatFormatting.GRAY))
           .append(text(NeodymiumI18n.trByLang(lang, "status.villager_val",
               VillagerLobotomy.enabled() ? "ON" : "OFF",
               compact(OutsideSpawnAndDensity.blockedNametagBypasses()),
               RegionEntryNotifier.enabled() ? "ON" : "OFF"
           ), ChatFormatting.WHITE));

        out.append(text(NeodymiumI18n.getByLang(lang, "status.world_label"), ChatFormatting.GRAY))
           .append(text(NeodymiumI18n.trByLang(lang, "status.world_val",
               compact(OutsideWorldOptimizer.throttledRandomTickChunks()),
               compact(OutsideWorldOptimizer.skippedFluidScans()),
               compact(OutsideWorldOptimizer.throttledHopperPolls())
           ), ChatFormatting.WHITE));

        out.append(navBar("status", lang));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    public static int sendModules(CommandSourceStack source) {
        String lang = NeodymiumI18n.resolveLang(source);
        List<NeodymiumModule> modules = ModuleRegistry.all();
        MutableComponent out = Component.empty();
        out.append(text(NeodymiumI18n.trByLang(lang, "modules.header", modules.size() + 1), ChatFormatting.GOLD));

        for (NeodymiumModule m : modules) {
            boolean on = m.enabled();
            String modName = NeodymiumI18n.getByLang(lang, "module." + m.id());
            if (("module." + m.id()).equals(modName)) {
                modName = m.displayName();
            }
            MutableComponent line = text("  ", ChatFormatting.GRAY)
                .append(text(on ? "ON   " : "OFF  ", on ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(text(modName, ChatFormatting.WHITE))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                    Component.literal(NeodymiumI18n.trByLang(lang, "modules.hover_target", m.id(), m.targetHooks()))
                )));
            out.append(line).append(text("\n", ChatFormatting.GRAY));
        }
        out.append(text("  ", ChatFormatting.GRAY))
           .append(text("ON   ", ChatFormatting.GREEN))
           .append(text(NeodymiumI18n.getByLang(lang, "modules.extra_lagfree"), ChatFormatting.WHITE));

        out.append(navBar("modules", lang));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    public static int executeReload(CommandSourceStack source) {
        String lang = NeodymiumI18n.resolveLang(source);
        boolean ok = Neodymium2.reload();
        if (ok) {
            source.sendSuccess(() -> text(NeodymiumI18n.getByLang(lang, "reload.success"), ChatFormatting.GREEN), false);
            return sendStatus(source);
        } else {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "reload.failed"), ChatFormatting.RED));
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
        String lang = NeodymiumI18n.resolveLang(source);
        var snap = Neodymium2.snapshot();
        List<RegionPolicies.Ref> refs = new ArrayList<>(snap.regions());
        refs.sort(Comparator.comparing(RegionPolicies.Ref::dimension).thenComparing(RegionPolicies.Ref::region));

        NeodymiumRegionProvider provider = NeodymiumApi.getRegionProvider();
        boolean canManageExternal = provider != null && provider.canOpenManageGui();

        List<RegionRow> rows = new ArrayList<>(refs.size());
        for (RegionPolicies.Ref ref : refs) {
            NeodymiumRegionProvider.ResolvedRegion resolved = ref.resolve();
            String disp = cleanRegionName(ref.region(), lang);
            int cx = 0, cy = 80, cz = 0;
            String boundsDesc = NeodymiumI18n.getByLang(lang, "regions.no_bounds");
            if (resolved != null) {
                if (ref.isStandaloneChunk()) {
                    int[] b = RegionPolicies.parseStandaloneChunkBounds(ref.region());
                    if (b != null) {
                        if (b[0] == b[2] && b[1] == b[3]) {
                            disp = NeodymiumI18n.trByLang(lang, "nametags.chunk_fmt", b[0], b[1]);
                        } else {
                            disp = NeodymiumI18n.trByLang(lang, "nametags.chunk_range_fmt",
                                b[0], b[1], b[2], b[3], b[2] - b[0] + 1, b[3] - b[1] + 1);
                        }
                    } else if (resolved.displayName() != null && !resolved.displayName().isBlank()) {
                        disp = resolved.displayName();
                    }
                } else if (resolved.displayName() != null && !resolved.displayName().isBlank()) {
                    disp = cleanRegionName(resolved.displayName(), lang);
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
        out.append(text(NeodymiumI18n.trByLang(lang, "regions.header", page, totalPages, total), ChatFormatting.GOLD));
        if (source.getPlayer() != null) {
            out.append(button(NeodymiumI18n.getByLang(lang, "regions.btn_toggle_chunk"), ChatFormatting.GREEN,
                "/neodymium2 chunk toggle",
                NeodymiumI18n.getByLang(lang, "regions.hover_toggle_chunk")));
        }
        out.append(text("\n", ChatFormatting.GRAY));

        if (total == 0) {
            out.append(text(NeodymiumI18n.getByLang(lang, "regions.empty"), ChatFormatting.GRAY));
        } else {
            for (int i = start; i < end; i++) {
                RegionRow row = rows.get(i);
                String dimShort = formatDim(row.dimension(), lang);
                String tpCmd = "/execute in " + row.dimension() + " run tp @s " + row.cx() + " " + row.cy() + " " + row.cz();
                String hoverInfo = NeodymiumI18n.trByLang(lang, "regions.hover_tp", row.regionId(), row.boundsDesc(), tpCmd);

                out.append(text(String.format(Locale.US, "  %02d  ", i + 1), ChatFormatting.GRAY))
                   .append(button(NeodymiumI18n.getByLang(lang, "regions.btn_tp"), ChatFormatting.YELLOW, tpCmd, hoverInfo));

                if (row.standaloneChunk()) {
                    String removeCmd = "/neodymium2 chunk removeid \"" + row.dimension() + "\" " + row.regionId();
                    String removeHover = NeodymiumI18n.trByLang(lang, "regions.hover_remove", row.displayName());
                    out.append(text(" ", ChatFormatting.DARK_GRAY))
                       .append(button(NeodymiumI18n.getByLang(lang, "regions.btn_remove"), ChatFormatting.RED, removeCmd, removeHover));
                } else if (canManageExternal) {
                    String manageCmd = "/neodymium2 manage \"" + row.dimension() + "\" " + row.regionId();
                    String manageHover = NeodymiumI18n.trByLang(lang, "regions.hover_manage", row.displayName(), row.regionId());
                    out.append(text(" ", ChatFormatting.DARK_GRAY))
                       .append(button(NeodymiumI18n.getByLang(lang, "regions.btn_manage"), ChatFormatting.AQUA, manageCmd, manageHover));
                }

                out.append(text("  " + dimShort + "   ", ChatFormatting.GRAY))
                   .append(text(row.displayName(), ChatFormatting.WHITE))
                   .append(text(String.format(Locale.US, "  (%d, %d, %d)\n", row.cx(), row.cy(), row.cz()), ChatFormatting.GRAY));
            }
        }

        out.append(paginationBar("/neodymium2 regions ", page, totalPages, lang))
           .append(text("   |   ", ChatFormatting.DARK_GRAY))
           .append(navBar("regions", lang));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    public static int toggleCurrentChunk(CommandSourceStack source) {
        String lang = NeodymiumI18n.resolveLang(source);
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "chunk.console_toggle"), ChatFormatting.RED));
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
        String lang = NeodymiumI18n.resolveLang(source);
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "chunk.console_add"), ChatFormatting.RED));
            return 0;
        }
        if (radius < 0 || radius > 16) {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "chunk.radius_range"), ChatFormatting.RED));
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
        String lang = NeodymiumI18n.resolveLang(source);
        String dim = source.getLevel().dimension().identifier().toString();
        try {
            RegionPolicies.Ref ref = RegionPolicies.Ref.ofChunk(dim, chunkX, chunkZ);
            Neodymium2.setSensitive(ref, true);
            source.sendSuccess(() -> text(
                NeodymiumI18n.trByLang(lang, "chunk.added_single", chunkX, chunkZ, formatDim(dim, lang).trim()),
                ChatFormatting.GREEN
            ), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text(NeodymiumI18n.trByLang(lang, "chunk.save_failed", ex.getMessage()), ChatFormatting.RED));
            return 0;
        }
    }

    public static int addChunkRangeCoords(CommandSourceStack source, int cx1, int cz1, int cx2, int cz2) {
        String lang = NeodymiumI18n.resolveLang(source);
        String dim = source.getLevel().dimension().identifier().toString();
        try {
            RegionPolicies.Ref ref = RegionPolicies.Ref.ofChunkRange(dim, cx1, cz1, cx2, cz2);
            Neodymium2.setSensitive(ref, true);
            int minCx = Math.min(cx1, cx2), maxCx = Math.max(cx1, cx2);
            int minCz = Math.min(cz1, cz2), maxCz = Math.max(cz1, cz2);
            source.sendSuccess(() -> text(
                NeodymiumI18n.trByLang(lang, "chunk.added_range",
                    minCx, minCz, maxCx, maxCz, formatDim(dim, lang).trim()),
                ChatFormatting.GREEN
            ), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text(NeodymiumI18n.trByLang(lang, "chunk.range_save_failed", ex.getMessage()), ChatFormatting.RED));
            return 0;
        }
    }

    public static int removeCurrentChunk(CommandSourceStack source) {
        String lang = NeodymiumI18n.resolveLang(source);
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "chunk.console_remove"), ChatFormatting.RED));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        return removeChunkCoords(source, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int removeChunkCoords(CommandSourceStack source, int chunkX, int chunkZ) {
        String lang = NeodymiumI18n.resolveLang(source);
        String dim = source.getLevel().dimension().identifier().toString();
        List<RegionPolicies.Ref> matching = new ArrayList<>();
        for (RegionPolicies.Ref ref : Neodymium2.snapshot().regions()) {
            if (ref.coversChunk(dim, chunkX, chunkZ)) {
                matching.add(ref);
            }
        }
        if (matching.isEmpty()) {
            source.sendFailure(text(NeodymiumI18n.trByLang(lang, "chunk.none_at", chunkX, chunkZ), ChatFormatting.RED));
            return 0;
        }
        try {
            for (RegionPolicies.Ref ref : matching) {
                Neodymium2.setSensitive(ref, false);
            }
            final int count = matching.size();
            source.sendSuccess(() -> text(
                NeodymiumI18n.KO_KR.equals(lang)
                    ? NeodymiumI18n.trByLang(lang, "chunk.removed_coords", chunkX, chunkZ, count)
                    : NeodymiumI18n.trByLang(lang, "chunk.removed_coords", count, chunkX, chunkZ),
                ChatFormatting.YELLOW
            ), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text(NeodymiumI18n.trByLang(lang, "chunk.remove_failed", ex.getMessage()), ChatFormatting.RED));
            return 0;
        }
    }

    public static int removeChunkById(CommandSourceStack source, String dimension, String regionId) {
        String lang = NeodymiumI18n.resolveLang(source);
        try {
            RegionPolicies.Ref ref = new RegionPolicies.Ref(dimension, regionId);
            if (!Neodymium2.sensitive(ref)) {
                source.sendFailure(text(NeodymiumI18n.trByLang(lang, "chunk.already_removed", regionId), ChatFormatting.RED));
                return 0;
            }
            Neodymium2.setSensitive(ref, false);
            source.sendSuccess(() -> text(NeodymiumI18n.trByLang(lang, "chunk.removed_id", regionId), ChatFormatting.YELLOW), true);
            return sendRegionsPage(source, 1);
        } catch (Exception ex) {
            source.sendFailure(text(NeodymiumI18n.trByLang(lang, "chunk.remove_failed", ex.getMessage()), ChatFormatting.RED));
            return 0;
        }
    }

    public static int openRegionManageGui(CommandSourceStack source, String dimension, String regionId) {
        String lang = NeodymiumI18n.resolveLang(source);
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "manage.player_only"), ChatFormatting.RED));
            return 0;
        }
        NeodymiumRegionProvider provider = NeodymiumApi.getRegionProvider();
        if (provider == null || !provider.canOpenManageGui()) {
            source.sendFailure(text(NeodymiumI18n.getByLang(lang, "manage.no_provider"), ChatFormatting.RED));
            return 0;
        }
        if (!provider.openManageGui(player, dimension, regionId)) {
            source.sendFailure(text(NeodymiumI18n.trByLang(lang, "manage.not_found", regionId), ChatFormatting.RED));
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
        String lang = NeodymiumI18n.resolveLang(source);
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
                        regionDisp = cleanRegionName(extRegion.displayName(), lang);
                    } else {
                        int cx = pos.getX() >> 4;
                        int cz = pos.getZ() >> 4;
                        rawId = "chunk:" + cx + "," + cz;
                        locKey = "chunk:" + (cx >> 1) + ":" + (cz >> 1);
                        regionDisp = isProtected
                            ? NeodymiumI18n.trByLang(lang, "nametags.chunk_fmt", cx, cz)
                            : NeodymiumI18n.getByLang(lang, "nametags.wild");
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
        out.append(text(NeodymiumI18n.trByLang(lang, "nametags.header",
            page, totalPages, totalNamedEntities, totalBlockedExempt, totalProtectedExempt), ChatFormatting.GOLD));

        if (totalGroups == 0) {
            out.append(text(NeodymiumI18n.getByLang(lang, "nametags.empty"), ChatFormatting.GRAY));
        } else {
            for (int i = start; i < end; i++) {
                NametagGroup g = list.get(i);
                MutableComponent stateCol;
                if (g.protectedRegion) {
                    stateCol = text(NeodymiumI18n.getByLang(lang, "nametags.state_protected"), ChatFormatting.GREEN);
                } else if (g.isExemptNameAttempt) {
                    stateCol = text(NeodymiumI18n.getByLang(lang, "nametags.state_blocked"), ChatFormatting.RED);
                } else {
                    stateCol = text(NeodymiumI18n.getByLang(lang, "nametags.state_normal"), ChatFormatting.GRAY);
                }

                String tpCmd = "/execute in " + g.dimension + " run tp @s " + g.sampleX + " " + g.sampleY + " " + g.sampleZ;
                String hoverInfo = NeodymiumI18n.trByLang(lang, "nametags.hover_tp",
                    g.sampleX, g.sampleY, g.sampleZ, g.regionRawId, tpCmd);

                out.append(text(String.format(Locale.US, "  %02d  ", i + 1), ChatFormatting.GRAY))
                   .append(button(NeodymiumI18n.getByLang(lang, "regions.btn_tp"), ChatFormatting.YELLOW, tpCmd, hoverInfo))
                   .append(text("  ", ChatFormatting.GRAY))
                   .append(stateCol)
                   .append(text("\"" + g.customName + "\"  ", ChatFormatting.WHITE))
                   .append(text(g.entityTypeName + " x" + g.count + "   ", ChatFormatting.GRAY))
                   .append(text(g.regionDisplay + "\n", ChatFormatting.GRAY));
            }
        }

        out.append(paginationBar("/neodymium2 nametags ", page, totalPages, lang))
           .append(text("   |   ", ChatFormatting.DARK_GRAY))
           .append(navBar("nametags", lang));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static MutableComponent paginationBar(String baseCommand, int page, int totalPages, String lang) {
        MutableComponent bar = Component.empty();
        if (page > 1) {
            bar.append(button("[<]", ChatFormatting.YELLOW, baseCommand + (page - 1),
                NeodymiumI18n.trByLang(lang, "page.hover", page - 1)));
        } else {
            bar.append(text("[<]", ChatFormatting.DARK_GRAY));
        }
        bar.append(text(String.format(Locale.US, "  %d / %d  ", page, totalPages), ChatFormatting.WHITE));
        if (page < totalPages) {
            bar.append(button("[>]", ChatFormatting.YELLOW, baseCommand + (page + 1),
                NeodymiumI18n.trByLang(lang, "page.hover", page + 1)));
        } else {
            bar.append(text("[>]", ChatFormatting.DARK_GRAY));
        }
        return bar;
    }

    private static String cleanRegionName(String name, String lang) {
        if (name == null || name.isBlank()) return NeodymiumI18n.getByLang(lang, "region.unnamed");
        if (name.startsWith("area_") || name.startsWith("region-")) return NeodymiumI18n.getByLang(lang, "region.generic");
        return name;
    }

    private static String formatDim(String dim, String lang) {
        if ("minecraft:overworld".equals(dim)) return NeodymiumI18n.getByLang(lang, "dim.overworld");
        if ("minecraft:the_nether".equals(dim)) return NeodymiumI18n.getByLang(lang, "dim.the_nether");
        if ("minecraft:the_end".equals(dim)) return NeodymiumI18n.getByLang(lang, "dim.the_end");
        return dim;
    }

    private static String compact(long v) {
        if (v >= 1_000_000L) return String.format(Locale.US, "%.1fM", v / 1_000_000.0);
        if (v >= 10_000L) return String.format(Locale.US, "%.1fK", v / 1_000.0);
        return NUM.format(v);
    }
}
