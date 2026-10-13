package org.mtr.mod.client;

import org.mtr.core.data.RailMath;
import java.util.*;

/**
 * Immutable samples visible from an exact camera/view. Only the distance and
 * camera-direction checks are cached; the actual callbacks still run each
 * shadow/main pass to preserve lighting, moving trains and editor previews.
 * Render-thread owned. Invalidate when rails are re-synchronized.
 */
public final class RailVisibilityCache {
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_DOUBLES = 200_000;
    private static final int MAX_ENTRY_DOUBLES = 40_000;
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.engine.visibleRails", "true"));
    private final LinkedHashMap<Key, double[]> cached = new LinkedHashMap<>(64, .75f, true);
    private int storedDoubles;
    private boolean hasView;
    private double vx, vy, vz, nx, ny, nz;
    private int distance;
    private long hits, misses, uncached;
    private static final class Key {
        final RailMath rail;
        final double interval;
        final float r1, r2;
        final int hash;
        Key(RailMath rail,double interval,float r1,float r2) {
            this.rail=rail;this.interval=interval;this.r1=r1;this.r2=r2;
            int value=System.identityHashCode(rail);
            value=31*value+Double.hashCode(interval);
            value=31*value+Float.hashCode(r1);
            hash=31*value+Float.hashCode(r2);
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other) {
            if(!(other instanceof Key key))return false;
            return rail==key.rail && Double.doubleToLongBits(interval)==Double.doubleToLongBits(key.interval)
                && Float.floatToIntBits(r1)==Float.floatToIntBits(key.r1)
                && Float.floatToIntBits(r2)==Float.floatToIntBits(key.r2);
        }
    }
    private static final class Samples {
        double[] values = new double[160];
        int count;
        boolean overflow;
        void append(double x1,double z1,double x2,double z2,double x3,double z3,double x4,double z4,double y1,double y2) {
            if(overflow)return;
            if(count+10>MAX_ENTRY_DOUBLES){overflow=true;values=null;return;}
            if(count+10>values.length)values=Arrays.copyOf(values,Math.min(MAX_ENTRY_DOUBLES,values.length*2));
            values[count++]=x1;values[count++]=z1;values[count++]=x2;values[count++]=z2;
            values[count++]=x3;values[count++]=z3;values[count++]=x4;values[count++]=z4;
            values[count++]=y1;values[count++]=y2;
        }
        double[] done(){return overflow?null:Arrays.copyOf(values,count);}
    }
    /**
     * Return an immutable array of exactly visible geometry samples, retaining
     * the SAME array identity until the camera, direction, view distance, rail
     * or geometry registry changes. A null result means use the original
     * callback path (cache disabled, or sample budget exceeded).
     *
     * Allows the rail submitter to memoize prepared transforms per visible
     * rail rather than recreate the segment callback chain every shader pass.
     */
    public double[] visibleSamples(RailMath rail, RailGeometryCache geometry,
                                   double interval,float r1,float r2,double x,double y,double z,int renderDistance,
                                   double normalX,double normalY,double normalZ) {
        if (!ENABLED) return null;
        if (!hasView || x!=vx || y!=vy || z!=vz || normalX!=nx || normalY!=ny || normalZ!=nz || renderDistance!=distance) {
            cached.clear();storedDoubles=0;hasView=true;
            vx=x;vy=y;vz=z;nx=normalX;ny=normalY;nz=normalZ;distance=renderDistance;
        }
        Key key=new Key(rail,interval,r1,r2);
        double[] existing=cached.get(key);
        if(existing!=null) {
            hits++;
            return existing;
        }
        misses++;
        Samples samples=new Samples();
        filter(rail,geometry,(x1,z1,x2,z2,x3,z3,x4,z4,y1,y2)->samples.append(x1,z1,x2,z2,x3,z3,x4,z4,y1,y2),
               null,interval,r1,r2,x,y,z,renderDistance,normalX,normalY,normalZ);
        double[] visible=samples.done();
        if(visible==null){uncached++;return null;}
        if(visible.length>MAX_DOUBLES)return null;
        cached.put(key,visible);storedDoubles+=visible.length;
        Iterator<double[]> it=cached.values().iterator();
        while(cached.size()>MAX_ENTRIES || storedDoubles>MAX_DOUBLES){
            storedDoubles-=it.next().length;
            it.remove();
        }
        return visible;
    }

    /** Existing callback API retains exact lighting/editor semantics. */
    public void render(RailMath rail, RailGeometryCache geometry, RailMath.RenderRail draw,
                       double interval,float r1,float r2,double x,double y,double z,int renderDistance,
                       double normalX,double normalY,double normalZ) {
        double[] visible=visibleSamples(rail,geometry,interval,r1,r2,x,y,z,renderDistance,normalX,normalY,normalZ);
        if(visible==null) {
            filter(rail,geometry,draw,null,interval,r1,r2,x,y,z,renderDistance,normalX,normalY,normalZ);
            return;
        }
        for(int i=0;i<visible.length;i+=10) {
            draw.renderRail(visible[i],visible[i+1],visible[i+2],visible[i+3],visible[i+4],
                            visible[i+5],visible[i+6],visible[i+7],visible[i+8],visible[i+9]);
        }
    }
    private static void filter(RailMath rail,RailGeometryCache geometry,RailMath.RenderRail draw,Samples samples,
                               double interval,float r1,float r2,double x,double y,double z,int radius,
                               double normalX,double normalY,double normalZ){
        final double limit=(double)radius*radius;
        geometry.render(rail,(x1,z1,x2,z2,x3,z3,x4,z4,y1,y2)->{
            double dx=x1-x,dz=z1-z,dist=dx*dx+dz*dz;
            if(dist<=limit && (dist<1024 || dx*normalX+(y1-y)*normalY+dz*normalZ>-0.00001)) {
                draw.renderRail(x1,z1,x2,z2,x3,z3,x4,z4,y1,y2);
                if(samples!=null)samples.append(x1,z1,x2,z2,x3,z3,x4,z4,y1,y2);
            }
        },interval,r1,r2);
    }
    public long hits(){return hits;}
    public long misses(){return misses;}
    public long uncached(){return uncached;}
    public void clear(){cached.clear();storedDoubles=0;hasView=false;hits=misses=uncached=0;}
}
