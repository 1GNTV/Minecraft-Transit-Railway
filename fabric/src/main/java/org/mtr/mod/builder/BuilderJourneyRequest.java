package org.mtr.mod.builder;
import org.mtr.core.data.*;
import org.mtr.core.data.Station;
import org.mtr.core.map.DirectionsRequest;
import org.mtr.core.map.DirectionsConnection;
import org.mtr.core.serializer.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.*;
import java.util.function.Consumer;

/** Reuses MTR's asynchronous timetable routing, on its simulation queue. */
public final class BuilderJourneyRequest implements SerializedDataBase {
    public static final String KEY="sillon/builder/journey";
    private final long origin,destination;
    private final Consumer<String> callback;
    public BuilderJourneyRequest(long origin,long destination,Consumer<String> callback){this.origin=origin;this.destination=destination;this.callback=callback;}
    @Override public void updateData(ReaderBase r){}
    @Override public void serializeData(WriterBase w){w.writeLong("origin",origin);w.writeLong("destination",destination);}
    public SerializedDataBase search(Simulator data){
        Station start=data.stationIdMap.get(origin),end=data.stationIdMap.get(destination);
        if(start==null||end==null){callback.accept(error("Station absente du réseau."));return new BuilderPlanRequest.Response("");}
        if(origin==destination){callback.accept(error("Tu es déjà dans cette station."));return new BuilderPlanRequest.Response("");}
        data.directionsFinder.addRequest(new DirectionsRequest(position(start),position(end),data.getCurrentMillis(),response->{
            try{
                JsonObject result=new JsonObject();JsonArray legs=new JsonArray();result.addProperty("origin",start.getName());result.addProperty("destination",end.getName());result.addProperty("generated",data.getCurrentMillis());
                for(DirectionsConnection connection:response.getDirectionsConnections()){
                    JsonObject leg=Utilities.getJsonObjectFromData(connection);String routeId=leg.get("routeId").getAsString();Route route=data.routes.stream().filter(r->r.getHexId().equalsIgnoreCase(routeId)).findFirst().orElse(null);
                    String startId=leg.get("startStationId").getAsString(),endId=leg.get("endStationId").getAsString();
                    Station from=data.stations.stream().filter(s->s.getHexId().equalsIgnoreCase(startId)).findFirst().orElse(null),to=data.stations.stream().filter(s->s.getHexId().equalsIgnoreCase(endId)).findFirst().orElse(null);
                    leg.addProperty("from",from==null?"À pied":from.getName());leg.addProperty("to",to==null?end.getName():to.getName());
                    leg.addProperty("line",route==null?"À pied":route.getRouteNumber().isBlank()?route.getName():route.getRouteNumber());leg.addProperty("color",route==null?0x6E8798:route.getColor());
                    int stops=-1,first=-1,last=-1;
                    if(route!=null)for(int i=0;i<route.getRoutePlatforms().size();i++){Platform p=route.getRoutePlatforms().get(i).getPlatform();if(p==null||p.area==null)continue;if(first<0&&p.area.getHexId().equalsIgnoreCase(startId))first=i;else if(first>=0&&p.area.getHexId().equalsIgnoreCase(endId)){last=i;break;}}
                    if(first>=0&&last>first)stops=last-first;leg.addProperty("stops",stops);leg.addProperty("direction",route!=null&&first>=0?route.getDestination(first):"");legs.add(leg);
                    if(legs.size()>=40)break;
                }
                result.add("legs",legs);if(legs.isEmpty())result.addProperty("error","Aucun itinéraire disponible dans les horaires MTR.");String raw=result.toString();callback.accept(raw.length()<27000?raw:error("Itinéraire trop détaillé : choisis une étape intermédiaire."));
            }catch(RuntimeException failure){callback.accept(error("Le calcul MTR n’a pas fourni un itinéraire lisible."));}
        }));
        return new BuilderPlanRequest.Response("");
    }
    private static Position position(Station station){
        Position center=station.getCenter();if(station.savedRails.isEmpty())return center;
        Platform nearest=station.savedRails.stream().min(java.util.Comparator.comparingDouble(p->Math.hypot(p.getMidPosition().getX()-center.getX(),p.getMidPosition().getZ()-center.getZ()))).orElseThrow();return nearest.getMidPosition();
    }
    public static String error(String message){JsonObject error=new JsonObject();error.addProperty("error",message);return error.toString();}
}
