package org.mtr.mod.render;

import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.client.IDrawing;
import java.util.Arrays;

/**
 * Packs vanilla/classic opaque rail quads into bounded callbacks. Geometry is
 * captured as primitive coordinates and never as per-segment Java lambdas.
 *
 * The current frame still samples light and selects visible segments; animated
 * previews/signals stay on the original renderer. Drawing uses the original
 * IDrawing path and render layer, so Oculus receives the same textured faces.
 *
 * This is an intermediate CPU batch; it does NOT claim static GPU residency.
 */
public final class ClassicRailQuadBatch {
    public static final boolean ENABLED=!"false".equalsIgnoreCase(
        System.getProperty("mtr.engine.classicRailBatch", "true"));
    private static final int MAX_QUADS=256;
    private final Identifier texture;
    private final int color;
    private final double yOffset,smallOffset;
    private final float u1,v1,u2,v2;
    private double[] coordinates=new double[10*16];
    private int[] lights=new int[16];
    private float[] stripeOffsets=new float[16];
    private int count;

    public ClassicRailQuadBatch(Identifier texture,int color,double yOffset,double smallOffset,
                                float u1,float v1,float u2,float v2) {
        this.texture=texture;this.color=color;this.yOffset=yOffset;this.smallOffset=smallOffset;
        this.u1=u1;this.v1=v1;this.u2=u2;this.v2=v2;
    }

    public void add(double x1,double z1,double x2,double z2,
                    double x3,double z3,double x4,double z4,double y1,double y2,int light) {
        if(count==MAX_QUADS)flush();
        if(count==lights.length){
            int capacity=Math.min(MAX_QUADS,lights.length*2);
            coordinates=Arrays.copyOf(coordinates,capacity*10);
            lights=Arrays.copyOf(lights,capacity);
            stripeOffsets=Arrays.copyOf(stripeOffsets,capacity);
        }
        int index=count*10;
        coordinates[index]=x1;coordinates[index+1]=z1;
        coordinates[index+2]=x2;coordinates[index+3]=z2;
        coordinates[index+4]=x3;coordinates[index+5]=z3;
        coordinates[index+6]=x4;coordinates[index+7]=z4;
        coordinates[index+8]=y1;coordinates[index+9]=y2;
        lights[count]=light;
        stripeOffsets[count]=(((int)(x1+z1))%4)*0.25F;
        count++;
    }

    /** Must be called after the final segment, before render queue submission. */
    public void flush(){
        if(count==0)return;
        final int length=count;
        final double[] coords=Arrays.copyOf(coordinates,length*10);
        final int[] levels=Arrays.copyOf(lights,length);
        final float[] stripe=Arrays.copyOf(stripeOffsets,length);
        final float uu1=u1<0?0:u1,uu2=u2<0?1:u2;
        MainRenderer.scheduleRender(texture,false,QueuedRenderLayer.EXTERIOR,(graphicsHolder,offset)->{
            for(int i=0;i<length;i++){
                int j=i*10;
                double x1=coords[j],z1=coords[j+1],x2=coords[j+2],z2=coords[j+3];
                double x3=coords[j+4],z3=coords[j+5],x4=coords[j+6],z4=coords[j+7];
                double y1=coords[j+8]+yOffset,y2=coords[j+9]+yOffset;
                float vv1=v1<0?0.1875F+stripe[i]:v1;
                float vv2=v2<0?0.3125F+stripe[i]:v2;
                IDrawing.drawTexture(graphicsHolder,x1,y1,z1,x2,y1+smallOffset,z2,
                    x3,y2,z3,x4,y2+smallOffset,z4,offset,
                    uu1,vv1,uu2,vv2,Direction.UP,color,levels[i]);
                IDrawing.drawTexture(graphicsHolder,x2,y1+smallOffset,z2,x1,y1,z1,
                    x4,y2+smallOffset,z4,x3,y2,z3,offset,
                    uu1,vv1,uu2,vv2,Direction.UP,color,levels[i]);
            }
        });
        count=0;
    }
}
