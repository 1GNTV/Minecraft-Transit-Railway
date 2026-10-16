package org.mtr.mod.render;

import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.*;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockPanamDisplayEntity;
import org.mtr.mod.block.BlockPANAMPIDS;
import org.mtr.mod.block.BlockPANAMScreen;
import org.mtr.mod.data.PanamScreenSettings;
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
public final class RenderPANAMPIDS<T extends BlockPanamDisplayEntity> extends BlockEntityRenderer<T> {
    private static final WeakHashMap<BlockPanamDisplayEntity,Panel> PANELS=new WeakHashMap<>();
    private static final boolean DEBUG_ARRIVALS=Boolean.getBoolean("mtr.panam.debugArrivals");
    private static final class Panel {
        final PanamFrame.Timeline timeline=new PanamFrame.Timeline();
        final PanamNoticeFeed noticeFeed=new PanamNoticeFeed();
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
    @Override public void render(T entity,float tickDelta,GraphicsHolder g,int light,int overlay){
        // The housing is world geometry; emissive LCD pixels do not belong in the shadow map.
        // Return before sampling/config/atlas work in an Iris/Oculus shadow invocation.
        if(OptimizedRenderer.renderingShadows())return;
        World world=entity.getWorld2();if(world==null)return;BlockPos pos=entity.getPos2();
        if(!entity.canStoreData.test(world,pos))return;
        Direction facing=IBlock.getStatePropertySafe(world,pos,DirectionHelper.FACING);
        boolean screenOnly=entity instanceof BlockPANAMScreen.BlockEntity;
        PanamScreenSettings settings=screenOnly?((BlockPANAMScreen.BlockEntity)entity).settings():PanamScreenSettings.DEFAULT;
        BlockState partner=world.getBlockState(pos.offset(facing));
        if(!screenOnly&&(!partner.isOf(world.getBlockState(pos).getBlock())||IBlock.getStatePropertySafe(partner,DirectionHelper.FACING)!=facing.getOpposite()))return;
        Vector3d eye=MinecraftClient.getInstance().getGameRendererMapped().getCamera().getPos();
        double dx=eye.getXMapped()-pos.getX()-(screenOnly?settings.x:0),dy=eye.getYMapped()-pos.getY()-(screenOnly?settings.y:0),dz=eye.getZMapped()-pos.getZ()-(screenOnly?settings.z:0);
        double radius=64+(screenOnly?Math.hypot(settings.width,settings.height)/2:0);if(dx*dx+dy*dy+dz*dz>radius*radius)return;
        if(screenOnly){
            // Text layers can disable GPU culling. Cull explicitly to keep ONE face.
            double yaw=Math.toRadians(-facing.asRotation()+settings.yaw),pitch=Math.toRadians(settings.pitch);
            double front=(dx-.5)*(-Math.cos(pitch)*Math.sin(yaw))+(dy-.5)*Math.sin(pitch)+(dz-.5)*(-Math.cos(pitch)*Math.cos(yaw));
            if(front<=0)return;
        }
        long now=System.currentTimeMillis();Panel panel=PANELS.computeIfAbsent(entity,key->new Panel());
        if(now-panel.sampled>=1000||panel.config!=entity.pixelRevision()||panel.revision!=ArrivalsCacheClient.INSTANCE.revision()||panel.network!=MinecraftClientData.getInstance().networkRevision||panel.newsRevision!=PanamNewsClient.revision())sample(entity,pos,panel,now);
        if(panel.spec==null)return;
        if(entity instanceof BlockPANAMPIDS.BlockEntity && ((BlockPANAMPIDS.BlockEntity)entity).setCasingColor(panel.spec.color)){
            // Both halves share the same owner/color. Re-bake only on an actual color change.
            BlockState state=world.getBlockState(pos);
            world.updateListeners(pos,state,state,3);
            BlockPos other=pos.offset(facing);BlockState otherState=world.getBlockState(other);
            world.updateListeners(other,otherState,otherState,3);
        }
        long minute=now/60000;if(panel.clockMinute!=minute){LocalTime time=LocalTime.now();panel.hour=time.getHour();panel.minute=time.getMinute();panel.clockMinute=minute;}
        panel.timeline.notices(panel.spec.noticePriorities,now);
        PixelAtlasCache.Entry entry=PixelAtlasCache.INSTANCE.get(panel.spec,now);if(entry==null)return;
        // Draw while the vanilla block-entity dispatcher still owns the supplied
        // matrix/buffer. Iris/Oculus wraps that buffer with block-entity context
        // and selects TEXT_BE. Deferring to MainRenderer loses this context and
        // lets a shadow pass consume the pending screen callbacks.
        g.createVertexConsumer(MoreRenderLayers.getLight2(entry.texture));
        for(int side=0;side<(screenOnly?1:2);side++){
            if(screenOnly)transformScreen(g,facing,settings,entry.surface);else transform(g,pos,facing,side);
            try {
                final int[] layer={0};
                PanamFrame.draw(entry.surface,panel.timeline,now,panel.hour,panel.minute,(s,x,y,w,h,alpha,cx,cy,cw,ch)->{
                    float left=Math.max(x,cx),top=Math.max(y,cy),right=Math.min(x+w,cx+cw),bottom=Math.min(y+h,cy+ch);
                    if(right<=left||bottom<=top||alpha<=0)return;
                    float u1=(s.x+(left-x)*s.w/w)/entry.surface.atlasWidth,u2=(s.x+(right-x)*s.w/w)/entry.surface.atlasWidth;
                    float v1=(s.y+(top-y)*s.h/h)/entry.surface.atlasHeight,v2=(s.y+(bottom-y)*s.h/h)/entry.surface.atlasHeight;
                    // Ordered alpha sprites with the existing full-bright text layer.
                    float z=-.0006f*layer[0]++;
                    IDrawing.drawTexture(g,left,top,z,right,bottom,z,u1,v1,u2,v2,Direction.NORTH,((int)(alpha*255)<<24)|0xFFFFFF,GraphicsHolder.getDefaultLight());
                    PixelAtlasCache.INSTANCE.quad();
                });
            } finally {
                g.pop();
            }
        }
    }
    private static void transformScreen(GraphicsHolder g,Direction facing,PanamScreenSettings s,PanamSurface surface){
        g.push();g.translate(.5+s.x,.5+s.y,.5+s.z);
        g.rotateYDegrees(-facing.asRotation()+s.yaw);g.rotateXDegrees(s.pitch);g.rotateZDegrees(s.roll);
        g.rotateZDegrees(180);g.translate(-s.width/2,-s.height/2,0);
        g.scale(s.width/surface.spec.viewport.width,s.height/surface.spec.viewport.height,1);
    }
    @Override public boolean isInRenderDistance(T entity,Vector3d position){return true;}
    private static void transform(GraphicsHolder g,BlockPos pos,Direction facing,int side){
        BlockPos next=pos.offset(facing);
        // The incoming block-entity matrix already contains pos - camera.
        g.push();g.translate((next.getX()-pos.getX())*.5+.5,0,(next.getZ()-pos.getZ())*.5+.5);
        g.rotateYDegrees(90-facing.asRotation()+side*180);g.rotateZDegrees(180);
        // Positive local pitch becomes DOWNWARD-facing after rotateZ(180).
        // The upper edges are farther apart than the lower edges, as photographed.
        g.translate(0,-PanamSurface.TOP,0);
        g.rotateXDegrees(2.6f);
        g.translate(-PanamSurface.WORLD_WIDTH/2,0,-.164f);
        g.scale(PanamSurface.WORLD_WIDTH/PanamSurface.WIDTH,PanamSurface.WORLD_HEIGHT/PanamSurface.HEIGHT,1);
    }
    private static void sample(BlockPanamDisplayEntity entity,BlockPos pos,Panel panel,long now){
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
        // All synchronized line bulletins belong to the network information feed,
        // including routes that do not serve this platform. Each retains its badge.
        ArrayList<PanamNotice> candidates=new ArrayList<>();
        for(PanamBulletins.Bulletin item:PanamBulletins.decode(PanamNewsClient.global())){
            if(item.type==PanamBulletins.STOP)panel.suspended=true;
            candidates.add(new PanamNotice("",0x808080,PanamBulletins.marked(item),item.type==PanamBulletins.STOP?3:0));
        }
        for(Map.Entry<Long,String> routeNews:new TreeMap<>(PanamNewsClient.routes()).entrySet()){
            long routeId=routeNews.getKey();
            SimplifiedRoute route=MinecraftClientData.getInstance().simplifiedRouteIdMap.get(routeId);
            String badge=route==null?"?":firstLanguage(route.getName()).replaceFirst("(?i)^(ligne|line)\\s+","");
            if(badge.isEmpty())badge="?";
            int color=route==null?0x808080:route.getColor();
            boolean sameLine=!badge.equals("?")&&badge.equals(panel.line)&&color==panel.color;
            boolean current=routeId==panel.activeRouteId||sameLine||(panel.activeRouteId<0&&panel.servedRoutes.contains(routeId));
            for(PanamBulletins.Bulletin item:PanamBulletins.decode(routeNews.getValue())){
                // A shutdown on another line must never cancel this line's departures.
                if(current&&item.type==PanamBulletins.STOP)panel.suspended=true;
                int importance=current?((item.type==PanamBulletins.DANGER||item.type==PanamBulletins.STOP)?3:1):0;
                candidates.add(new PanamNotice(badge,color,PanamBulletins.marked(item),importance));
            }
        }
        for(String local:panel.notices)candidates.add(new PanamNotice(panel.line,panel.color,local,0));
        if(candidates.isEmpty())candidates.add(new PanamNotice("",0x808080,planned
                ?"Horaires prévisionnels MTR.\nInformations voyageurs indisponibles."
                :"Informations en temps réel indisponibles",0));
        List<PanamNotice> visible=panel.noticeFeed.window(candidates,now);
        ArrayList<String> notices=new ArrayList<>();ArrayList<Integer> priorities=new ArrayList<>();
        for(PanamNotice notice:visible){notices.add(notice.message);priorities.add(notice.priority);}
        ArrayList<String> previous=new ArrayList<>();for(PanamFrame.Departure train:panel.timeline.previous)previous.add(train.destination);
        PanamSurface.Spec next=new PanamSurface.Spec(panel.line,panel.destination,panel.color,destinations,notices,panel.suspended,panel.last,previous,priorities).withNoticeSources(visible);
        if(entity instanceof BlockPANAMScreen.BlockEntity)next=next.withViewport(new PanamViewport(((BlockPANAMScreen.BlockEntity)entity).settings()));
        if(!next.equals(panel.spec)) {
            // Only restart the ticker when the actual bulletin sequence or its
            // priorities changes, not whenever departures update.
            if(panel.spec==null || !panel.spec.notices.equals(next.notices)
                    || !panel.spec.noticePriorities.equals(next.noticePriorities)
                    || !panel.spec.noticeSources.equals(next.noticeSources))
                panel.timeline.restartNotices(now);
            panel.spec=next;
        }
        panel.config=entity.pixelRevision();panel.revision=ArrivalsCacheClient.INSTANCE.revision();panel.network=network;panel.newsRevision=PanamNewsClient.revision();
    }
    private static String firstLanguage(String value){return value==null?"":value.split("\\|",2)[0].split("\\(",2)[0].trim();}
    @Override public boolean rendersOutsideBoundingBox2(T entity){return true;}
}
