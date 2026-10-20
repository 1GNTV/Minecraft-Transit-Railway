import org.mtr.core.data.*;
import org.mtr.mod.builder.BuilderJourneyRequest;
public final class BuilderJourneyRegression {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Platform platform(ClientData data,Station station,String name,int x){
        Platform platform=new Platform(new Position(x,0,0),new Position(x,0,10),TransportMode.TRAIN,data);
        platform.area=station;platform.setName(name);return platform;
    }
    private static RoutePlatformData stop(Platform platform,String direction){
        RoutePlatformData stop=new RoutePlatformData(platform.getId());stop.platform=platform;stop.setCustomDestination(direction);return stop;
    }
    public static void main(String[] args){
        ClientData data=new ClientData();Station a=new Station(data),b=new Station(data),c=new Station(data);
        a.setName("A");b.setName("B");c.setName("C");
        Platform a1=platform(data,a,"1",0),b2=platform(data,b,"2",10),c1=platform(data,c,"1",20),b1=platform(data,b,"1",30);
        Route route=new Route(TransportMode.TRAIN,data);
        route.getRoutePlatforms().add(stop(a1,"C"));route.getRoutePlatforms().add(stop(b2,""));
        route.getRoutePlatforms().add(stop(c1,""));route.getRoutePlatforms().add(stop(b1,"A"));route.getRoutePlatforms().add(stop(a1,""));
        var returning=BuilderJourneyRequest.routeDetails(route,b.getHexId(),a.getHexId(),"1","1");
        check(returning.stops()==1&&returning.direction().equals("A"),"Return platform selected the outbound visit");
        var outbound=BuilderJourneyRequest.routeDetails(route,b.getHexId(),c.getHexId(),"2","1");
        check(outbound.stops()==1&&outbound.direction().equals("C"),"Outbound platform or custom destination lost");
        var loop=BuilderJourneyRequest.routeDetails(route,b.getHexId(),a.getHexId(),"2","1");
        check(loop.stops()==3&&loop.direction().equals("C"),"Intermediate loop stops were counted incorrectly");
        var ambiguous=BuilderJourneyRequest.routeDetails(route,b.getHexId(),a.getHexId(),"","1");
        check(ambiguous.stops()==-1&&ambiguous.direction().isEmpty(),"Ambiguous repeated visits must not invent details");
        check(BuilderJourneyRequest.routeDetails(route,b.getHexId(),a.getHexId(),"missing","1").stops()==-1,"Missing platform matched an unrelated visit");
        check(BuilderJourneyRequest.routeDetails(null,b.getHexId(),a.getHexId(),"1","1").stops()==-1,"Walking step invented transit details");
        System.out.println("PASS native route details: platform identity, return visit, loop stop count, custom direction and ambiguous fallback");
    }
}
