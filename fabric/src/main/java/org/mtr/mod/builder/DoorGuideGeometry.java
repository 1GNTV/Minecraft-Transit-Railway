package org.mtr.mod.builder;
import java.util.*;
public final class DoorGuideGeometry {
    public record Point(double x,double z){}
    private DoorGuideGeometry(){}
    public static List<Point> clip(List<Point> polygon,double x,double z){
        List<Point> r=polygon;for(int edge=0;edge<4;edge++){List<Point> out=new ArrayList<>();if(r.isEmpty())return out;Point prev=r.get(r.size()-1);for(Point cur:r){boolean a=inside(prev,edge,x,z),b=inside(cur,edge,x,z);if(a!=b)out.add(intersect(prev,cur,edge,x,z));if(b)out.add(cur);prev=cur;}r=out;}return r;
    }
    private static boolean inside(Point p,int edge,double x,double z){return switch(edge){case 0->p.x>=x;case 1->p.x<=x+1;case 2->p.z>=z;default->p.z<=z+1;};}
    private static Point intersect(Point a,Point b,int edge,double x,double z){boolean vertical=edge<2;double boundary=vertical?x+(edge==1?1:0):z+(edge==3?1:0),start=vertical?a.x:a.z,end=vertical?b.x:b.z,t=(boundary-start)/(end-start);return new Point(a.x+(b.x-a.x)*t,a.z+(b.z-a.z)*t);}
    public static double area(List<Point> points){double a=0;for(int i=0;i<points.size();i++){var p=points.get(i);var q=points.get((i+1)%points.size());a+=p.x*q.z-q.x*p.z;}return Math.abs(a)/2;}
}
