package kr.harusarri.neodymium2;

import com.google.gson.*;
import kr.harusarri.neodymium2.api.NeodymiumApi;
import kr.harusarri.neodymium2.api.NeodymiumRegionProvider;
import kr.harusarri.neodymium2.core.*;
import java.util.*;

public final class RegionPolicies {
    private static final int MAX_CHUNK_COORD = 1_875_000;
    private static final int MAX_CHUNK_RANGE_SPAN = 256;

    public record Ref(String dimension, String region) {
        public Ref {
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || region == null || region.isBlank() || region.length() > 256) {
                throw new IllegalArgumentException("Invalid region reference");
            }
        }

        public boolean isStandaloneChunk() {
            return region.startsWith("chunk:") || region.startsWith("chunks:");
        }

        public static Ref ofChunk(String dimension, int chunkX, int chunkZ) {
            validateChunkCoord(chunkX, chunkZ);
            return new Ref(dimension, "chunk:" + chunkX + "," + chunkZ);
        }

        public static Ref ofChunkRange(String dimension, int cx1, int cz1, int cx2, int cz2) {
            validateChunkCoord(cx1, cz1);
            validateChunkCoord(cx2, cz2);
            int minCx = Math.min(cx1, cx2);
            int maxCx = Math.max(cx1, cx2);
            int minCz = Math.min(cz1, cz2);
            int maxCz = Math.max(cz1, cz2);
            if (maxCx - minCx + 1 > MAX_CHUNK_RANGE_SPAN || maxCz - minCz + 1 > MAX_CHUNK_RANGE_SPAN) {
                throw new IllegalArgumentException("Chunk range span exceeds maximum (" + MAX_CHUNK_RANGE_SPAN + ")");
            }
            if (minCx == maxCx && minCz == maxCz) {
                return ofChunk(dimension, minCx, minCz);
            }
            return new Ref(dimension, "chunks:" + minCx + "," + minCz + ".." + maxCx + "," + maxCz);
        }

        public boolean coversChunk(String dim, int chunkX, int chunkZ) {
            if (!this.dimension.equals(dim) || !isStandaloneChunk()) return false;
            int[] bounds = parseStandaloneChunkBounds(this.region);
            if (bounds == null) return false;
            return chunkX >= bounds[0] && chunkX <= bounds[2] && chunkZ >= bounds[1] && chunkZ <= bounds[3];
        }

