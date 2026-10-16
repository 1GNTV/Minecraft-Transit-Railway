package org.mtr.mod.render;

import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.*;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockPANAMPIDS;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.ArrivalsCacheClient;
import org.mtr.mod.data.PanamNewsClient;
import org.mtr.mod.data.PanamBulletins;
import org.mtr.mod.render.pixel.*;
import java.time.LocalTime;
import java.util.*;

/** PIXEL: shared retained surfaces, event-driven scene compilation, clipped sprite animation. */
public final class RenderPANAMPIDS extends BlockEntityRenderer<BlockPANAMPIDS.BlockEntity> {
    private static final WeakHashMap<BlockPANAMPIDS.BlockEntity,Panel> PANELS=new WeakHashMap<>();
    private static final boolean DEBUG_ARRIVALS=Boolean.getBoolean("mtr.panam.debugArrivals");
    private static final class Panel {
        final PanamFrame.Timeline timeline=new PanamFrame.Timeline();
        final LongAVLTreeSet platforms=new LongAVLTreeSet();
        final HashSet<Long> servedRoutes=new HashSet<>();
        PanamSurface.Spec spec;
        long revision=-1,config=-1,network=-1,newsRevision=-1,sampled,platformAt,clockMinute=-1;
        long activeRouteId=-1,lastDiagnostic;
        String line="?",destination="En attente";int color=0x808080,hour,minute;
        List<String> notices=List.of(); boolean suspended,localSuspended,last;
    }
    public RenderPANAMPIDS(Argument dispatcher){super(dispatcher);}
    public static void reset(){PANELS.clear();PixelAtlasCache.INSTANCE.reset();PanamNewsClient.reset();}
    @Override public void render(BlockPANAMPIDS.BlockEntity entity,float tickDelta,GraphicsHolder ignored,int light,int overlay){
        World world=entity.getWorld2();if(world==null)return;BlockPos pos=entity.getPos2();
        if(!entity.canStoreData.test(world,pos))return;
        Direction facing=IBlock.getStatePropertySafe(world,pos,DirectionHelper.FACING);
        BlockState partner=world.getBlockState(pos.offset(facing));
        if(!partner.isOf(world.getBlockState(pos).getBlock())||IBlock.getStatePropertySafe(partner,DirectionHelper.FACING)!=facing.getOpposite())return;
        Vector3d eye=MinecraftClient.getInstance().getGameRendererMapped().getCamera().getPos();
        double dx=eye.getXMapped()-pos.getX(),dy=eye.getYMapped()-pos.getY(),dz=eye.getZMapped()-pos.getZ();if(dx*dx+dy*dy+dz*dz>64*64)return;
        long now=System.currentTimeMillis();Panel panel=PANELS.computeIfAbsent(entity,key->new Panel());
        if(now-panel.sampled>=1000||panel.config!=entity.pixelRevision()||panel.revision!=ArrivalsCacheClient.INSTANCE.revision()||panel.network!=MinecraftClientData.getInstance().networkRevision||panel.newsRevision!=PanamNewsClient.revision())sample(entity,pos,panel,now);
        if(panel.spec==null)return;
        long minute=now/60000;if(panel.clockMinute!=minute){LocalTime time=LocalTime.now();panel.hour=time.getHour();panel.minute=time.getMinute();panel.clockMinute=minute;}
        panel.timeline.notices(panel.spec.noticePriorities,now);
        PixelAtlasCache.Entry entry=PixelAtlasCache.INSTANCE.get(panel.spec,now);if(entry==null)return;
        // Use the full-bright text layer, not world-lit EntityCutout or a
        // camera-sorted translucent beacon layer. The UI needs alpha blending
        // with a stable draw order, independent of player view angle.
        MainRenderer.scheduleRender(entry.texture,false,QueuedRenderLayer.LIGHT_2,(g,offset)->{
            g.createVertexConsumer(MoreRenderLayers.getLight2(entry.texture));
            for(int side=0;side<2;side++){
                transform(g,pos,facing,offset,side);
                final int[] layer={0};
                PanamFrame.draw(entry.surface,panel.timeline,now,panel.hour,panel.minute,(s,x,y,w,h,alpha,cx,cy,cw,ch)->{
                    float left=Math.max(x,cx),top=Math.max(y,cy),right=Math.min(x+w,cx+cw),bottom=Math.min(y+h,cy+ch);
                    if(right<=left||bottom<=top||alpha<=0)return;
                    float u1=(s.x+(left-x)*s.w/w)/entry.surface.atlasWidth,u2=(s.x+(right-x)*s.w/w)/entry.surface.atlasWidth;
                    float v1=(s.y+(top-y)*s.h/h)/entry.surface.atlasHeight,v2=(s.y+(bottom-y)*s.h/h)/entry.surface.atlasHeight;
                    // Keep overlays close to the casing: the previous .004f
                    // step visibly pushed characters off the front face.
                    float z=-.0006f*layer[0]++;
                    IDrawing.drawTexture(g,left,top,z,right,bottom,z,u1,v1,u2,v2,Direction.NORTH,((int)(alpha*255)<<24)|0xFFFFFF,GraphicsHolder.getDefaultLight());
                    PixelAtlasCache.INSTANCE.quad();
                });
                g.pop();
            }
        });
    }
    private static void transform(GraphicsHolder g,BlockPos pos,Direction facing,Vector3d offset,int side){
        BlockPos next=pos.offset(facing);
        g.push();g.translate((pos.getX()+next.getX())*.5+.5-offset.getXMapped(),pos.getY()-offset.getYMapped(),(pos.getZ()+next.getZ())*.5+.5-offset.getZMapped());
        g.rotateYDegrees(90-facing.asRotation()+side*180);g.rotateZDegrees(180);
        // Rotate just 2.6 degrees around the top edge. Both display faces
        // lean outward at the bottom towards their respective platform.
        g.translate(0,-PanamSurface.TOP,0);
        g.rotateXDegrees(-2.6f);
        g.translate(-PanamSurface.WORLD_WIDTH/2,0,-.164f);
        g.scale(PanamSurface.WORLD_WIDTH/PanamSurface.WIDTH,PanamSurface.WORLD_HEIGHT/PanamSurface.HEIGHT,1);
    }
    private static void sample(BlockPANAMPIDS.BlockEntity entity,BlockPos pos,Panel panel,long now){
        panel.sampled=now;
        long network=MinecraftClientData.getInstance().networkRevision;
        boolean configured=panel.config!=entity.pixelRevision();
        if(configured||network!=panel.network||now-panel.platformAt>=2000){
            panel.platforms.clear();
            if(!entity.getPlatformIds().isEmpty())panel.platforms.addAll(entity.getPlatformIds());
            else InitClient.findClosePlatform(pos.down(4),5,platform->panel.platforms.add(platform.getId()));
            panel.platformAt=now;
            panel.servedRoutes.clear();
            for(SimplifiedRoute route: MinecraftClientData.getInstance().simplifiedRouteIdMap.values()){
                for(var stop:route.getPlatforms())if(panel.platforms.contains(stop.getPlatformId())){
                    panel.servedRoutes.add(route.getId());break;
                }
            }
        }
        if(configured){
            ArrayList<String> notices=new ArrayList<>();panel.localSuspended=false;panel.last=false;
            for(int i=0;i<entity.maxArrivals;i++)for(String value:entity.getMessage(i).split("\\|")){
                String message=value.trim();
                if(message.equalsIgnoreCase("[panam:suspended]")){panel.localSuspended=true;continue;}
                if(message.equalsIgnoreCase("[panam:last]")){panel.last=true;continue;}
                if(!message.isEmpty()&&!notices.contains(message)&&notices.size()<8)notices.add(message.substring(0,Math.min(512,message.length())));
            }
            panel.notices=List.copyOf(notices);panel.timeline.noticeAt=now;panel.timeline.noticeIndex=0;
        }
        ArrayList<ArrivalResponse> arrivals=new ArrayList<>(ArrivalsCacheClient.INSTANCE.requestArrivals(panel.platforms));
        // Match the base MTR PIDS: preserve the server's arrival order.
        // A local resort can reorder a shared platform's departures.
        ArrayList<PanamFrame.Departure> trains=new ArrayList<>();
        ArrayList<String> destinations=new ArrayList<>();boolean planned=false;
        // Match RenderPIDS paging and hidden-row semantics. Do not drop valid
        // server arrivals via extra freshness, distance or de-duplication filters.
        List<ArrivalResponse> selected=PanamArrivalSelection.select(
                arrivals,entity.maxArrivals,entity.getDisplayPage(),entity::getHideArrival);
        int slot=0;
        for(ArrivalResponse a:selected){
            long at=PanamArrivalSelection.clientArrivalMillis(
                    a.getArrival(),ArrivalsCacheClient.INSTANCE.getMillisOffset());
            String dest=firstLanguage(a.getDestination());
            if(trains.isEmpty()){panel.line=firstLanguage(a.getRouteNumber());if(panel.line.isEmpty())panel.line=firstLanguage(a.getRouteName()).replaceFirst("(?i)^ligne\\s+","");if(panel.line.isEmpty())panel.line="?";panel.destination=dest;panel.color=a.getRouteColor();panel.activeRouteId=a.getRouteId();}
            // Two distinct server rows can share a departure index.
            String id=(slot++)+":"+a.getRouteId()+":"+a.getPlatformId()+":"+a.getDepartureIndex();
            trains.add(new PanamFrame.Departure(id,dest,at,a.getRealtime()));
            destinations.add(dest);planned|=!a.getRealtime();
        }
        panel.timeline.update(trains.toArray(new PanamFrame.Departure[0]),now);
        if(DEBUG_ARRIVALS && now-panel.lastDiagnostic>30000) {
            panel.lastDiagnostic=now;
            org.mtr.mod.Init.LOGGER.info("PANAM arrivals pos={} platforms={} page={} sourceCount={} shown={} offsetMs={}",
                    pos,panel.platforms,entity.getDisplayPage(),arrivals.size(),
                    trains.stream().map(t->t.id+"="+((t.at-now)/1000)+"s").toList(),
                    ArrivalsCacheClient.INSTANCE.getMillisOffset());
        }
        panel.suspended=panel.localSuspended;
        // Global notices are never suppressed by a full local bulletin list.
        ArrayList<String> notices = new ArrayList<>();
        ArrayList<Integer> priorities = new ArrayList<>();
        for (PanamBulletins.Bulletin item: PanamBulletins.decode(PanamNewsClient.global())) {
            if(item.type==PanamBulletins.STOP)panel.suspended=true;
            appendNotice(notices,priorities,PanamBulletins.marked(item),
                item.type==PanamBulletins.STOP?3:0);
        }
        if(panel.activeRouteId>=0)panel.servedRoutes.add(panel.activeRouteId);
        for(long routeId:panel.servedRoutes) {
            for (PanamBulletins.Bulletin item: PanamBulletins.decode(PanamNewsClient.forRoute(routeId))) {
                if(item.type==PanamBulletins.STOP)panel.suspended=true;
                String marked=PanamBulletins.marked(item);
                // Route-specific danger/stop is urgent, but scheduled works is
                // never treated as a safety alert. Other lines still rotate.
                int importance=routeId==panel.activeRouteId
                    ? ((item.type==PanamBulletins.DANGER || item.type==PanamBulletins.STOP)?3:1)
                    : 0;
                appendNotice(notices,priorities,marked,importance);
            }
        }
        for(String local:panel.notices)
            appendNotice(notices,priorities,local,0);
        if (notices.isEmpty()) appendNotice(notices,priorities,planned
                ? "Horaires prévisionnels MTR.\nInformations voyageurs indisponibles."
                : "Informations en temps réel indisponibles",0);
        ArrayList<String> previous=new ArrayList<>();for(PanamFrame.Departure train:panel.timeline.previous)previous.add(train.destination);
        PanamSurface.Spec next=new PanamSurface.Spec(panel.line,panel.destination,panel.color,destinations,notices,panel.suspended,panel.last,previous,priorities);
        if(!next.equals(panel.spec)) {
            // Only restart the ticker when the actual bulletin sequence or its
            // priorities changes, not whenever departures update.
            if(panel.spec==null || !panel.spec.notices.equals(next.notices)
                    || !panel.spec.noticePriorities.equals(next.noticePriorities))
                panel.timeline.restartNotices(now);
            panel.spec=next;
        }
        panel.config=entity.pixelRevision();panel.revision=ArrivalsCacheClient.INSTANCE.revision();panel.network=network;panel.newsRevision=PanamNewsClient.revision();
    }
    private static void appendNotice(List<String> notices,List<Integer> priorities,String message,int priority) {
        int index=notices.indexOf(message);
        if(index>=0) {
            priorities.set(index,Math.max(priorities.get(index),priority));
        } else if(notices.size()<8) {
            notices.add(message);
            priorities.add(priority);
        } else if(priority>=3) {
            // A full network bulletin list must not hide an urgent alert on
            // the currently served line. Keep the other seven messages alive.
            for(int i=notices.size()-1;i>=0;i--)if(priorities.get(i)<3) {
                notices.set(i,message);
                priorities.set(i,priority);
                break;
            }
        }
    }
    private static String firstLanguage(String value){return value==null?"":value.split("\\|",2)[0].split("\\(",2)[0].trim();}
    @Override public boolean rendersOutsideBoundingBox2(BlockPANAMPIDS.BlockEntity entity){return true;}
}
