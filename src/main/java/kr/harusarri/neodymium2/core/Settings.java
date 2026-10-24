package kr.harusarri.neodymium2.core;

import com.google.gson.*;
import java.util.*;

public record Settings(boolean enabled,Set<String> reclaimEntityTypes,int activationRange,int inactiveTickInterval,
                       int wakeupIntervalSeconds,int regionMarginBlocks,boolean requireProtectedRegion,
                       boolean lazyChunkProjectileCleanup,VillagerLobotomySettings villagerLobotomy,
                       boolean outsideSpawnThrottle,int outsideSpawnBaseInterval,int outsideSpawnMaxInterval,
                       boolean outsideEntityTightening,boolean outsideWorldTightening) {
    public Settings(boolean enabled,Set<String> types,int range,int interval,int wakeup,int margin,boolean requireRegion,boolean projectiles,VillagerLobotomySettings lobotomy,boolean outsideSpawnThrottle,int outsideSpawnBaseInterval,int outsideSpawnMaxInterval,boolean outsideEntityTightening){this(enabled,types,range,interval,wakeup,margin,requireRegion,projectiles,lobotomy,outsideSpawnThrottle,outsideSpawnBaseInterval,outsideSpawnMaxInterval,outsideEntityTightening,outsideEntityTightening);}
    public Settings(boolean enabled,Set<String> types,int range,int interval,int wakeup,int margin,boolean requireRegion,boolean projectiles,VillagerLobotomySettings lobotomy){this(enabled,types,range,interval,wakeup,margin,requireRegion,projectiles,lobotomy,true,4,16,true,true);}
    public Settings(boolean enabled,Set<String> types,int range,int interval,int wakeup,int margin,boolean requireRegion,boolean projectiles){this(enabled,types,range,interval,wakeup,margin,requireRegion,projectiles,VillagerLobotomySettings.defaults(),true,4,16,true,true);}
    public Settings(boolean enabled,Set<String> types,int range,int interval,int wakeup,int margin,boolean requireRegion){this(enabled,types,range,interval,wakeup,margin,requireRegion,false,VillagerLobotomySettings.defaults(),true,4,16,true,true);}
    public static final Set<String> SUPPORTED=Set.of("minecraft:zombie","minecraft:husk","minecraft:drowned","minecraft:zombified_piglin","minecraft:skeleton","minecraft:stray","minecraft:bogged","minecraft:creeper","minecraft:villager","minecraft:wandering_trader","minecraft:pillager");
    private static final Set<String> KEYS=Set.of("schemaVersion","enabled","reclaimEntityTypes","activationRange","inactiveTickInterval","wakeupIntervalSeconds","regionMarginBlocks","requireProtectedRegion","lazyChunkProjectileCleanup","villagerLobotomy","outsideSpawnThrottle","outsideSpawnBaseInterval","outsideSpawnMaxInterval","outsideEntityTightening","outsideWorldTightening");
    public static final Gson JSON=new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().disableHtmlEscaping().create();
    public Settings {
        Objects.requireNonNull(villagerLobotomy,"villagerLobotomy");
        reclaimEntityTypes=Set.copyOf(reclaimEntityTypes);
        if(!SUPPORTED.containsAll(reclaimEntityTypes))throw new IllegalArgumentException("Unsupported reclaimEntityTypes; use the documented tested mob types");
        if(activationRange<8||activationRange>128||inactiveTickInterval<1||inactiveTickInterval>200||wakeupIntervalSeconds< -1||wakeupIntervalSeconds>600||regionMarginBlocks<0||regionMarginBlocks>32)throw new IllegalArgumentException("Setting outside allowed limits");
        if(outsideSpawnBaseInterval<1||outsideSpawnBaseInterval>20||outsideSpawnMaxInterval<outsideSpawnBaseInterval||outsideSpawnMaxInterval>80)throw new IllegalArgumentException("Outside spawn interval outside allowed limits");
    }
    public static Settings defaults(){return new Settings(false,Set.of("minecraft:zombie","minecraft:skeleton","minecraft:creeper","minecraft:villager"),24,20,60,2,true);}
    public static Settings parse(String text) {
        JsonObject o=JSON.fromJson(text,JsonObject.class);if(o==null)throw new IllegalArgumentException("Empty config");
        for(String k:o.keySet())if(!KEYS.contains(k)&&!"regionEntryActionBar".equals(k)&&!"language".equals(k))throw new IllegalArgumentException("Unknown setting: "+k);
        if(number(o,"schemaVersion",1)!=1)throw new IllegalArgumentException("Unsupported schemaVersion");
        kr.harusarri.neodymium2.RegionEntryNotifier.setEnabled(bool(o,"regionEntryActionBar",true));
        if(o.has("language")){var lv=o.get("language");if(!lv.isJsonPrimitive()||!lv.getAsJsonPrimitive().isString()||!kr.harusarri.neodymium2.NeodymiumI18n.setMode(lv.getAsString()))throw new IllegalArgumentException("language must be auto, en_us, or ko_kr");}
        Settings d=defaults();Set<String> types=d.reclaimEntityTypes;
        if(o.has("reclaimEntityTypes")){types=new HashSet<>();var a=o.getAsJsonArray("reclaimEntityTypes");if(a.size()>64)throw new IllegalArgumentException("Too many types");for(var v:a){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Entity type must be a string");types.add(v.getAsString());}}
        boolean entityTight=bool(o,"outsideEntityTightening",true);
        return new Settings(bool(o,"enabled",d.enabled),types,number(o,"activationRange",d.activationRange),number(o,"inactiveTickInterval",d.inactiveTickInterval),number(o,"wakeupIntervalSeconds",d.wakeupIntervalSeconds),number(o,"regionMarginBlocks",d.regionMarginBlocks),bool(o,"requireProtectedRegion",true),bool(o,"lazyChunkProjectileCleanup",false),o.has("villagerLobotomy")?VillagerLobotomySettings.parse(o.get("villagerLobotomy")):VillagerLobotomySettings.defaults(),bool(o,"outsideSpawnThrottle",true),number(o,"outsideSpawnBaseInterval",4),number(o,"outsideSpawnMaxInterval",16),entityTight,bool(o,"outsideWorldTightening",entityTight));
    }
    static boolean bool(JsonObject o,String k,boolean d){if(!o.has(k))return d;var v=o.get(k);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(k+" must be boolean");return v.getAsBoolean();}
    static int number(JsonObject o,String k,int d){if(!o.has(k))return d;var v=o.get(k);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(k+" must be an integer");return v.getAsBigDecimal().intValueExact();}
    public String json(){var o=JSON.toJsonTree(this).getAsJsonObject();o.addProperty("schemaVersion",1);o.addProperty("regionEntryActionBar",kr.harusarri.neodymium2.RegionEntryNotifier.enabled());return JSON.toJson(o)+"\n";}
}
