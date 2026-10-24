package kr.harusarri.neodymium2.core;

/** Walk the horizontal centre line, including intermediate chunks for fast projectiles. */
public final class ChunkTraversal {
    @FunctionalInterface public interface Allowed { boolean test(int x,int z); }
    private ChunkTraversal() {}
    public static double firstBlocked(double x,double z,double dx,double dz,Allowed allowed) {
        if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(dx)||!Double.isFinite(dz))return 0;
        // Minecraft's legal horizontal extent; also prevents integer overflow on malformed data.
        if(Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(x+dx)>30_000_000||Math.abs(z+dz)>30_000_000)return 0;
        int cx=(int)Math.floor(x/16),cz=(int)Math.floor(z/16);
        if(!allowed.test(cx,cz))return 0;
        int sx=dx>0?1:dx<0?-1:0,sz=dz>0?1:dz<0?-1:0;
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?(cx+1)*16d:cx*16d)-x)/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?(cz+1)*16d:cz*16d)-z)/dz;
        double stepx=sx==0?Double.POSITIVE_INFINITY:16/Math.abs(dx);
        double stepz=sz==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
        // More than 4096 crossed chunk boundaries in one tick is invalid operational input.
        for(int n=0;n<4096;n++) {
            double t=Math.min(tx,tz);
            if(t>1)return Double.POSITIVE_INFINITY;
            if(t==1)return allowed.test((int)Math.floor((x+dx)/16),(int)Math.floor((z+dz)/16))?Double.POSITIVE_INFINITY:1;
            boolean crossX=tx<=tz,crossZ=tz<=tx;
            if(crossX){cx+=sx;tx+=stepx;}
            if(crossZ){cz+=sz;tz+=stepz;}
            if(!allowed.test(cx,cz))return Math.max(0,t);
        }
        return 0;
    }
}
