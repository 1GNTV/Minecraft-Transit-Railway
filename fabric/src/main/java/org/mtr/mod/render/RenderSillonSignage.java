package org.mtr.mod.render;

import org.mtr.core.data.Station;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockSillonSignage;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.client.DynamicTextureCache;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.SillonSignageSpec;
import org.mtr.mod.data.SillonSignageOptions;
import org.mtr.mod.data.SillonSignageRow;
import org.mtr.mod.client.SillonSignageGeometry;
import org.mtr.mod.screen.EditStationScreen;
import org.mtr.core.data.StationExit;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.mod.data.ArrivalsCacheClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cached texture signage. Rendered inside block entity shader context, never in a shadow pass. */
public final class RenderSillonSignage extends BlockEntityRenderer<BlockSillonSignage.BlockEntity> {
    public static Identifier routeDiagram(BlockPos pos,SillonSignageSpec spec){return null;}
    public static SillonSignageSpec resolveContent(BlockPos pos,SillonSignageSpec spec) {
        return org.mtr.mod.client.SillonSignageNetwork.resolve(pos,spec).document();
    }
    public RenderSillonSignage(Argument dispatcher){super(dispatcher);}
    @Override public void render(BlockSillonSignage.BlockEntity entity,float tickDelta,GraphicsHolder g,int light,int overlay){
        if(OptimizedRenderer.renderingShadows())return;
        World world=entity.getWorld2();if(world==null)return;
        BlockPos pos=entity.getPos2();
        SillonSignageSpec spec=entity.document();
        spec=resolveContent(pos,spec);
        Identifier diagram=routeDiagram(pos,spec);
        Identifier texture=DynamicTextureCache.instance.getSillonSignage(spec).identifier;
        Direction facing=IBlock.getStatePropertySafe(world,pos,BlockSillonSignage.FACING);
        SillonSignageOptions o=spec.options();
        float w=spec.width(),h=spec.height(),depth=o.thickness();
        g.push();
        try {
            g.translate(.5,SillonSignageGeometry.pivotY(spec),.5);
            g.rotateYDegrees(-facing.asRotation()+spec.yaw()+("flag".equals(spec.mount())?90:0));
            g.translate(o.offsetX(),o.offsetY(),o.offsetZ());
            g.rotateXDegrees(spec.pitch());g.rotateZDegrees(spec.roll()+180);
            float z=SillonSignageGeometry.frontZ(spec);
            if("flag".equals(spec.mount()))g.translate(-w/2,0,0);
            int color=0xFF000000|o.frameColor();
            if(!"none".equals(o.frame())) {
                float rim="box".equals(o.frame())?.018f:.005f;
                box(g,-w/2-rim,-h/2-rim,z,w/2+rim,h/2+rim,z+depth,color,light);
            }else box(g,-w/2,-h/2,z,w/2,h/2,z+depth,0xFF000000|spec.background(),light);
            switch(spec.mount()) {
                case "hanging" -> {
                    for(float x:new float[]{-w*.32f,w*.32f})box(g,x-.012f,-h/2-o.suspension(),z+depth*.5f-.012f,
                        x+.012f,-h/2,z+depth*.5f+.012f,color,light);
                }
                case "standing" -> {
                    if(SillonSignageGeometry.pylon(spec)){box(g,-w*.49f,h/2-.045f,z-.01f,w*.49f,h/2,z+depth,0xFF777C80,light);break;}
                    for(float x:new float[]{-w*.28f,w*.28f})box(g,x-.022f,h/2,z+depth*.5f-.022f,
                        x+.022f,h/2+.8f,z+depth*.5f+.022f,color,light);
                    box(g,-w*.43f,h/2+.76f,z-.16f,w*.43f,h/2+.8f,z+depth+.16f,color,light);
                }
                case "flag" -> box(g,w/2,-.07f,z+depth*.5f-.02f,w/2+.5f,.07f,z+depth*.5f+.02f,color,light);
            }
            drawFace(g,texture,diagram,spec,-w/2,-h/2,z-.001f,light);
            if(spec.doubleSided()) {
                g.translate(0,0,2*z+depth);g.rotateYDegrees(180);
                drawFace(g,texture,diagram,spec,-w/2,-h/2,z-.001f,light);
            }
        }finally{g.pop();}
    }
    private static void drawFace(GraphicsHolder g,Identifier texture,Identifier diagram,SillonSignageSpec spec,float x,float y,float z,int light) {
        if(SillonSignageGeometry.pylon(spec)){
            g.createVertexConsumer(MoreRenderLayers.getExterior(WHITE));
            int colour=0xFF000000|spec.background();
            for(int i=0;i<16;i++){
                float x1=x+spec.width()*i/16,x2=x+spec.width()*(i+1)/16;
                float z1=z-SillonSignageGeometry.bow(spec,x1),z2=z-SillonSignageGeometry.bow(spec,x2);
                IDrawing.drawTexture(g,x1,y,z1,x2,y,z2,x2,y,z,x1,y,z,0,0,1,1,Direction.DOWN,colour,light);
                IDrawing.drawTexture(g,x1,y+spec.height(),z,x2,y+spec.height(),z,x2,y+spec.height(),z2,x1,y+spec.height(),z1,0,0,1,1,Direction.UP,colour,light);
            }
        }
        g.createVertexConsumer(spec.illuminated()?MoreRenderLayers.getLight2(texture):MoreRenderLayers.getExterior(texture));
        int applied=spec.illuminated()?GraphicsHolder.getDefaultLight():light;
        if(SillonSignageGeometry.pylon(spec)){
            for(int i=0;i<16;i++){
                float x1=x+spec.width()*i/16,x2=x+spec.width()*(i+1)/16;
                float z1=z-SillonSignageGeometry.bow(spec,x1),z2=z-SillonSignageGeometry.bow(spec,x2);
                IDrawing.drawTexture(g,x1,y+spec.height(),z1,x2,y+spec.height(),z2,x2,y,z2,x1,y,z1,i/16f,0,(i+1)/16f,1,Direction.NORTH,-1,applied);
            }
        }else IDrawing.drawTexture(g,x,y,z,x+spec.width(),y+spec.height(),z,0,0,1,1,Direction.NORTH,-1,applied);
        if(diagram!=null) {
            // Retain the sign header; the MTR diagram occupies the reserved map region.
            g.createVertexConsumer(spec.illuminated()?MoreRenderLayers.getLight2(diagram):MoreRenderLayers.getExterior(diagram));
            float pad=Math.min(spec.width()*.08f,spec.height()*.10f)*spec.options().padding();
            IDrawing.drawTexture(g,x+pad,y+spec.height()*.27f,z-.001f,x+spec.width()-pad,y+spec.height()-pad,z-.001f,
                0,0,1,1,Direction.NORTH,-1,applied);
        }
    }
    private static final Identifier WHITE=new Identifier("mtr","textures/block/white.png");
    private static void box(GraphicsHolder g,float x,float y,float z,float X,float Y,float Z,int color,int light) {
        g.createVertexConsumer(MoreRenderLayers.getExterior(WHITE));
        IDrawing.drawTexture(g,x,Y,z,X,Y,z,X,y,z,x,y,z,0,0,1,1,Direction.NORTH,color,light);
        IDrawing.drawTexture(g,X,Y,Z,x,Y,Z,x,y,Z,X,y,Z,0,0,1,1,Direction.SOUTH,color,light);
        IDrawing.drawTexture(g,x,Y,Z,x,Y,z,x,y,z,x,y,Z,0,0,1,1,Direction.WEST,color,light);
        IDrawing.drawTexture(g,X,Y,z,X,Y,Z,X,y,Z,X,y,z,0,0,1,1,Direction.EAST,color,light);
        IDrawing.drawTexture(g,x,y,z,X,y,z,X,y,Z,x,y,Z,0,0,1,1,Direction.DOWN,color,light);
        IDrawing.drawTexture(g,x,Y,Z,X,Y,Z,X,Y,z,x,Y,z,0,0,1,1,Direction.UP,color,light);
    }
    @Override public boolean rendersOutsideBoundingBox2(BlockSillonSignage.BlockEntity entity){return true;}
    @Override public boolean isInRenderDistance(BlockSillonSignage.BlockEntity entity,Vector3d camera){
        BlockPos pos=entity.getPos2();
        double dx=camera.getXMapped()-pos.getX(),dy=camera.getYMapped()-pos.getY(),dz=camera.getZMapped()-pos.getZ();
        double extent=Math.max(entity.document().width(),entity.document().height())+16;
        double distance=128+extent;
        return dx*dx+dy*dy+dz*dz<distance*distance;
    }
}
