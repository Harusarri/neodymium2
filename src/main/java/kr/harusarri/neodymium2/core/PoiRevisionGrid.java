package kr.harusarri.neodymium2.core;

/** Bounded, conservative change detector. Hash collisions only cause an extra retry. */
public final class PoiRevisionGrid {
    private final long[] revisions=new long[1024];
    private static int bucket(int x,int z){return (x*73428767^z*912931)&1023;}
    public void changed(int blockX,int blockZ){revisions[bucket(blockX>>6,blockZ>>6)]++;}
    public long near(int blockX,int blockZ){
        int x=blockX>>6,z=blockZ>>6;long sum=0;
        // Native AcquirePoi scans 48 blocks, contained within these nine 64-block cells.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)sum+=revisions[bucket(x+dx,z+dz)];
        return sum;
    }
}
