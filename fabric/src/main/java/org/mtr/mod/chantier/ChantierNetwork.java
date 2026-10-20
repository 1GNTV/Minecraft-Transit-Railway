package org.mtr.mod.chantier;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;
import org.mtr.core.serializer.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.*;
import org.mtr.libraries.com.google.gson.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.*;
import static org.mtr.mod.chantier.ChantierPlan.*;

/** Railway records are created/deleted on MTR's simulation queue, never on the block-placement thread. */
public final class ChantierNetwork implements SerializedDataBase {
    public static final String KEY="sillon/chantier/network";
    private final int action;private final String project,plan,expected,desired;
    public ChantierNetwork(int action,String project,String plan,String expected,String desired){this.action=action;this.project=project;this.plan=plan;this.expected=expected;this.desired=desired;}
    @Override public void updateData(ReaderBase r){}
    @Override public void serializeData(WriterBase w){}
    public static final class Result implements SerializedDataBase {
        public final String target,snapshot,update,deleted,error;
        public Result(String target,String snapshot,String update,String deleted,String error){this.target=target;this.snapshot=snapshot;this.update=update;this.deleted=deleted;this.error=error;}
        @Override public void updateData(ReaderBase r){}
        @Override public void serializeData(WriterBase w){w.writeString("target",target);w.writeString("snapshot",snapshot);w.writeString("update",update);w.writeString("deleted",deleted);w.writeString("error",error);}
    }
    private static JsonObject json(String s){return s==null||s.isEmpty()?new JsonObject():JsonParser.parseString(s).getAsJsonObject();}
    private static JsonArray array(JsonObject j,String key){return j.has(key)?j.getAsJsonArray(key):new JsonArray();}
    public static List<long[]> nodes(String raw){List<long[]> result=new ArrayList<>();for(var e:array(json(raw),"rails")){Rail rail=new Rail(new JsonReader(e));JsonObject r=Utilities.getJsonObjectFromData(rail);for(String key:List.of("position1","position2")){JsonObject p=r.getAsJsonObject(key);result.add(new long[]{p.get("x").getAsLong(),p.get("y").getAsLong(),p.get("z").getAsLong(),Math.round(rail.getStartAngle(position(p)).angleDegrees*1000)});}}return result;}
    private static JsonObject snapshot(Data data,JsonObject manifest){
        JsonObject out=new JsonObject();if(manifest.has("station")){long id=manifest.getAsJsonObject("station").get("id").getAsLong();Station s=data.stationIdMap.get(id);if(s!=null)out.add("station",Utilities.getJsonObjectFromData(s));}
        JsonArray rails=new JsonArray(),platforms=new JsonArray();Set<String> railIds=new HashSet<>();for(var e:array(manifest,"rails")){Rail requested=new Rail(new JsonReader(e));Rail actual=data.railIdMap.get(requested.getHexId());if(actual!=null){rails.add(Utilities.getJsonObjectFromData(actual));railIds.add(actual.getHexId());}}
        for(Platform p:data.platforms){var j=Utilities.getJsonObjectFromData(p);Position a=position(j.getAsJsonObject("position1")),b=position(j.getAsJsonObject("position2"));if(railIds.contains(Rail.getHexId(a,b)))platforms.add(j);}
        if(!rails.isEmpty())out.add("rails",rails);if(!platforms.isEmpty())out.add("platforms",platforms);return out;
    }
    private static Position position(JsonObject p){return new Position(p.get("x").getAsLong(),p.get("y").getAsLong(),p.get("z").getAsLong());}
    private static void unused(Data data,JsonObject manifest){Set<Long> ids=new HashSet<>();for(var p:array(manifest,"platforms"))ids.add(p.getAsJsonObject().get("id").getAsLong());for(Route route:data.routes)for(var p:route.getRoutePlatforms())if(p.getPlatform()!=null&&ids.contains(p.getPlatform().getId()))throw new IllegalArgumentException("Un quai du chantier est maintenant desservi : retire d’abord sa desserte avant de déplacer ou supprimer les voies.");}
    private static void unattached(Data data,JsonObject manifest){Set<String> owned=new HashSet<>();for(var e:array(manifest,"rails"))owned.add(new Rail(new JsonReader(e)).getHexId());for(long[] n:nodes(manifest.toString())){var edges=data.positionsToRail.get(new Position(n[0],n[1],n[2]));if(edges!=null)for(Rail r:edges.values())if(!owned.contains(r.getHexId()))throw new IllegalArgumentException("Une voie extérieure est raccordée au chantier : déconnecte-la avant de déplacer les quais.");}}
    private static JsonObject generate(Data data,String project,ChantierPlan blueprint,JsonObject old){
        List<Part> stations=blueprint.parts().stream().filter(p->p.kind()==Kind.STATION).toList();
        JsonObject manifest=new JsonObject();JsonArray rails=new JsonArray();Set<String> oldIds=new HashSet<>();for(var e:array(old,"rails"))oldIds.add(new Rail(new JsonReader(e)).getHexId());
        for(Part p:stations){double dx=(p.b().x()-p.a().x())/p.length(),dz=(p.b().z()-p.a().z())/p.length();Angle angle=Angle.fromAngle((float)Math.toDegrees(Math.atan2(dz,dx)));for(int side:new int[]{-1,1}){
            double offset=side*p.trackOffset();Position a=new Position(Math.round(p.a().x()+dx*2-dz*offset-.5),Math.round(p.a().y()),Math.round(p.a().z()+dz*2+dx*offset-.5)),b=new Position(Math.round(p.b().x()-dx*2-dz*offset-.5),Math.round(p.b().y()),Math.round(p.b().z()-dz*2+dx*offset-.5));
            Rail r=Rail.newPlatformRail(a,angle,b,angle.getOpposite(),Rail.Shape.QUADRATIC,0,new ObjectArrayList<>(List.of("default")),TransportMode.TRAIN);if(!r.isValid())throw new IllegalArgumentException("La géométrie des voies est invalide : allonge la station.");
            // An existing railway is preserved. Architecture may still be generated around it.
            for(Rail existing:data.rails)if(!oldIds.contains(existing.getHexId())&&(existing.closeTo(a,3)||existing.closeTo(b,3)))return new JsonObject();
            rails.add(Utilities.getJsonObjectFromData(r));
        }}
        for(Part p:blueprint.parts())if(p.kind()==Kind.TUNNEL&&p.layout()==0){double dx=(p.b().x()-p.a().x())/p.length(),dz=(p.b().z()-p.a().z())/p.length();Angle angle=Angle.fromAngle((float)Math.toDegrees(Math.atan2(dz,dx)));Position a=new Position(Math.round(p.a().x()-.5),Math.round(p.a().y()),Math.round(p.a().z()-.5)),b=new Position(Math.round(p.b().x()-.5),Math.round(p.b().y()),Math.round(p.b().z()-.5));boolean existing=false;for(Rail r:data.rails)if(!oldIds.contains(r.getHexId())&&(r.closeTo(a,3)||r.closeTo(b,3))){existing=true;break;}if(!existing){Rail r=Rail.newRail(a,angle,b,angle.getOpposite(),Rail.Shape.QUADRATIC,0,new ObjectArrayList<>(List.of("default")),80,80,false,false,true,false,true,TransportMode.TRAIN);if(!r.isValid())throw new IllegalArgumentException("Tracé de tunnel trop court pour la voie.");rails.add(Utilities.getJsonObjectFromData(r));}}
        if(stations.isEmpty()){if(!rails.isEmpty())manifest.add("rails",rails);return manifest;}
        long id=UUID.fromString(project).getMostSignificantBits()&Long.MAX_VALUE;if(id==0)id=1;
        if(data.stationIdMap.containsKey(id)&&!old.has("station"))throw new IllegalArgumentException("Identifiant de gare déjà utilisé.");
        var seed=Utilities.getJsonObjectFromData(new Station(data));seed.addProperty("id",id);Station station=new Station(new JsonReader(old.has("station")?old.get("station"):seed),data);if(!old.has("station")){station.setName("Gare chantier "+project.substring(0,6));station.setColor(0x347D85);}
        long minX=Long.MAX_VALUE,minY=Long.MAX_VALUE,minZ=Long.MAX_VALUE,maxX=Long.MIN_VALUE,maxY=Long.MIN_VALUE,maxZ=Long.MIN_VALUE;
        for(Part p:stations){int m=p.width()/2+2;for(Point v:List.of(p.a(),p.b())){minX=Math.min(minX,(long)Math.floor(v.x())-m);maxX=Math.max(maxX,(long)Math.ceil(v.x())+m);minZ=Math.min(minZ,(long)Math.floor(v.z())-m);maxZ=Math.max(maxZ,(long)Math.ceil(v.z())+m);minY=Math.min(minY,(long)v.y()-1);maxY=Math.max(maxY,(long)v.y()+p.height());}}
        station.setCorners(new Position(minX,minY,minZ),new Position(maxX,maxY,maxZ));for(Station other:data.stations)if(other.getId()!=id&&station.intersecting(other))return new JsonObject();
        manifest.add("station",Utilities.getJsonObjectFromData(station));manifest.add("rails",rails);return manifest;
    }
    public Result run(Simulator data){
        try{
            JsonObject old=json(expected),target=json(desired),actual=snapshot(data,old);
            boolean sameAssets=array(actual,"rails").equals(array(old,"rails"))&&array(actual,"platforms").equals(array(old,"platforms"));
            if(action==1&&target.has("preserve"))return new Result(target.toString(),actual.toString(),"","","");
            if(!actual.equals(old)&&action==0&&sameAssets){JsonObject generated=plan.isEmpty()?new JsonObject():generate(data,project,ChantierPlan.decode(plan),old);if(array(generated,"rails").equals(array(old,"rails")))old=actual;}
            if(!actual.equals(old)){
                // A pending disk journal can refer to either side of a fully applied network update.
                if((action==2||action==3)&&!target.entrySet().isEmpty()&&snapshot(data,target).has("station")){old=snapshot(data,target);JsonObject clean=new JsonObject();clean.add("station",old.get("station"));clean.add("rails",old.get("rails"));if(!clean.equals(target))throw new IllegalArgumentException("Les voies du chantier interrompu ont changé.");}
                else throw new IllegalArgumentException("Les données MTR du chantier ont changé : gare ou voies modifiées dans le dashboard.");
            }
            if(action==0||action==3){JsonObject generated=plan.isEmpty()?new JsonObject():generate(data,project,ChantierPlan.decode(plan),old);if(action==0&&array(generated,"rails").equals(array(old,"rails"))){generated=old.deepCopy();generated.addProperty("preserve",true);}else {unused(data,old);unattached(data,old);}return new Result(generated.toString(),old.toString(),"","","");}
            unused(data,old);
            DeleteDataRequest delete=new DeleteDataRequest();for(var e:array(old,"rails"))delete.addRailId(new Rail(new JsonReader(e)).getHexId());for(var e:array(old,"platforms"))delete.addPlatformId(e.getAsJsonObject().get("id").getAsLong());if(old.has("station"))delete.addStationId(old.getAsJsonObject("station").get("id").getAsLong());
            unattached(data,old);
            String deleted=Utilities.getJsonObjectFromData(delete.delete(data)).toString();if(action==2||target.entrySet().isEmpty())return new Result("{}","{}","",deleted,"");
            UpdateDataRequest update=new UpdateDataRequest(data);if(target.has("station"))update.addStation(new Station(new JsonReader(target.get("station")),data));for(var e:array(target,"rails"))update.addRail(new Rail(new JsonReader(e)));String updated=Utilities.getJsonObjectFromData(update.update()).toString();data.sync();
            return new Result(target.toString(),snapshot(data,target).toString(),updated,deleted,"");
        }catch(RuntimeException e){return new Result("","","","",e.getMessage()==null?"Erreur du réseau MTR.":e.getMessage());}
    }
}
