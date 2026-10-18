package org.mtr.mod.data;
import java.util.*;

/** The complete input of the automatic compositor, independent of Minecraft rendering. */
public record SillonSignageNetworkSnapshot(List<Station> stations,List<Platform> platforms,List<Line> lines,List<Service> services) {
    public record Point(double x,double y,double z){public double distance(Point p){return Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2);}}
    public record Exit(String number,String text,String detail){}
    public record Station(long id,String name,String detail,Point center,List<Exit> exits,List<Long> connected){
        public Station{exits=List.copyOf(exits);connected=List.copyOf(connected);}}
    public record Platform(long id,long stationId,String name,Point center){}
    public record Stop(long stationId,long platformId,String name,String destination){}
    public record Line(long id,String label,String mode,int color,List<Stop> stops){public Line{stops=List.copyOf(stops);}}
    public record Service(long stationId,String kind,String name,String detail,String icons,Point position){}
    public SillonSignageNetworkSnapshot{stations=List.copyOf(stations);platforms=List.copyOf(platforms);lines=List.copyOf(lines);services=List.copyOf(services);}
    public static final SillonSignageNetworkSnapshot EMPTY=new SillonSignageNetworkSnapshot(List.of(),List.of(),List.of(),List.of());
}
