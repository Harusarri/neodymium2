import kr.harusarri.neodymium2.NeodymiumI18n;
import kr.harusarri.neodymium2.core.*;
import java.util.*;
public class VillagerConfigTests {
 public static void main(String[] args){int n=0;
  if(Settings.parse("{}").villagerLobotomy().enabled())throw new AssertionError("legacy opt-in");n++;
  if(Settings.parse("{\"villagerLobotomy\":{\"enabled\":true}}").villagerLobotomy().idleVillagersEnabled())throw new AssertionError("idle extension must be explicitly enabled");n++;
  var ext=Settings.parse("{\"villagerLobotomy\":{\"enabled\":true,\"idleVillagersEnabled\":true,\"jobSearchMaxIntervalTicks\":400}}").villagerLobotomy();if(!ext.idleVillagersEnabled()||ext.jobSearchMaxIntervalTicks()!=400)throw new AssertionError("extended config");n++;
  if(!Settings.parse("{\"villagerLobotomy\":{\"enabled\":true}}").villagerLobotomy().exemptName().equals("춘식이"))throw new AssertionError();n++;
  for(String s:List.of("{\"enabled\":\"true\"}","{\"exemptName\":\"\"}","{\"exemptName\":12}","{\"maintenanceIntervalTicks\":0}","{\"maintenanceIntervalTicks\":101}","{\"wakeAfterTradeTicks\":-1}","{\"wakeAfterHurtTicks\":0}","{\"unknown\":true}","null","true")){
   try{Settings.parse("{\"villagerLobotomy\":"+s+"}");throw new AssertionError(s);}catch(RuntimeException expected){n++;}
  }
  if(!Settings.parse(Settings.defaults().json()).equals(Settings.defaults()))throw new AssertionError("roundtrip");n++;
  if(NeodymiumI18n.englishMap().isEmpty()||!NeodymiumI18n.englishMap().keySet().equals(NeodymiumI18n.koreanMap().keySet()))throw new AssertionError("lang keys mismatch");n++;
  Settings.parse("{\"language\":\"en_us\"}");if(!NeodymiumI18n.EN_US.equals(NeodymiumI18n.mode()))throw new AssertionError("lang mode en_us");n++;
  Settings.parse("{\"language\":\"auto\"}");if(!NeodymiumI18n.AUTO.equals(NeodymiumI18n.mode()))throw new AssertionError("lang mode auto");n++;
  System.out.println("NEO_VILLAGER_CONFIG_PASS checks="+n);
 }
}
