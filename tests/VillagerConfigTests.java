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
  System.out.println("NEO_VILLAGER_CONFIG_PASS checks="+n);
 }
}
