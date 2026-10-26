package kr.harusarri.neodymium2.core;

import java.util.*;

/** Immutable BVH: storage is proportional to regions, never their surface/volume. */
public final class SpatialIndex {
    public static final byte CHUNK_OUTSIDE = 0;
    public static final byte CHUNK_MIXED = 1;
    public static final byte CHUNK_FULL_VANILLA = 2;

    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Box {
            if (!Double.isFinite(minX+minY+minZ+maxX+maxY+maxZ) || minX>maxX || minY>maxY || minZ>maxZ)
                throw new IllegalArgumentException("Invalid region bounds");
        }
        public boolean intersects(Box b) { return minX<=b.maxX && maxX>=b.minX && minY<=b.maxY && maxY>=b.minY && minZ<=b.maxZ && maxZ>=b.minZ; }
        public Box expand(double d) { return new Box(minX-d,minY-d,minZ-d,maxX+d,maxY+d,maxZ+d); }
        Box union(Box b) { return new Box(Math.min(minX,b.minX),Math.min(minY,b.minY),Math.min(minZ,b.minZ),Math.max(maxX,b.maxX),Math.max(maxY,b.maxY),Math.max(maxZ,b.maxZ)); }
        double center(int axis) { return axis==0?(minX+maxX)/2:axis==1?(minY+maxY)/2:(minZ+maxZ)/2; }
    }
    private record Node(Box bounds,Node left,Node right,Box[] leaves,long[] chunkCache) {
        boolean intersects(Box q) {
            if (!bounds.intersects(q)) return false;
            if (leaves!=null) { for (Box b:leaves) if (b.intersects(q)) return true; return false; }
            return left.intersects(q)||right.intersects(q);
        }
        boolean intersects(double x,double y,double z,double xx,double yy,double zz) {
            if(!hit(bounds,x,y,z,xx,yy,zz))return false;
            if(leaves!=null){for(Box b:leaves)if(hit(b,x,y,z,xx,yy,zz))return true;return false;}
            return left.intersects(x,y,z,xx,yy,zz)||right.intersects(x,y,z,xx,yy,zz);
        }
        byte classifyChunk(double x0,double z0,double x1,double z1) {
            if(bounds.minX>x1||bounds.maxX<x0||bounds.minZ>z1||bounds.maxZ<z0)return CHUNK_OUTSIDE;
            if(leaves!=null) {
                byte state=CHUNK_OUTSIDE;
                for(Box b:leaves) {
                    if(b.minX<=x1&&b.maxX>=x0&&b.minZ<=z1&&b.maxZ>=z0) {
                        if(b.minX<=x0&&b.maxX>=x1&&b.minZ<=z0&&b.maxZ>=z1&&b.minY<=-64.0&&b.maxY>=319.0)return CHUNK_FULL_VANILLA;
                        state=CHUNK_MIXED;
                    }
                }
                return state;
            }
            byte l=left.classifyChunk(x0,z0,x1,z1);if(l==CHUNK_FULL_VANILLA)return CHUNK_FULL_VANILLA;
            byte r=right.classifyChunk(x0,z0,x1,z1);return l>=r?l:r;
        }
        byte chunkState(int cx,int cz) {
            double x0=cx*16.0,z0=cz*16.0;
            if(bounds.minX>x0+16.0||bounds.maxX<x0||bounds.minZ>z0+16.0||bounds.maxZ<z0)return CHUNK_OUTSIDE;
            long tag=(1L<<62)|((cx&0x1FFFFFFFL)<<31)|((cz&0x1FFFFFFFL)<<2);
            int slot=(int)((cx*0x9E3779B97F4A7C15L^cz*0xBF58476D1CE4E5B9L)>>>50);
            long entry=chunkCache[slot];
            if((entry&~3L)==tag)return (byte)(entry&3L);
            byte state=classifyChunk(x0,z0,x0+16.0,z0+16.0);
            chunkCache[slot]=tag|(state&3L);
            return state;
        }
    }
    private static boolean hit(Box b,double x,double y,double z,double xx,double yy,double zz){return b.minX<=xx&&b.maxX>=x&&b.minY<=yy&&b.maxY>=y&&b.minZ<=zz&&b.maxZ>=z;}
    private final Map<String,Node> dimensions;
    private final int size;
    public SpatialIndex(Map<String,List<Box>> input) {
        Map<String,Node> result=new HashMap<>();int count=0;
        for(var e:input.entrySet()) {
            count+=e.getValue().size(); if(count>4096) throw new IllegalArgumentException("Too many protected regions (limit 4096)");
            if(!e.getValue().isEmpty())result.put(e.getKey(),build(new ArrayList<>(e.getValue()),true));
        }
        dimensions=Map.copyOf(result);size=count;
    }
    private static Node build(List<Box> boxes,boolean root) {
        Box bounds=boxes.getFirst();for(Box b:boxes)bounds=bounds.union(b);
        long[] cache=root?new long[16384]:null;
        if(boxes.size()<=6)return new Node(bounds,null,null,boxes.toArray(Box[]::new),cache);
        double x=bounds.maxX-bounds.minX,y=bounds.maxY-bounds.minY,z=bounds.maxZ-bounds.minZ;int axis=x>=y&&x>=z?0:y>=z?1:2;
        boxes.sort(Comparator.comparingDouble(b->b.center(axis)));int split=boxes.size()/2;
        return new Node(bounds,build(new ArrayList<>(boxes.subList(0,split)),false),build(new ArrayList<>(boxes.subList(split,boxes.size())),false),null,cache);
    }
    public byte chunkState(String dimension,int chunkX,int chunkZ) {
        Node n=dimensions.get(dimension);return n==null?CHUNK_OUTSIDE:n.chunkState(chunkX,chunkZ);
    }
    public boolean contains(String dimension,Box bounds) { Node n=dimensions.get(dimension);return n!=null&&n.intersects(bounds); }
    public boolean contains(String d,double x,double y,double z,double xx,double yy,double zz) {
        Node n=dimensions.get(d);if(n==null)return false;
        int cx0=(int)Math.floor(x)>>4,cx1=(int)Math.floor(xx)>>4,cz0=(int)Math.floor(z)>>4,cz1=(int)Math.floor(zz)>>4;
        if(cx0==cx1&&cz0==cz1) {
            byte cs=n.chunkState(cx0,cz0);
            if(cs==CHUNK_OUTSIDE)return false;
            if(cs==CHUNK_FULL_VANILLA&&y>=-64.0&&yy<=319.0)return true;
        }
        return n.intersects(x,y,z,xx,yy,zz);
    }
    public int size(){return size;}
}
