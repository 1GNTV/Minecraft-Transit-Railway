package org.mtr.mod.render;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.block.*;
import org.mtr.mod.client.DecorBrushClient;
import org.mtr.mod.decor.*;
import org.mtr.mod.client.IDrawing;
import java.util.*;

/** Immutable bounded mesh cache: no animation, world scan or mesh rebuild in the render loop. */
public final class RenderStationDecor extends BlockEntityRenderer<BlockStationDecor.BlockEntity> {
    private static final Map<String,Identifier> TEXTURES=new HashMap<>();
    public RenderStationDecor(Argument dispatcher){super(dispatcher);}
    public static void draw(GraphicsHolder g,DecorSpec spec,int light,int tint){
        for(var f:DecorMesh.get(spec).faces()){
            boolean glass=f.surface().equals("glass");boolean glow=f.surface().equals("glow")&&spec.illuminated();
            Identifier texture=f.surface().equals("poster")&&!spec.image().isEmpty()?new Identifier(spec.image()):TEXTURES.computeIfAbsent(f.surface(),m->new Identifier("mtr","textures/decor/"+m+".png"));
            if(f.surface().equals("label"))texture=org.mtr.mod.client.DynamicTextureCache.instance.getSignText(spec.label(),org.mtr.mod.data.IGui.HorizontalAlignment.CENTER,0.1f,spec.accent(),0xFFFFFF).identifier;
            g.createVertexConsumer(glass||tint!=255?MoreRenderLayers.getExteriorTranslucent(texture):glow?MoreRenderLayers.getLight2(texture):MoreRenderLayers.getExterior(texture));
            var a=f.a();var b=f.b();var c=f.c();var d=f.d();int color=(f.color()&0xFFFFFF)|((glass?Math.min(tint,96):tint)<<24);
            float nx=(b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
            float ny=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
            float nz=(b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
            Direction normal=Math.abs(ny)>Math.abs(nx)&&Math.abs(ny)>Math.abs(nz)?ny>0?Direction.UP:Direction.DOWN:Math.abs(nx)>Math.abs(nz)?nx>0?Direction.EAST:Direction.WEST:nz>0?Direction.SOUTH:Direction.NORTH;
            IDrawing.drawTexture(g,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),c.x(),c.y(),c.z(),d.x(),d.y(),d.z(),0,0,f.u(),f.v(),normal,color,glow?GraphicsHolder.getDefaultLight():light);
            if(f.surface().equals("poster")||f.surface().equals("perforated")||f.surface().equals("label"))IDrawing.drawTexture(g,d.x(),d.y(),d.z(),c.x(),c.y(),c.z(),b.x(),b.y(),b.z(),a.x(),a.y(),a.z(),f.u(),f.v(),0,0,normal,color,light);
        }
    }
    @Override public void render(BlockStationDecor.BlockEntity entity,float tick,GraphicsHolder g,int light,int overlay){
        World world=entity.getWorld2();if(world==null)return;DecorSpec s=entity.spec();BlockPos pos=entity.getPos2();
        Direction facing=IBlock.getStatePropertySafe(world,pos,BlockStationDecor.FACING);
        g.push();try{g.translate(.5+s.x(),s.y(),.5+s.z());g.rotateYDegrees(-facing.asRotation()+s.yaw());g.rotateXDegrees(s.pitch());draw(g,s,light,255);}finally{g.pop();}
        if(!OptimizedRenderer.renderingShadows())DecorBrushClient.drawGhosts(pos,g,light);
    }
    @Override public boolean rendersOutsideBoundingBox2(BlockStationDecor.BlockEntity entity){return true;}
    @Override public boolean isInRenderDistance(BlockStationDecor.BlockEntity e,Vector3d camera){BlockPos p=e.getPos2();double dx=camera.getXMapped()-p.getX(),dy=camera.getYMapped()-p.getY(),dz=camera.getZMapped()-p.getZ();return dx*dx+dy*dy+dz*dz<112*112;}
}
