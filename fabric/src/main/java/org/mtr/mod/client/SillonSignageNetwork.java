package org.mtr.mod.client;

import org.mtr.core.data.ClientData;
import org.mtr.core.operation.ListDataResponse;
import org.mtr.core.serializer.JsonReader;
import org.mtr.mapping.holder.*;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockSillonSignage;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.data.*;
import org.mtr.mod.data.SillonSignageNetworkSnapshot.*;
import org.mtr.mod.packet.PacketSignageNetwork;
import org.mtr.mod.render.PanamArrivalSelection;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import java.util.*;

/** Shared, throttled network subscription. Work is cached across all visible panels. */
public final class SillonSignageNetwork {
    private static MinecraftClientData owner;
    private static ClientData full;
    private static SillonSignageNetworkSnapshot snapshot=SillonSignageNetworkSnapshot.EMPTY;
    private static long requested,built,revision,serial,clientRevision=-1;
    private record CacheKey(long position,SillonSignageSpec spec){}
    private record Entry(long time,long revision,SillonSignageAutomatic.Result result){}
    private static final Map<CacheKey,Entry> CACHE=new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<CacheKey,Entry> e){return size()>512;}
    };
    private static String name(String s){return SillonSignageAutomatic.primary(s);}
    private record ServiceCell(long station,int x,int y,int z){}
    private record ServiceEntry(long time,List<Service> services){}
    private static final Map<ServiceCell,ServiceEntry> SERVICES=new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<ServiceCell,ServiceEntry> e){return size()>128;}
    };
    private static final class ServiceScan {final ServiceCell cell;final BlockPos origin;final World world;final Map<String,Service> found=new LinkedHashMap<>();int cursor;ServiceScan(ServiceCell c,BlockPos p,World w){cell=c;origin=p;world=w;}}
    private static final LinkedHashMap<ServiceCell,ServiceScan> SCANS=new LinkedHashMap<>();
    public static void tick(){
        var clientWorld=MinecraftClient.getInstance().getWorldMapped();if(clientWorld==null){SCANS.clear();return;}
        int budget=192;var it=SCANS.values().iterator();if(!it.hasNext())return;var scan=it.next();
        if(scan.world.data!=clientWorld.data){it.remove();return;}
        while(budget-->0&&scan.cursor<2601){int n=scan.cursor++,x=n%17-8,z=(n/17)%17-8,y=n/(17*17)-4;BlockPos at=new BlockPos(scan.origin.getX()+x,scan.origin.getY()+y,scan.origin.getZ()+z);if(!Init.isChunkLoaded(scan.world,at))continue;
            String type=scan.world.getBlockState(at).getBlock().data.getClass().getSimpleName().toLowerCase(Locale.ROOT);String kind=type.contains("escalator")?"escalator":type.contains("stair")?"stairs":type.contains("ticketmachine")?"information":"";if(kind.isEmpty())continue;Point point=new Point(at.getX()+.5,at.getY()+.5,at.getZ()+.5);Point origin=new Point(scan.origin.getX()+.5,scan.origin.getY()+.5,scan.origin.getZ()+.5);Service previous=scan.found.get(kind);if(previous!=null&&previous.position().distance(origin)<=point.distance(origin))continue;String text=kind.equals("escalator")?"Escalator":kind.equals("stairs")?"Escalier":"Billetterie";String icon=kind.equals("escalator")?"official_escalator":kind.equals("stairs")?"official_stairs":"sncf_info";scan.found.put(kind,new Service(scan.cell.station(),kind,text,"",SillonSignageIconCatalog.get(icon)==null?"":icon,point));
        }
        if(scan.cursor>=2601){SERVICES.put(scan.cell,new ServiceEntry(System.currentTimeMillis(),List.copyOf(scan.found.values())));it.remove();}
    }
    private SillonSignageNetwork(){}
    public static void accept(JsonReader reader){
        ClientData data=new ClientData();new ListDataResponse(reader,data).write();full=data;serial++;built=0;CACHE.clear();
    }
    public static SillonSignageNetworkSnapshot snapshot(){
        MinecraftClientData live=MinecraftClientData.getInstance();long now=System.currentTimeMillis();
        if(owner!=live){owner=live;full=null;requested=built=0;serial++;clientRevision=-1;CACHE.clear();SERVICES.clear();SCANS.clear();}
        if(now-requested>10000&&MinecraftClient.getInstance().getPlayerMapped()!=null){
            requested=now;InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketSignageNetwork());
        }
        if(built==0||clientRevision!=live.networkRevision||now-built>10000){
            snapshot=SillonSignageMtrData.build(full==null?live:full,live);clientRevision=live.networkRevision;built=now;revision++;CACHE.clear();
        }
        return snapshot;
    }
    public static SillonSignageAutomatic.Result resolve(BlockPos pos,SillonSignageSpec spec){
        if(!spec.stationAuto())return new SillonSignageAutomatic.Result(spec,"Contenu manuel",0,0,0);
        var data=snapshot();long now=System.currentTimeMillis();CacheKey key=new CacheKey(pos.asLong(),spec);Entry cached=CACHE.get(key);
        if(cached!=null&&cached.revision()==revision&&now-cached.time()<1500)return cached.result();
        var station=InitClient.findStation(pos);final long[] platform={0};InitClient.findClosePlatform(pos.down(3),24,p->platform[0]=p.getId());
        double rotation=-spec.yaw()-("flag".equals(spec.mount())?90:0);var world=MinecraftClient.getInstance().getWorldMapped();
        if(world!=null)rotation+=IBlock.getStatePropertySafe(new World(world.data),pos,BlockSillonSignage.FACING).asRotation();
        var context=new SillonSignageAutomatic.Context(new Point(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5),station==null?0:station.getId(),platform[0],rotation);
        if(world!=null&&Set.of("services","interchange","custom").contains(spec.style()))data=nearbyServices(data,context,spec.binding().stationId(),new World(world.data),pos);
        var result=SillonSignageAutomatic.resolve(spec,data,context);
        if("digital".equals(spec.style()))result=departures(result,data);
        CACHE.put(key,new Entry(now,revision,result));return result;
    }
    private static SillonSignageNetworkSnapshot nearbyServices(SillonSignageNetworkSnapshot data,SillonSignageAutomatic.Context context,long boundStation,World world,BlockPos origin){
        long sid=boundStation==0?context.stationHint():boundStation;if(sid==0||sid!=context.stationHint())return data;
        ServiceCell cell=new ServiceCell(sid,origin.getX()>>4,origin.getY()>>3,origin.getZ()>>4);
        long now=System.currentTimeMillis();ServiceEntry cached=SERVICES.get(cell);
        if((cached==null||now-cached.time()>8000)&&SCANS.size()<32)SCANS.computeIfAbsent(cell,c->new ServiceScan(c,origin,world));
        if(cached==null)return data;
        List<Service> merged=new ArrayList<>(data.services());merged.addAll(cached.services());return new SillonSignageNetworkSnapshot(data.stations(),data.platforms(),data.lines(),merged);
    }

    private static SillonSignageAutomatic.Result departures(SillonSignageAutomatic.Result result,SillonSignageNetworkSnapshot data){
        SillonSignageSpec s=result.document();LongAVLTreeSet ids=new LongAVLTreeSet();
        if(result.platformId()!=0)ids.add(result.platformId());else for(Platform p:data.platforms())if(p.stationId()==result.stationId())ids.add(p.id());
        List<SillonSignageRow> rows=new ArrayList<>();long now=System.currentTimeMillis();
        if(ids.isEmpty())return new SillonSignageAutomatic.Result(s.withRows(List.of(new SillonSignageRow("text","","Quai à associer","","",-1))),result.status(),result.stationId(),result.platformId(),result.routeId());
        var arrivals=new ArrayList<>(ArrivalsCacheClient.INSTANCE.requestArrivals(ids));
        if(result.routeId()!=0)arrivals.removeIf(a->a.getRouteId()!=result.routeId());
        for(var a:PanamArrivalSelection.select(arrivals,8,0,i->false)){
            String label=name(a.getRouteNumber());if(label.isBlank())label=name(a.getRouteName());
            String mode=data.lines().stream().filter(l->l.id()==a.getRouteId()).findFirst().map(Line::mode).orElse("metro");
            if(!s.binding().mode().equals("auto"))mode=s.binding().mode();
            long at=PanamArrivalSelection.clientArrivalMillis(a.getArrival(),ArrivalsCacheClient.INSTANCE.getMillisOffset());
            String time=at-now<60000?"À l’approche":((at-now+59999)/60000)+" min";
            rows.add(new SillonSignageRow("transport",time,name(a.getDestination()),name(a.getPlatformName()),new SillonSignageLineBadge(mode,a.getRouteColor(),label).token(),-1));
        }
        if(rows.isEmpty())rows.add(new SillonSignageRow("text","",ids.isEmpty()?"Quai à associer":"Aucun départ annoncé","","",-1));
        String clock=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        var shown=s.materialize(s.title(),clock,s.icons(),s.accent(),rows,s.plan());
        return new SillonSignageAutomatic.Result(shown,result.status(),result.stationId(),result.platformId(),result.routeId());
    }
}
