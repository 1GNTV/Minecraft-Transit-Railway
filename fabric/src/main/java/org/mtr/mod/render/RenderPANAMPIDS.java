package org.mtr.mod.render;

import org.mtr.core.operation.ArrivalResponse;
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
import org.mtr.mod.render.pixel.*;
import java.time.LocalTime;
import java.util.*;

/** PIXEL: shared retained surfaces, event-driven scene compilation, clipped sprite animation. */
public final class RenderPANAMPIDS extends BlockEntityRenderer<BlockPANAMPIDS.BlockEntity> {
    private static final WeakHashMap<BlockPANAMPIDS.BlockEntity,Panel> PANELS=new WeakHashMap<>();
    private static final class Panel {
        final PanamFrame.Timeline timeline=new PanamFrame.Timeline();
        final LongAVLTreeSet platforms=new LongAVLTreeSet();
        PanamSurface.Spec spec;
        long revision=-1,config=-1,network=-1,sampled,platformAt,clockMinute=-1;
        String line="?",destination="En attente";int color=0x808080,hour,minute;
        List<String> notices=List.of(); boolean suspended,last;
    }
    public RenderPANAMPIDS(Argument dispatcher){super(dispatcher);}
    public static void reset(){PANELS.clear();PixelAtlasCache.INSTANCE.reset();}
    @Override public void render(BlockPANAMPIDS.BlockEntity entity,float tickDelta,GraphicsHolder ignored,int light,int overlay){
        World world=entity.getWorld2();if(world==null)return;BlockPos pos=entity.getPos2();
        if(!entity.canStoreData.test(world,pos))return;
        Direction facing=IBlock.getStatePropertySafe(world,pos,DirectionHelper.FACING);
        BlockState partner=world.getBlockState(pos.offset(facing));
        if(!partner.isOf(world.getBlockState(pos).getBlock())||IBlock.getStatePropertySafe(partner,DirectionHelper.FACING)!=facing.getOpposite())return;
        Vector3d eye=MinecraftClient.getInstance().getGameRendererMapped().getCamera().getPos();
        double dx=eye.getXMapped()-pos.getX(),dy=eye.getYMapped()-pos.getY(),dz=eye.getZMapped()-pos.getZ();if(dx*dx+dy*dy+dz*dz>64*64)return;
        long now=System.currentTimeMillis();Panel panel=PANELS.computeIfAbsent(entity,key->new Panel());
        if(now-panel.sampled>=1000||panel.config!=entity.pixelRevision()||panel.revision!=ArrivalsCacheClient.INSTANCE.revision()||panel.network!=MinecraftClientData.getInstance().networkRevision)sample(entity,pos,panel,now);
        if(panel.spec==null)return;
        long minute=now/60000;if(panel.clockMinute!=minute){LocalTime time=LocalTime.now();panel.hour=time.getHour();panel.minute=time.getMinute();panel.clockMinute=minute;}
        panel.timeline.notices(panel.spec.notices.size(),now);
        PixelAtlasCache.Entry entry=PixelAtlasCache.INSTANCE.get(panel.spec,now);if(entry==null)return;
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
                    float z=-.0003f*layer[0]++;
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
        g.translate(-PanamSurface.WORLD_WIDTH/2,-PanamSurface.TOP,-.14);
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
        }
        if(configured){
            ArrayList<String> notices=new ArrayList<>();panel.suspended=false;panel.last=false;
            for(int i=0;i<entity.maxArrivals;i++)for(String value:entity.getMessage(i).split("\\|")){
                String message=value.trim();
                if(message.equalsIgnoreCase("[panam:suspended]")){panel.suspended=true;continue;}
                if(message.equalsIgnoreCase("[panam:last]")){panel.last=true;continue;}
                if(!message.isEmpty()&&!notices.contains(message)&&notices.size()<8)notices.add(message.substring(0,Math.min(512,message.length())));
            }
            panel.notices=List.copyOf(notices);panel.timeline.noticeAt=now;panel.timeline.noticeIndex=0;
        }
        ArrayList<ArrivalResponse> arrivals=new ArrayList<>(ArrivalsCacheClient.INSTANCE.requestArrivals(panel.platforms));
        arrivals.sort(Comparator.comparingLong(ArrivalResponse::getArrival));
        ArrayList<PanamFrame.Departure> trains=new ArrayList<>();ArrayList<String> destinations=new ArrayList<>();HashSet<String> ids=new HashSet<>();boolean planned=false;
        int skip=Math.max(0,entity.getDisplayPage())*3;
        boolean fresh=now-ArrivalsCacheClient.INSTANCE.receivedAt()<15000;
        if(fresh&&!panel.suspended)for(int i=skip;i<arrivals.size()&&i<skip+3;i++){
            ArrivalResponse a=arrivals.get(i);int slot=i-skip;if(entity.getHideArrival(slot))continue;
            long at=a.getArrival()-ArrivalsCacheClient.INSTANCE.getMillisOffset();if(now-at>120000)continue;
            String id=a.getRouteId()+":"+a.getPlatformId()+":"+a.getDepartureIndex();if(!ids.add(id))continue;
            String dest=firstLanguage(a.getDestination());
            if(trains.isEmpty()){panel.line=firstLanguage(a.getRouteNumber());if(panel.line.isEmpty())panel.line=firstLanguage(a.getRouteName()).replaceFirst("(?i)^ligne\\s+","");if(panel.line.isEmpty())panel.line="?";panel.destination=dest;panel.color=a.getRouteColor();}
            trains.add(new PanamFrame.Departure(id,dest,at,a.getRealtime()));destinations.add(dest);planned|=!a.getRealtime();
        }
        panel.timeline.update(trains.toArray(new PanamFrame.Departure[0]),now);
        List<String> notices=panel.notices;
        if(notices.isEmpty())notices=List.of(planned?"Horaires prévisionnels MTR.\nInformations voyageurs indisponibles.":"Informations en temps réel indisponibles");
        ArrayList<String> previous=new ArrayList<>();for(PanamFrame.Departure train:panel.timeline.previous)previous.add(train.destination);
        PanamSurface.Spec next=new PanamSurface.Spec(panel.line,panel.destination,panel.color,destinations,notices,panel.suspended,panel.last,previous);
        if(!next.equals(panel.spec))panel.spec=next;
        panel.config=entity.pixelRevision();panel.revision=ArrivalsCacheClient.INSTANCE.revision();panel.network=network;
    }
    private static String firstLanguage(String value){return value==null?"":value.split("\\|",2)[0].split("\\(",2)[0].trim();}
    @Override public boolean rendersOutsideBoundingBox2(BlockPANAMPIDS.BlockEntity entity){return true;}
}
