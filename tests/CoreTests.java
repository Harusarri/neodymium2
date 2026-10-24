import kr.harusarri.neodymium2.RegionPolicies;
import kr.harusarri.neodymium2.api.NeodymiumApi;
import kr.harusarri.neodymium2.api.NeodymiumRegionProvider;
import kr.harusarri.neodymium2.core.*;
import java.util.*;
import java.nio.file.*;
public class CoreTests {
 static int checks;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 static void reject(String json){try{Settings.parse(json);throw new AssertionError("accepted "+json);}catch(RuntimeException expected){checks++;}}
 public static void main(String[] args)throws Exception {
  check(Settings.parse(Settings.defaults().json()).equals(Settings.defaults()),"round trip");
  for(String s:List.of("null","{","{\"enabled\":\"true\"}","{\"activationRange\":0}","{\"activationRange\":24.5}","{\"inactiveTickInterval\":201}","{\"regionMarginBlocks\":33}","{\"schemaVersion\":2}","{\"level\":\"high\"}","{\"reclaimEntityTypes\":[\"minecraft:item\"]}","{\"enabled\":true} trailing"))reject(s);
  Random r=new Random(8243);var boxes=new ArrayList<SpatialIndex.Box>();
  for(int i=0;i<4096;i++){double x=r.nextInt(20000)-10000,y=r.nextInt(384)-64,z=r.nextInt(20000)-10000;boxes.add(new SpatialIndex.Box(x,y,z,x+r.nextInt(200),y+r.nextInt(100),z+r.nextInt(200)));}
  var index=new SpatialIndex(Map.of("test:world",boxes));
  for(int i=0;i<25000;i++){double x=r.nextInt(20000)-10000,y=r.nextInt(384)-64,z=r.nextInt(20000)-10000;var q=new SpatialIndex.Box(x,y,z,x+1,y+2,z+1);boolean brute=boxes.stream().anyMatch(b->b.intersects(q));check(index.contains("test:world",q)==brute,"BVH differential "+i);check(index.contains("test:world",q.minX(),q.minY(),q.minZ(),q.maxX(),q.maxY(),q.maxZ())==brute,"chunk-cached contains "+i);check(!index.contains("test:other",q),"dimension isolation");}
  var one=new SpatialIndex(Map.of("test:world",List.of(new SpatialIndex.Box(-10,-64,-10,0,0,0),new SpatialIndex.Box(32,-64,32,48,320,48))));check(one.contains("test:world",0,0,0,1,1,1),"inclusive boundary");check(!one.contains("test:world",1,1,1,2,2,2),"outside boundary");
  check(one.chunkState("test:world",2,2)==SpatialIndex.CHUNK_FULL_VANILLA,"full vanilla chunk classification");
  check(one.chunkState("test:world",-1,-1)==SpatialIndex.CHUNK_MIXED,"mixed chunk classification");
  check(one.chunkState("test:world",10,10)==SpatialIndex.CHUNK_OUTSIDE,"outside chunk classification");
  for(String s:List.of("{\"outsideSpawnBaseInterval\":0}","{\"outsideSpawnBaseInterval\":21}","{\"outsideSpawnBaseInterval\":8,\"outsideSpawnMaxInterval\":4}","{\"outsideSpawnMaxInterval\":81}"))reject(s);
  var cRef=RegionPolicies.Ref.ofChunk("minecraft:overworld",3,-2);
  var rRef=RegionPolicies.Ref.ofChunkRange("minecraft:overworld",10,10,12,12);
  check(cRef.isStandaloneChunk()&&cRef.coversChunk("minecraft:overworld",3,-2)&&!cRef.coversChunk("minecraft:overworld",4,-2),"standalone chunk ref");
  check(rRef.isStandaloneChunk()&&rRef.coversChunk("minecraft:overworld",11,11)&&!rRef.coversChunk("minecraft:overworld",9,11),"standalone chunk range ref");
  var standaloneIdx=RegionPolicies.compile(Set.of(cRef,rRef),0);
  check(standaloneIdx.chunkState("minecraft:overworld",3,-2)==SpatialIndex.CHUNK_FULL_VANILLA,"standalone chunk compiles to full vanilla");
  check(standaloneIdx.chunkState("minecraft:overworld",11,11)==SpatialIndex.CHUNK_FULL_VANILLA,"standalone chunk range compiles to full vanilla");
  check(standaloneIdx.chunkState("minecraft:overworld",0,0)==SpatialIndex.CHUNK_OUTSIDE,"unprotected chunk remains outside");
  NeodymiumApi.registerRegionProvider((dim,id)->"ext_farm".equals(id)?new NeodymiumRegionProvider.ResolvedRegion(dim,id,"외부 농장",new SpatialIndex.Box(160,-64,160,176,320,176),168,80,168,"test"):null);
  var extIdx=RegionPolicies.compile(Set.of(cRef,new RegionPolicies.Ref("minecraft:overworld","ext_farm")),0);
  check(extIdx.chunkState("minecraft:overworld",10,10)==SpatialIndex.CHUNK_FULL_VANILLA,"external provider region compiles into SpatialIndex");
  NeodymiumApi.registerRegionProvider(null);
  var tmp=Files.createTempDirectory("neodymium2-unit-");var f=tmp.resolve("regions.json");AtomicFiles.write(f,"old");AtomicFiles.write(f,"new");check(Files.readString(f).equals("new"),"atomic replace");try{AtomicFiles.read(f,1);throw new AssertionError();}catch(java.io.IOException expected){checks++;}try(var files=Files.list(tmp)){check(files.count()==1,"no temp residue");}Files.delete(f);Files.delete(tmp);
  System.out.println("NEO_CORE_PASS checks="+checks);
 }
}
