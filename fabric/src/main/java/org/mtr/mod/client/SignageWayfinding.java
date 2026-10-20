package org.mtr.mod.client;
import org.mtr.mod.builder.WalkGraph;
import org.mtr.mod.data.*;
import org.mtr.mod.data.SillonSignageNetworkSnapshot.*;
import java.util.*;

/** Derives only the next authored walking edge. Missing topology is shown explicitly. */
public final class SignageWayfinding {
    private SignageWayfinding(){}
    public static SillonSignageAutomatic.Result resolve(SillonSignageAutomatic.Result base,SillonSignageNetworkSnapshot network,SillonSignageAutomatic.Context context){
        var spec=base.document();if(!spec.binding().guided())return base;
        if(!Set.of("transfer","interchange","direction","gpe_direction","custom","vertical","exit","services","sector").contains(spec.style()))return missing(base,"Ce format ne propose pas de guidage piéton.");
        WalkGraph graph=WayfindingClient.graph();var start=graph.nearest(context.origin().x(),context.origin().y(),context.origin().z(),8);
        if(start==null)return missing(base,"Point de cheminement à placer près du panneau.");
        List<SillonSignageRow> rows=new ArrayList<>();Set<String> seen=new HashSet<>();var binding=spec.binding();
        if(Set.of("exit","sector","services").contains(spec.style())){
            for(var target:graph.nodes()){
                boolean exit=Set.of("exit","sector").contains(spec.style());
                if(exit?!target.kind().equals("exit"):!Set.of("stairs","lift").contains(target.kind())||!binding.service().equals("auto")&&!binding.service().equals(target.kind()))continue;
                if(exit&&!binding.exitFilter().isBlank()&&!target.label().toLowerCase(Locale.ROOT).contains(binding.exitFilter().toLowerCase(Locale.ROOT)))continue;
                var path=graph.pathToNode(start.id(),target.id(),false);if(path.isEmpty())continue;int arrow=arrow(context,path);
                String label=target.label().isBlank()?target.kind().equals("lift")?"Ascenseur":target.kind().equals("stairs")?"Escalier":"Sortie":target.label();
                String icon=target.kind().equals("lift")?"official_lift":target.kind().equals("stairs")?"official_stairs":"";
                if(SillonSignageIconCatalog.get(icon)==null)icon="";
                rows.add(new SillonSignageRow(exit?"exit":"service","",label,"",icon,arrow));if(rows.size()>=12)break;
            }
        }else{
            for(Platform platform:network.platforms()){
                if(binding.platformId()!=0?platform.id()!=binding.platformId():platform.stationId()!=base.stationId())continue;
                var path=graph.path(start.id(),platform.id(),false);if(path.isEmpty())continue;int arrow=arrow(context,path);
                for(Line line:network.lines()){
                    if(binding.routeId()!=0&&line.id()!=binding.routeId())continue;
                    for(Stop stop:line.stops())if(stop.platformId()==platform.id()){
                        String destination=SillonSignageAutomatic.primary(stop.destination());String key=line.id()+"/"+destination+"/"+arrow;
                        if(!seen.add(key))continue;
                        rows.add(new SillonSignageRow("transport","",destination.isBlank()?SillonSignageAutomatic.primary(line.label()):destination,"Quai "+platform.name(),new SillonSignageLineBadge(binding.mode().equals("auto")?line.mode():binding.mode(),line.color(),SillonSignageAutomatic.primary(line.label())).token(),arrow));break;
                    }
                    if(rows.size()>=12)break;
                }
                if(rows.size()>=12)break;
            }
        }
        if(rows.isEmpty())return missing(base,"Cheminement vers la destination à relier au brush.");
        return new SillonSignageAutomatic.Result(spec.withRows(rows).withArrow(rows.get(0).arrow()),"Guidage par cheminements déclarés",base.stationId(),base.platformId(),base.routeId());
    }
    private static int arrow(SillonSignageAutomatic.Context context,List<WalkGraph.Node> path){if(path.size()<2)return -1;var next=path.get(1);var start=path.get(0);return SillonSignageAutomatic.arrow(new SillonSignageAutomatic.Context(new Point(start.x(),start.y(),start.z()),context.stationHint(),context.platformHint(),context.rotation()),new Point(next.x(),next.y(),next.z()));}
    private static SillonSignageAutomatic.Result missing(SillonSignageAutomatic.Result base,String status){return new SillonSignageAutomatic.Result(base.document().withRows(List.of(new SillonSignageRow("text","","Cheminement à définir","","",-1))).withArrow(-1),status,base.stationId(),base.platformId(),base.routeId());}
}
