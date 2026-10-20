package org.mtr.mod.builder;

import java.util.*;

/** Screen-space selection and label placement, independent of Minecraft rendering. */
public final class MapGeometry {
    public record Rect(double x,double y,double w,double h){
        public boolean overlaps(Rect o){return x<o.x+o.w&&x+w>o.x&&y<o.y+o.h&&y+h>o.y;}
        public boolean contains(double px,double py){return px>=x&&px<=x+w&&py>=y&&py<=y+h;}
    }
    public record Hit(long id,double x,double y,double distance){}
    public static double zoom(double scale,double amount){return Math.max(1/128D,Math.min(64,scale*Math.pow(1.25,amount)));}
    public static double anchoredCenter(double center,double screenOffset,double before,double after){return center+screenOffset/before-screenOffset/after;}
    public static Rect place(double x,double y,int width,int height,List<Rect> used,Rect viewport){
        for(int[] p:new int[][]{{9,-8},{9,8},{-width-9,-8},{-width-9,8},{-width/2,-height-12},{-width/2,14}}){
            Rect r=new Rect(x+p[0],y+p[1],width,height);
            if(r.x<viewport.x||r.y<viewport.y||r.x+r.w>viewport.x+viewport.w||r.y+r.h>viewport.y+viewport.h)continue;
            if(used.stream().noneMatch(r::overlaps)){used.add(r);return r;}
        }return null;
    }
    public static double segmentDistance(double px,double py,double x1,double y1,double x2,double y2){double dx=x2-x1,dy=y2-y1,len=dx*dx+dy*dy,t=len==0?0:Math.max(0,Math.min(1,((px-x1)*dx+(py-y1)*dy)/len));return Math.hypot(px-x1-t*dx,py-y1-t*dy);}
    private MapGeometry(){}
}
