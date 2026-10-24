import kr.harusarri.neodymium2.core.*;
import java.util.*;
public class ChunkTraversalTests {
 static int checks;
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static void close(double a,double b){check(a==b||Math.abs(a-b)<1e-10,"fractions "+a+" vs "+b);}
 public static void main(String[] args){
  var old=Settings.parse("{\"enabled\":true}");check(!old.lazyChunkProjectileCleanup(),"upgrade opt-in");
  check(Settings.parse("{\"lazyChunkProjectileCleanup\":true}").lazyChunkProjectileCleanup(),"toggle parse");
  try{Settings.parse("{\"lazyChunkProjectileCleanup\":\"true\"}");throw new AssertionError();}catch(IllegalArgumentException ex){checks++;}
  close(ChunkTraversal.firstBlocked(8,8,16,0,(x,z)->x==0),.5);
  close(ChunkTraversal.firstBlocked(-8,-8,-16,0,(x,z)->x==-1),.5);
  check(Double.isInfinite(ChunkTraversal.firstBlocked(8,8,-8,-0.0,(x,z)->x==0&&z==0)),"negative endpoint stays in chunk");
  close(ChunkTraversal.firstBlocked(8,8,8,0,(x,z)->x==0),1);
  close(ChunkTraversal.firstBlocked(0,8,-1,0,(x,z)->x==0),0);
  check(Double.isInfinite(ChunkTraversal.firstBlocked(8,8,-0.0,-0.0,(x,z)->true)),"signed zero");
  close(ChunkTraversal.firstBlocked(8,8,80,0,(x,z)->x!=2),.3);
  close(ChunkTraversal.firstBlocked(8,8,Double.NaN,0,(x,z)->true),0);
  close(ChunkTraversal.firstBlocked(8,8,70000,0,(x,z)->true),0);
  Random random=new Random(29301);
  // Independent segment/slab reference for a single forbidden chunk.
  for(int n=0;n<20000;n++){
   double x=random.nextDouble()*160-80,z=random.nextDouble()*160-80,dx=random.nextDouble()*320-160,dz=random.nextDouble()*320-160;
   int bx=random.nextInt(15)-7,bz=random.nextInt(15)-7;
   double a=Math.max(0,Math.max(Math.min((bx*16d-x)/dx,((bx+1)*16d-x)/dx),Math.min((bz*16d-z)/dz,((bz+1)*16d-z)/dz)));
   double b=Math.min(1,Math.min(Math.max((bx*16d-x)/dx,((bx+1)*16d-x)/dx),Math.max((bz*16d-z)/dz,((bz+1)*16d-z)/dz)));
   close(ChunkTraversal.firstBlocked(x,z,dx,dz,(cx,cz)->cx!=bx||cz!=bz),a<=b?a:Double.POSITIVE_INFINITY);
  }
  System.out.println("NEO_TRAVERSAL_PASS checks="+checks);
 }
}
