package kr.harusarri.neodymium2.core;
import com.google.gson.*;
import java.util.Set;
public record VillagerLobotomySettings(boolean enabled,String exemptName,int maintenanceIntervalTicks,int wakeAfterTradeTicks,int wakeAfterHurtTicks,boolean idleVillagersEnabled,int jobSearchMaxIntervalTicks) {
    public VillagerLobotomySettings(boolean enabled,String name,int maintenance,int trade,int hurt){this(enabled,name,maintenance,trade,hurt,false,200);}
    private static final Set<String> KEYS=Set.of("enabled","exemptName","maintenanceIntervalTicks","wakeAfterTradeTicks","wakeAfterHurtTicks","idleVillagersEnabled","jobSearchMaxIntervalTicks");
    public VillagerLobotomySettings {
        if(exemptName==null||exemptName.isBlank()||exemptName.length()>64||exemptName.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid villager exemptName");
        if(maintenanceIntervalTicks<1||maintenanceIntervalTicks>100||wakeAfterTradeTicks<0||wakeAfterTradeTicks>1200||wakeAfterHurtTicks<20||wakeAfterHurtTicks>1200)throw new IllegalArgumentException("Invalid villager timing");
        if(jobSearchMaxIntervalTicks<40||jobSearchMaxIntervalTicks>1200)throw new IllegalArgumentException("Invalid job search interval");
    }
    public static VillagerLobotomySettings defaults(){return new VillagerLobotomySettings(false,"춘식이",20,600,100);}
    public static VillagerLobotomySettings parse(JsonElement value){
        if(!value.isJsonObject())throw new IllegalArgumentException("villagerLobotomy must be an object");var o=value.getAsJsonObject();
        for(String k:o.keySet())if(!KEYS.contains(k))throw new IllegalArgumentException("Unknown villagerLobotomy setting: "+k);
        var d=defaults();String name=d.exemptName;
        if(o.has("exemptName")){var n=o.get("exemptName");if(!n.isJsonPrimitive()||!n.getAsJsonPrimitive().isString())throw new IllegalArgumentException("exemptName must be string");name=n.getAsString();}
        return new VillagerLobotomySettings(Settings.bool(o,"enabled",d.enabled),name,Settings.number(o,"maintenanceIntervalTicks",d.maintenanceIntervalTicks),Settings.number(o,"wakeAfterTradeTicks",d.wakeAfterTradeTicks),Settings.number(o,"wakeAfterHurtTicks",d.wakeAfterHurtTicks),Settings.bool(o,"idleVillagersEnabled",false),Settings.number(o,"jobSearchMaxIntervalTicks",d.jobSearchMaxIntervalTicks));
    }
}
