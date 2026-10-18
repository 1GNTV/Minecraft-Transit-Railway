package org.mtr.mod.client;
import org.mtr.core.data.ClientData;
import org.mtr.mod.data.SillonSignageNetworkSnapshot;
import org.mtr.mod.data.SillonSignageNetworkSnapshot.*;
import java.util.*;

/** Core-data adapter shared by the client and integration checks; no Minecraft renderer dependency. */
public final class SillonSignageMtrData {
    private SillonSignageMtrData(){}
    private static Point point(org.mtr.core.data.Position p){return new Point(p.getX(),p.getY(),p.getZ());}
    private static String name(String s){return SillonSignageAutomatic.primary(s);}
    private static String detail(String s){String[] p=s==null?new String[0]:s.split("\\|",-1);return p.length>1?p[1].strip():"";}
    private static String mode(org.mtr.core.data.Route r){
        String label=(r.getName()+" "+r.getRouteNumber()).toLowerCase(Locale.ROOT);
        if(label.matches(".*\\brer\\b.*"))return "rer";
        if(label.matches(".*\\bbus\\b.*"))return "bus";
        if(label.matches(".*\\b(transilien|ter|tgv|train)\\b.*"))return "train";
        return switch(r.getTransportMode()){
            case BOAT->"boat";case CABLE_CAR->"cable";case AIRPLANE->"air";
            default->r.getRouteType()==org.mtr.core.data.RouteType.LIGHT_RAIL?"tram":r.getRouteType()==org.mtr.core.data.RouteType.HIGH_SPEED?"train":"metro";
        };
    }
    public static SillonSignageNetworkSnapshot build(ClientData data,ClientData live){
        List<Station> stations=new ArrayList<>();List<Platform> platforms=new ArrayList<>();List<Line> lines=new ArrayList<>();List<Service> services=new ArrayList<>();
        for(var s:data.stations){
            List<Exit> exits=new ArrayList<>();for(var e:s.getExits()){
                List<String> dest=e.getDestinations();String text=dest.isEmpty()?"Sortie "+e.getName():name(dest.get(0));
                exits.add(new Exit(e.getName(),text,dest.size()>1?String.join(" · ",dest.subList(1,dest.size())):""));
            }
            exits.sort(Comparator.comparing(Exit::number,org.mtr.mod.data.SillonSignageNetworkOrdering::compare));
            stations.add(new Station(s.getId(),name(s.getName()),detail(s.getName()),point(s.getCenter()),exits,s.connectedStations.stream().map(x->x.getId()).toList()));
        }
        for(var p:data.platforms)platforms.add(new Platform(p.getId(),p.area==null?0:p.area.getId(),name(p.getName()),point(p.getMidPosition())));
        for(var r:data.routes)if(!r.getHidden()){
            List<Stop> stops=new ArrayList<>();int i=0;
            for(var entry:r.getRoutePlatforms()){
                var p=entry.platform;if(p!=null&&p.area!=null)stops.add(new Stop(p.area.getId(),p.getId(),name(p.area.getName()),name(r.getDestination(i))));i++;
            }
            String label=name(r.getRouteNumber());if(label.isBlank())label=name(r.getName());
            if(!stops.isEmpty())lines.add(new Line(r.getId(),label,mode(r),r.getColor(),stops));
        }
        if(lines.isEmpty())for(var r:live.simplifiedRoutes){
            List<Stop> stops=r.getPlatforms().stream().map(p->new Stop(p.getStationId(),p.getPlatformId(),name(p.getStationName()),name(p.getDestination()))).toList();
            lines.add(new Line(r.getId(),name(r.getName()),"metro",r.getColor(),stops));
        }
        for(var lift:live.lifts)lift.iterateFloors(floor->{
            for(var s:data.stations)if(s.inArea(floor.getPosition())){
                String description=name(floor.getDescription());if(description.isBlank())description=name(floor.getNumber());
                services.add(new Service(s.getId(),"lift","Ascenseur",description,"sncf_ascenseur,idfm_pmr",point(floor.getPosition())));break;
            }
        });
        return new SillonSignageNetworkSnapshot(stations,platforms,lines,services);
    }
}
