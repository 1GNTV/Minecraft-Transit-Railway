package org.mtr.mod.builder;
import java.util.*;

/** Bounded simple polygon, inclusive boundaries, concave containment and ear-clipped fill. */
public final class RegionPolygon {
    public record Point(double x,double z){}
    private static final double EPS=1e-7;
    private final List<Point> points;
    public RegionPolygon(List<Point> vertices){
        if(vertices.size()<3||vertices.size()>64)throw new IllegalArgumentException("Le contour doit contenir 3 à 64 points.");
        points=List.copyOf(vertices);
        for(int i=0;i<points.size();i++){
            Point a=points.get(i),b=points.get((i+1)%points.size());
            if(!Double.isFinite(a.x()+a.z())||Math.abs(a.x())>30000000||Math.abs(a.z())>30000000)throw new IllegalArgumentException("Point hors du monde.");
            if(distanceSquared(a,b)<EPS)throw new IllegalArgumentException("Deux points du contour sont confondus.");
        }
        if(Math.abs(area())<.01)throw new IllegalArgumentException("Le contour est trop petit.");
        for(int i=0;i<points.size();i++)for(int j=i+1;j<points.size();j++){
            if(j==i+1||i==0&&j==points.size()-1)continue;
            if(crosses(points.get(i),points.get((i+1)%points.size()),points.get(j),points.get((j+1)%points.size())))
                throw new IllegalArgumentException("Le contour se croise.");
        }
    }
    public List<Point> points(){return points;}
    private double area(){double sum=0;for(int i=0;i<points.size();i++){Point a=points.get(i),b=points.get((i+1)%points.size());sum+=a.x()*b.z()-b.x()*a.z();}return sum/2;}
    private static double cross(Point a,Point b,Point c){return (b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x());}
    private static double distanceSquared(Point a,Point b){return (a.x()-b.x())*(a.x()-b.x())+(a.z()-b.z())*(a.z()-b.z());}
    private static double segmentDistance(Point p,Point a,Point b){double dx=b.x()-a.x(),dz=b.z()-a.z(),t=Math.max(0,Math.min(1,((p.x()-a.x())*dx+(p.z()-a.z())*dz)/(dx*dx+dz*dz)));return Math.hypot(p.x()-a.x()-dx*t,p.z()-a.z()-dz*t);}
    private static boolean crosses(Point a,Point b,Point c,Point d){
        double abC=cross(a,b,c),abD=cross(a,b,d),cdA=cross(c,d,a),cdB=cross(c,d,b);
        if(((abC>EPS&&abD< -EPS)||(abC< -EPS&&abD>EPS))&&((cdA>EPS&&cdB< -EPS)||(cdA< -EPS&&cdB>EPS)))return true;
        return Math.abs(abC)<EPS&&segmentDistance(c,a,b)<EPS||Math.abs(abD)<EPS&&segmentDistance(d,a,b)<EPS||Math.abs(cdA)<EPS&&segmentDistance(a,c,d)<EPS||Math.abs(cdB)<EPS&&segmentDistance(b,c,d)<EPS;
    }
    public boolean contains(double x,double z,double margin){
        Point p=new Point(x,z);boolean inside=false;double distance=Double.POSITIVE_INFINITY;
        for(int i=0,j=points.size()-1;i<points.size();j=i++){
            Point a=points.get(j),b=points.get(i);distance=Math.min(distance,segmentDistance(p,a,b));
            if((a.z()>z)!=(b.z()>z)&&x<(b.x()-a.x())*(z-a.z())/(b.z()-a.z())+a.x())inside=!inside;
        }
        if(margin>=0)return inside||distance<=margin+EPS;
        return inside&&distance+EPS>=-margin;
    }
    public boolean intersects(RegionPolygon other){
        for(Point p:points)if(other.contains(p.x(),p.z(),0))return true;
        for(Point p:other.points)if(contains(p.x(),p.z(),0))return true;
        for(int i=0;i<points.size();i++)for(int j=0;j<other.points.size();j++)if(crosses(points.get(i),points.get((i+1)%points.size()),other.points.get(j),other.points.get((j+1)%other.points.size())))return true;
        return false;
    }
    public double minX(){return points.stream().mapToDouble(Point::x).min().orElseThrow();}
    public double maxX(){return points.stream().mapToDouble(Point::x).max().orElseThrow();}
    public double minZ(){return points.stream().mapToDouble(Point::z).min().orElseThrow();}
    public double maxZ(){return points.stream().mapToDouble(Point::z).max().orElseThrow();}
    public String encode(){StringJoiner join=new StringJoiner(";");for(Point p:points)join.add(p.x()+","+p.z());return join.toString();}
    public static RegionPolygon decode(String value){
        if(value==null||value.isEmpty())return null;
        if(value.length()>4096)throw new IllegalArgumentException("Contour trop long.");
        List<Point> points=new ArrayList<>();for(String pair:value.split(";",-1)){String[] xy=pair.split(",",-1);if(xy.length!=2)throw new IllegalArgumentException("Contour invalide.");points.add(new Point(Double.parseDouble(xy[0]),Double.parseDouble(xy[1])));}return new RegionPolygon(points);
    }
    public static RegionPolygon rectangle(double x,double z,double X,double Z){return new RegionPolygon(List.of(new Point(x,z),new Point(X,z),new Point(X,Z),new Point(x,Z)));}
    public List<int[]> triangles(){
        List<Integer> remaining=new ArrayList<>();for(int i=0;i<points.size();i++)remaining.add(i);
        List<int[]> result=new ArrayList<>();double sign=Math.signum(area());int guard=points.size()*points.size();
        while(remaining.size()>3&&guard-->0){
            boolean clipped=false;
            for(int i=0;i<remaining.size();i++){
                int a=remaining.get((i+remaining.size()-1)%remaining.size()),b=remaining.get(i),c=remaining.get((i+1)%remaining.size());
                Point A=points.get(a),B=points.get(b),C=points.get(c);
                if(cross(A,B,C)*sign<=EPS)continue;
                boolean occupied=false;for(int p:remaining)if(p!=a&&p!=b&&p!=c){Point P=points.get(p);if(cross(A,B,P)*sign>=-EPS&&cross(B,C,P)*sign>=-EPS&&cross(C,A,P)*sign>=-EPS){occupied=true;break;}}
                if(!occupied){result.add(new int[]{a,b,c});remaining.remove(i);clipped=true;break;}
            }
            if(!clipped){
                for(int i=0;i<remaining.size();i++){Point a=points.get(remaining.get((i+remaining.size()-1)%remaining.size())),b=points.get(remaining.get(i)),c=points.get(remaining.get((i+1)%remaining.size()));if(Math.abs(cross(a,b,c))<EPS){remaining.remove(i);clipped=true;break;}}
                if(!clipped)throw new IllegalStateException("Contour non triangulable.");
            }
        }
        if(remaining.size()==3)result.add(new int[]{remaining.get(0),remaining.get(1),remaining.get(2)});return List.copyOf(result);
    }
    public Point interior(){int[] t=triangles().get(0);Point a=points.get(t[0]),b=points.get(t[1]),c=points.get(t[2]);return new Point((a.x()+b.x()+c.x())/3,(a.z()+b.z()+c.z())/3);}
}
