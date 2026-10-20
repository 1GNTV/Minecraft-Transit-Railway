package org.mtr.mod.decor;
import java.util.*;

/** Integer anchor repetition leaves original materials and textures at their natural scale. */
public final class DecorPlacement {
    public record Offset(int x,int y,int z) {}
    public static List<Offset> repeat(int count,int axis,int step){
        count=Math.max(1,Math.min(count,32));step=Math.max(1,Math.min(step,16));axis=Math.max(0,Math.min(2,axis));
        var out=new ArrayList<Offset>();for(int i=1;i<=count;i++){int d=i*step;if(d>48)break;out.add(new Offset(axis==0?d:0,axis==1?d:0,axis==2?d:0));}return List.copyOf(out);
    }
    public static boolean validBox(int dx,int dy,int dz){return dx>=0&&dy>=0&&dz>=0&&dx<=48&&dy<=48&&dz<=48&&(long)(dx+1)*(dy+1)*(dz+1)<=32768;}
}
