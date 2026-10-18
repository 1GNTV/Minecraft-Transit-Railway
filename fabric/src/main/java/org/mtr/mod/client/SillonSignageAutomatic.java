package org.mtr.mod.client;

import org.mtr.mod.data.*;
import org.mtr.mod.data.SillonSignageNetworkSnapshot.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** One resolver for ALL families. The saved document stores bindings, never generated Paris examples. */
public final class SillonSignageAutomatic {
    public record Context(Point origin,long stationHint,long platformHint,double rotation){}
    public record Result(SillonSignageSpec document,String status,long stationId,long platformId,long routeId){}
    private SillonSignageAutomatic(){}
    public static String primary(String s){return s==null?"":s.split("\\|",-1)[0].strip();}
    private static String key(Line l){return l.mode()+"/"+l.label()+"/"+l.color();}
    public static String badge(Line l,String mode){return new SillonSignageLineBadge(mode,l.color(),l.label()).token();}
    public static Result resolve(SillonSignageSpec input,SillonSignageNetworkSnapshot network,Context c){
        if(!input.stationAuto())return new Result(input,"Contenu manuel",0,0,0);
        var b=input.binding();
        Map<Long,Station> stations=network.stations().stream().collect(Collectors.toMap(Station::id,x->x,(a,d)->a,LinkedHashMap::new));
        Map<Long,Platform> platforms=network.platforms().stream().collect(Collectors.toMap(Platform::id,x->x,(a,d)->a,LinkedHashMap::new));
        Platform platform=platforms.get(b.platformId()!=0?b.platformId():c.platformHint());
        Station station=stations.get(b.stationId()!=0?b.stationId():platform!=null?platform.stationId():c.stationHint());
        if(station==null&&b.stationId()==0)station=network.stations().stream().filter(s->s.center().distance(c.origin())<128*128)
            .min(Comparator.comparingDouble(s->s.center().distance(c.origin()))).orElse(null);
        if(station!=null&&b.platformId()==0&&(platform==null||platform.stationId()!=station.id())){
            long id=station.id();platform=network.platforms().stream().filter(p->p.stationId()==id)
                .min(Comparator.comparingDouble(p->p.center().distance(c.origin()))).orElse(null);
        }
        final Station selectedStation=station;final Platform selectedPlatform=platform;
        long sid=station==null?0:station.id(),pid=platform==null?0:platform.id();
        List<Line> serving=network.lines().stream().filter(l->l.stops().stream().anyMatch(s->s.stationId()==sid))
            .sorted(Comparator.comparing(Line::label).thenComparingLong(Line::id)).toList();
        List<Line> atPlatform=network.lines().stream().filter(l->l.stops().stream().anyMatch(s->s.platformId()==pid))
            .sorted(Comparator.comparing(Line::label).thenComparingLong(Line::id)).toList();
        Line selected=b.routeId()!=0?network.lines().stream().filter(l->l.id()==b.routeId()).findFirst().orElse(null):
            (!atPlatform.isEmpty()?atPlatform.get(0):serving.isEmpty()?null:serving.get(0));
        String mode=!"auto".equals(b.mode())?b.mode():selected==null?"metro":selected.mode();
        String icons=selected==null?"":SillonSignageLineBadge.modeIcon(mode)+","+badge(selected,mode);
        String stationName=station==null?"Station à associer":station.name();
        int accent=b.lineColors()&&selected!=null&&Set.of("route","platform","map","direction","gpe_direction","digital","vertical","totem","transfer","custom").contains(input.style())?selected.color():input.accent();
        List<SillonSignageRow> rows=new ArrayList<>();String title=stationName,subtitle=station==null?"":station.detail();
        SillonSignagePlan plan=SillonSignagePlan.EMPTY;
        String missing=b.stationId()!=0&&station==null?"La station associée a été supprimée.":
            b.platformId()!=0&&platforms.get(b.platformId())==null?"Le quai associé a été supprimé.":
            b.routeId()!=0&&selected==null?"La ligne associée a été supprimée.":"";
        String status=missing.isBlank()?"Station : "+stationName+(platform==null?"":" · quai "+platform.name())+
            (selected==null?"":" · ligne "+selected.label()):missing;
        if(station==null&&selected==null){
            title="Station à associer";subtitle="Choisir une station dans Réseau";icons="";
        }else switch(input.style()){
            case "metro","metro_subtitle","rer","gpe" -> icons="";
            case "direction","gpe_direction" -> {
                List<Line> directions=b.routeId()!=0&&selected!=null?List.of(selected):!atPlatform.isEmpty()?atPlatform:serving;
                rows.addAll(destinations(directions,network,c,b,selectedPlatform,input.arrow()));
                title=rows.isEmpty()?"Aucune desserte renseignée":rows.get(0).text();subtitle="";
                if(rows.size()==1)icons=rows.get(0).icons();
            }
            case "transfer" -> {
                Set<Long> group=new HashSet<>();group.add(sid);if(station!=null)group.addAll(station.connected());
                final String own=selected==null?"":key(selected);
                List<Line> connections=network.lines().stream().filter(l->!key(l).equals(own)&&l.stops().stream().anyMatch(s->group.contains(s.stationId()))).toList();
                rows.addAll(destinations(connections,network,c,b,null,input.arrow()));
                title="Correspondances";subtitle=rows.isEmpty()?"Aucune correspondance renseignée":"";icons="";
            }
            case "route","platform" -> {
                if(selected!=null){
                    List<Stop> stops=selected.stops();int start=0;
                    if("remaining".equals(b.scope()))for(int i=0;i<stops.size();i++)if(stops.get(i).platformId()==pid){start=i;break;}
                    List<SillonSignagePlan.Stop> drawn=new ArrayList<>();
                    for(int i=start;i<stops.size();i++)drawn.add(planStop(stops.get(i),selected,network,stations));
                    plan=new SillonSignagePlan(List.of(new SillonSignagePlan.Line(selected.id(),selected.label(),mode,selected.color(),drawn)),sid,b.layout());
                    title=destination(selected,pid);subtitle="";
                }else{title="Ligne à associer";subtitle="Choisir une desserte dans Réseau";}
            }
            case "map" -> {
                List<SillonSignagePlan.Line> drawn=new ArrayList<>();
                for(Line l:network.lines())drawn.add(new SillonSignagePlan.Line(l.id(),l.label(),l.mode(),l.color(),l.stops().stream().map(s->planStop(s,l,network,stations)).toList()));
                plan=new SillonSignagePlan(drawn,sid,"network");title="Plan du réseau";subtitle="";icons="";
            }
            case "exit","sector" -> {
                rows.addAll(exits(station,b,input.arrow()));title=rows.size()==1?"Sortie":"Sorties";
                subtitle="sector".equals(input.style())?stationName:rows.isEmpty()?"Aucune sortie renseignée dans MTR":"";
                icons=stationSymbols(serving,b); 
            }
            case "vertical","totem" -> {title="";subtitle="";icons=selected==null?"":SillonSignageLineBadge.modeIcon(mode)+","+badge(selected,mode);}
            case "services" -> {
                rows.addAll(services(network,station,c,b,input.arrow()));title=rows.size()==1?rows.get(0).text():"Services";
                subtitle=rows.isEmpty()?"Aucun service repéré":"";icons="";
            }
            case "digital" -> {title="Prochains départs";subtitle=platform==null?"Quai à associer":"Quai "+platform.name();}
            case "interchange","custom" -> {
                rows.addAll(destinations(serving,network,c,b,null,input.arrow()));
                rows.addAll(services(network,station,c,b,input.arrow()));rows.addAll(exits(station,b,input.arrow()));
                title="Informations";subtitle=rows.isEmpty()?"Aucune donnée MTR disponible":"";icons="";
            }
            default -> {}
        }
        // No stale text, fixed street, static line logo or old manual list survives automatic mode.
        SillonSignageSpec resolved=input.materialize(title,subtitle,icons,accent,rows,plan);
        if(b.arrows()&&selectedPlatform!=null&&Set.of("vertical","totem").contains(input.style()))resolved=resolved.withArrow(arrow(c,selectedPlatform.center()));
        if(selected!=null&&atPlatform.size()>1&&b.routeId()==0&&Set.of("route","platform","totem","vertical").contains(input.style()))
            status+=" · plusieurs dessertes : ligne sélectionnable dans Réseau";
        return new Result(resolved,status,sid,pid,selected==null?0:selected.id());
    }
    private static SillonSignagePlan.Stop planStop(Stop stop,Line own,SillonSignageNetworkSnapshot network,Map<Long,Station> stations){
        Station station=stations.get(stop.stationId());Set<Long> group=new HashSet<>();group.add(stop.stationId());if(station!=null)group.addAll(station.connected());
        Map<String,String> connections=new LinkedHashMap<>();
        for(Line line:network.lines())if(!key(line).equals(key(own))&&line.stops().stream().anyMatch(s->group.contains(s.stationId())))
            {connections.putIfAbsent("mode/"+line.mode(),SillonSignageLineBadge.modeIcon(line.mode()));connections.putIfAbsent(key(line),badge(line,line.mode()));}
        return new SillonSignagePlan.Stop(stop.stationId(),stop.name(),station==null?"":station.detail(),String.join(",",connections.values()),
            station==null?0:station.center().x(),station==null?0:station.center().z(),station!=null);
    }
    private static String stationSymbols(List<Line> lines,SillonSignageBinding b){
        Map<String,String> symbols=new LinkedHashMap<>();for(Line l:lines){String mode="auto".equals(b.mode())?l.mode():b.mode();
            symbols.putIfAbsent("mode/"+mode,SillonSignageLineBadge.modeIcon(mode));symbols.putIfAbsent(key(l),badge(l,mode));}
        return String.join(",",symbols.values());
    }
    private static String destination(Line line,long platform){
        for(Stop s:line.stops())if(s.platformId()==platform&&!s.destination().isBlank())return s.destination();
        return line.stops().isEmpty()?line.label():line.stops().get(line.stops().size()-1).name();
    }
    private static List<SillonSignageRow> destinations(List<Line> lines,SillonSignageNetworkSnapshot n,Context c,SillonSignageBinding b,Platform selected,int fallback){
        Map<String,SillonSignageRow> out=new LinkedHashMap<>();
        for(Line l:lines){
            Platform p=selected!=null&&l.stops().stream().anyMatch(s->s.platformId()==selected.id())?selected:
                n.platforms().stream().filter(x->l.stops().stream().anyMatch(s->s.platformId()==x.id())).min(Comparator.comparingDouble(x->x.center().distance(c.origin()))).orElse(null);
            long pid=p==null?0:p.id();String dest=destination(l,pid),mode="auto".equals(b.mode())?l.mode():b.mode();
            out.putIfAbsent(key(l)+"/"+dest,new SillonSignageRow("transport","",dest,"",SillonSignageLineBadge.modeIcon(mode)+","+badge(l,mode),
                b.arrows()&&p!=null?arrow(c,p.center()):fallback));
        }
        return List.copyOf(out.values());
    }
    private static List<SillonSignageRow> exits(Station station,SillonSignageBinding b,int arrow){
        if(station==null)return List.of();String filter=b.exitFilter().toLowerCase(Locale.ROOT);
        return station.exits().stream().filter(e->filter.isBlank()||e.number().toLowerCase(Locale.ROOT).startsWith(filter)||e.text().toLowerCase(Locale.ROOT).contains(filter))
            .map(e->new SillonSignageRow("exit",e.number(),e.text(),e.detail(),"",arrow)).toList();
    }
    private static List<SillonSignageRow> services(SillonSignageNetworkSnapshot n,Station station,Context c,SillonSignageBinding b,int fallback){
        if(station==null)return List.of();Map<String,SillonSignageRow> rows=new LinkedHashMap<>();
        for(Service service:n.services())if(service.stationId()==station.id()&&("auto".equals(b.service())||b.service().equals(service.kind())))
            rows.putIfAbsent(service.kind()+"/"+service.name()+"/"+service.detail(),new SillonSignageRow("service","",service.name(),service.detail(),service.icons(),b.arrows()?arrow(c,service.position()):fallback));
        return List.copyOf(rows.values());
    }
    public static int arrow(Context c,Point target){
        double dy=target.y()-c.origin().y(),a=Math.toRadians(c.rotation());
        double dx=target.x()-c.origin().x(),dz=target.z()-c.origin().z(),side=-Math.cos(a)*dx-Math.sin(a)*dz;
        double forward=-Math.sin(a)*dx+Math.cos(a)*dz;
        if(Math.abs(dy)>4&&Math.abs(dy)>Math.hypot(dx,dz)*.25){
            if(Math.abs(side)>3)return dy>0?(side<0?4:5):(side<0?6:7);return dy>0?2:3;}
        if(Math.abs(side)>Math.max(2,Math.abs(forward)*.6))return side<0?0:1;
        return 2;
    }
}