        public NeodymiumRegionProvider.ResolvedRegion resolve() {
            if (isStandaloneChunk()) {
                return resolveStandaloneChunk(dimension, region);
            }
            NeodymiumRegionProvider provider = NeodymiumApi.getRegionProvider();
            return provider != null ? provider.resolve(dimension, region) : null;
        }
    }

    private static void validateChunkCoord(int cx, int cz) {
        if (cx < -MAX_CHUNK_COORD || cx > MAX_CHUNK_COORD || cz < -MAX_CHUNK_COORD || cz > MAX_CHUNK_COORD) {
            throw new IllegalArgumentException("Chunk coordinates out of bounds: " + cx + ", " + cz);
        }
    }

    /**
     * Parses "chunk:x,z" or "chunks:x1,z1..x2,z2" into [minCx, minCz, maxCx, maxCz], or null if invalid.
     */
    public static int[] parseStandaloneChunkBounds(String regionId) {
        try {
            if (regionId.startsWith("chunk:")) {
                String rest = regionId.substring("chunk:".length());
                int comma = rest.indexOf(',');
                if (comma <= 0 || comma != rest.lastIndexOf(',')) return null;
                int cx = Integer.parseInt(rest.substring(0, comma).trim());
                int cz = Integer.parseInt(rest.substring(comma + 1).trim());
                validateChunkCoord(cx, cz);
                return new int[]{cx, cz, cx, cz};
            }
            if (regionId.startsWith("chunks:")) {
                String rest = regionId.substring("chunks:".length());
                int dots = rest.indexOf("..");
                if (dots <= 0) return null;
                String first = rest.substring(0, dots);
                String second = rest.substring(dots + 2);
                int c1 = first.indexOf(',');
                int c2 = second.indexOf(',');
                if (c1 <= 0 || c1 != first.lastIndexOf(',') || c2 <= 0 || c2 != second.lastIndexOf(',')) return null;
                int cx1 = Integer.parseInt(first.substring(0, c1).trim());
                int cz1 = Integer.parseInt(first.substring(c1 + 1).trim());
                int cx2 = Integer.parseInt(second.substring(0, c2).trim());
                int cz2 = Integer.parseInt(second.substring(c2 + 1).trim());
                validateChunkCoord(cx1, cz1);
                validateChunkCoord(cx2, cz2);
                if (cx1 > cx2 || cz1 > cz2) return null;
                if (cx2 - cx1 + 1 > MAX_CHUNK_RANGE_SPAN || cz2 - cz1 + 1 > MAX_CHUNK_RANGE_SPAN) return null;
                return new int[]{cx1, cz1, cx2, cz2};
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static NeodymiumRegionProvider.ResolvedRegion resolveStandaloneChunk(String dimension, String regionId) {
        int[] b = parseStandaloneChunkBounds(regionId);
        if (b == null) return null;
        int minCx = b[0], minCz = b[1], maxCx = b[2], maxCz = b[3];
        int minBlockX = minCx * 16;
        int minBlockZ = minCz * 16;
        int maxBlockX = maxCx * 16 + 15;
        int maxBlockZ = maxCz * 16 + 15;
        int centerX = (minBlockX + maxBlockX) / 2;
        int centerY = 80;
        int centerZ = (minBlockZ + maxBlockZ) / 2;
        SpatialIndex.Box box = new SpatialIndex.Box(minBlockX, -2048, minBlockZ, (double) maxBlockX + 1, 2048, (double) maxBlockZ + 1);
        String disp;
        String desc;
        if (minCx == maxCx && minCz == maxCz) {
            disp = String.format(Locale.US, "청크 [%d, %d]", minCx, minCz);
            desc = String.format(Locale.US, "청크 (%d, %d) · 블록 (%d, %d) ~ (%d, %d) [16x16]",
                minCx, minCz, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        } else {
            int wChunks = maxCx - minCx + 1;
            int zChunks = maxCz - minCz + 1;
            disp = String.format(Locale.US, "청크 범위 [%d,%d ~ %d,%d] (%dx%d)", minCx, minCz, maxCx, maxCz, wChunks, zChunks);
            desc = String.format(Locale.US, "청크 (%d,%d) ~ (%d,%d) [%dx%d청크] · 블록 (%d, %d) ~ (%d, %d)",
                minCx, minCz, maxCx, maxCz, wChunks, zChunks, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        }
        return new NeodymiumRegionProvider.ResolvedRegion(dimension, regionId, disp, box, centerX, centerY, centerZ, desc);
    }

    public static Set<Ref> parse(String text) {
        var root = Settings.JSON.fromJson(text, JsonObject.class);
        if (root == null || root.size() != 2 || !root.has("schemaVersion") || !root.has("regions")
            || !root.get("schemaVersion").isJsonPrimitive() || !root.getAsJsonPrimitive("schemaVersion").isNumber()
            || root.get("schemaVersion").getAsBigDecimal().intValueExact() != 1) {
            throw new IllegalArgumentException("Invalid region policy file");
        }
        var array = root.getAsJsonArray("regions");
        if (array.size() > 4096) throw new IllegalArgumentException("Too many region policies");
        Set<Ref> result = new HashSet<>(), seen = new HashSet<>();
        for (var v : array) {
            var o = v.getAsJsonObject();
            if (o.size() != 3 || !o.has("dimension") || !o.has("region") || !o.has("vanillaSensitive")) {
                throw new IllegalArgumentException("Invalid policy fields");
            }
            var b = o.get("vanillaSensitive");
            if (!b.isJsonPrimitive() || !b.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("vanillaSensitive must be boolean");
            }
            for (String key : List.of("dimension", "region")) {
                if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isString()) {
                    throw new IllegalArgumentException(key + " must be a string");
                }
            }
            Ref ref = new Ref(o.get("dimension").getAsString(), o.get("region").getAsString());
            if (!seen.add(ref)) throw new IllegalArgumentException("Duplicate region policy");
            if (b.getAsBoolean()) result.add(ref);
        }
        return Set.copyOf(result);
    }

    public static String json(Set<Ref> refs) {
        var root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        var a = new JsonArray();
        refs.stream().sorted(Comparator.comparing(Ref::dimension).thenComparing(Ref::region)).forEach(r -> {
            var o = new JsonObject();
            o.addProperty("dimension", r.dimension);
            o.addProperty("region", r.region);
            o.addProperty("vanillaSensitive", true);
            a.add(o);
        });
        root.add("regions", a);
        return Settings.JSON.toJson(root) + "\n";
    }

    public static SpatialIndex compile(Set<Ref> refs, int margin) {
        Map<String, List<SpatialIndex.Box>> dims = new HashMap<>();
        for (Ref ref : refs) {
            var resolved = ref.resolve();
            if (resolved == null || resolved.bounds() == null) {
                throw new IllegalStateException("Protected region missing: " + ref);
            }
            dims.computeIfAbsent(ref.dimension, k -> new ArrayList<>()).add(resolved.bounds().expand(margin));
        }
        return new SpatialIndex(dims);
    }
}
