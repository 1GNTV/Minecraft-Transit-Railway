package org.mtr.mod.builder;
import org.mtr.core.data.*;
import org.mtr.libraries.com.google.gson.*;
import java.util.*;

/** Reports actual MTR path generation results, rather than inventing a second pathfinder. */
public final class NetworkDiagnostics {
    public record Problem(String title,String detail,Position a,Position b){}
    private NetworkDiagnostics(){}
    public static List<Problem> inspect(Data data){List<Problem> result=new ArrayList<>();
        for(Depot d:data.depots){if(d.getTransportMode()!=TransportMode.TRAIN)continue;Position p=d.getCenter();
            if(d.getRouteIds().isEmpty())result.add(new Problem(d.getName()+" : aucun itinéraire","Affecter les instructions dans le dépôt.",p,null));
            else if(d.getLastGeneratedMillis()==0)result.add(new Problem(d.getName()+" : trajet non calculé","Actualiser le trajet dans le dépôt pour lancer le diagnostic MTR.",p,null));
            if(d.getLastGeneratedStatus()==Depot.GeneratedStatus.NO_SIDINGS)result.add(new Problem(d.getName()+" : aucune voie de garage","Vérifier qu’une voie de garage est incluse dans la zone du dépôt.",p,null));
            if(d.getLastGeneratedStatus()==Depot.GeneratedStatus.TWO_PLATFORMS_REQUIRED)result.add(new Problem(d.getName()+" : desserte incomplète","MTR demande au moins deux quais dans les instructions.",p,null));
            d.getFailedPlatformIds((a,b)->{Platform pa=data.platformIdMap.get(a),pb=data.platformIdMap.get(b);result.add(new Problem("Liaison impossible : "+label(pa)+" → "+label(pb),"Échec du calcul MTR dans "+d.getName()+". Vérifier raccords et sens de circulation entre ces quais.",pa==null?p:pa.getMidPosition(),pb==null?null:pb.getMidPosition()));},n->{if(n>0)result.add(new Problem(d.getName()+" : "+n+" garage(s) inaccessibles","Vérifier les liaisons entre le dépôt, le premier et le dernier quai.",p,null));});
        }
        for(Siding s:data.sidings){if(s.getTransportMode()!=TransportMode.TRAIN)continue;if(s.getVehicleCars().isEmpty())result.add(new Problem("Garage "+s.getName()+" : aucune rame","Choisir une composition dans le sélecteur de rames.",s.getMidPosition(),null));else if(Siding.getTotalVehicleLength(s.getVehicleCars())>s.getRailLength()+.001)result.add(new Problem("Garage "+s.getName()+" : rame trop longue","Raccourcir la rame ou agrandir la voie de garage.",s.getMidPosition(),null));}
        for(Route r:data.routes){if(r.getTransportMode()!=TransportMode.TRAIN)continue;for(var stop:r.getRoutePlatforms())if(stop.getPlatform()==null)result.add(new Problem(r.getName()+" : quai introuvable","Un arrêt de l’itinéraire ne correspond plus à un quai existant.",null,null));}
        return List.copyOf(result.subList(0,Math.min(128,result.size())));
    }
    private static String label(Platform p){return p==null?"quai supprimé":(p.area==null?"":p.area.getName()+" · ")+p.getName();}
    public static JsonObject encode(List<Problem> problems){JsonObject root=new JsonObject();root.addProperty("type","diagnostics");JsonArray list=new JsonArray();for(Problem p:problems){JsonObject o=new JsonObject();o.addProperty("title",p.title);o.addProperty("detail",p.detail);if(p.a!=null)o.add("a",point(p.a));if(p.b!=null)o.add("b",point(p.b));list.add(o);}root.add("issues",list);return root;}
    private static JsonArray point(Position p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
}
