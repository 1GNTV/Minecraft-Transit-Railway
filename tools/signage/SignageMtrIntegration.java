import org.mtr.core.data.*;
import org.mtr.core.tool.Angle;
import org.mtr.core.operation.ListDataResponse;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.client.*;
import org.mtr.mod.data.*;
import java.util.*;

/** Exercises the production adapter against the assembled MTR core, including dashboard serialization. */
public final class SignageMtrIntegration {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    public static void main(String[] args){
        ClientData data=new ClientData();List<Station> stations=new ArrayList<>();List<Platform> platforms=new ArrayList<>();
        for(int i=0;i<4;i++){
            int x=i*100;Station s=new Station(data);s.setName(List.of("Bois des Forges","Manufacture|Quartier des Arts","Port des Brumes","Aérogare").get(i));s.setCorners(new Position(x-20,50,-20),new Position(x+20,80,20));data.stations.add(s);stations.add(s);
            Position a=new Position(x,64,0),b=new Position(x+10,64,0);
            Platform p=new Platform(a,b,TransportMode.TRAIN,data);p.setName("Quai "+(i+1));data.platforms.add(p);platforms.add(p);
            data.rails.add(Rail.newPlatformRail(a,Angle.E,b,Angle.W,Rail.Shape.QUADRATIC,0,new ObjectArrayList<>(),TransportMode.TRAIN));
        }
        StationExit ten=new StationExit();ten.setName("10");ten.getDestinations().add("Place des Tilleuls");StationExit two=new StationExit();two.setName("2");two.getDestinations().add("Rue des Ateliers");stations.get(1).getExits().add(ten);stations.get(1).getExits().add(two);
        Route r=new Route(TransportMode.TRAIN,data);r.setName("Desserte urbaine");r.setRouteNumber("X42");r.setColor(0x15865A);for(Platform p:platforms)r.getRoutePlatforms().add(new RoutePlatformData(p.getId()));data.routes.add(r);
        Route branch=new Route(TransportMode.TRAIN,data);branch.setName("RER des collines");branch.setRouteNumber("R9");branch.setColor(0xC24C9A);branch.getRoutePlatforms().add(new RoutePlatformData(platforms.get(1).getId()));branch.getRoutePlatforms().add(new RoutePlatformData(platforms.get(0).getId()));data.routes.add(branch);
        Lift lift=new Lift(data);var floors=new ObjectArrayList<LiftFloor>();floors.add(new LiftFloor(new Position(105,64,5),"-1","Quai 2"));floors.add(new LiftFloor(new Position(105,72,5),"0","Accueil"));lift.setFloors(floors);data.lifts.add(lift);
        data.sync();check(data.platforms.size()==4,"Real platform rails survive core sync");
        ClientData received=new ClientData();new ListDataResponse(new JsonReader(Utilities.getJsonObjectFromData(new ListDataResponse(data).list())),received).write();
        var network=SillonSignageMtrData.build(received,data);
        check(network.lines().size()==2,"Full routes survive dashboard packet");
        var line=network.lines().stream().filter(l->l.id()==r.getId()).findFirst().orElseThrow();
        check(line.label().equals("X42")&&line.color()==0x15865A&&line.stops().size()==4,"Actual route metadata and ordered stops");
        var station=network.stations().stream().filter(s->s.id()==stations.get(1).getId()).findFirst().orElseThrow();
        check(station.detail().equals("Quartier des Arts")&&station.exits().get(0).number().equals("2"),"Actual station subtitle and natural exit order");
        check(network.services().size()==2,"Actual lift floors");
        var binding=new SillonSignageBinding(station.id(),platforms.get(1).getId(),r.getId(),"auto","full","vertical","","auto",true,true);
        var preset=SillonSignagePresets.ALL.stream().map(p->p.document()).filter(s->s.style().equals("route")).findFirst().orElseThrow().withBinding(binding);
        var context=new SillonSignageAutomatic.Context(station.center(),station.id(),platforms.get(1).getId(),0);
        var resolved=SillonSignageAutomatic.resolve(preset,network,context).document();
        check(resolved.plan().lines().get(0).stops().get(1).icons().contains("line_rer_"),"Interchange from real route graph");
        r.setRouteNumber("Z7");r.setColor(0xB75609);stations.get(1).setName("Nouvelle Manufacture");
        var changed=SillonSignageMtrData.build(data,data);var after=SillonSignageAutomatic.resolve(preset,changed,context).document();
        check(after.plan().lines().get(0).label().equals("Z7")&&after.accent()==0xB75609,"Live route edit uses stable IDs");
        check(!after.visualKey().equals(resolved.visualKey()),"Live edit invalidates texture");
        System.out.println("PASS real MTR objects, LIST_DATA serialization, platform association, route number/colour, exits, lift floors, interchange and live edits");
    }
}
