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
import org.mtr.mod.screen.EditStationScreen;
import org.mtr.core.data.StationExit;

/** Cached texture signage. Rendered inside block entity shader context, never in a shadow pass. */
public final class RenderSillonSignage extends BlockEntityRenderer<BlockSillonSignage.BlockEntity> {
    public RenderSillonSignage(Argument dispatcher){super(dispatcher);}
    @Override public void render(BlockSillonSignage.BlockEntity entity,float tickDelta,GraphicsHolder g,int light,int overlay){
        if(OptimizedRenderer.renderingShadows())return;
        World world=entity.getWorld2();if(world==null)return;
        BlockPos pos=entity.getPos2();
        SillonSignageSpec spec=entity.document();
        if(spec.stationAuto()){
            Station near=InitClient.findStation(pos);
            if(near!=null){
                spec=spec.withTitle(near.getName());
                // For MTR stations with configured exits, generate an actual
                // destination list. No hardcoded Paris street names.
                if(spec.body().isBlank() && ("exit".equals(spec.style())||"map".equals(spec.style()))){
                    StringBuilder descriptions=new StringBuilder();
                    int count=0;
                    for(StationExit exit:EditStationScreen.getStationExits(near,true)){
                        if(count++>=8)break;
                        if(descriptions.length()>0)descriptions.append('|');
                        descriptions.append(exit.getName());
                        if(!exit.getDestinations().isEmpty())
                            descriptions.append("  ").append(exit.getDestinations().get(0));
                    }
                    if(descriptions.length()>0)spec=spec.withBody(descriptions.toString());
                }
            }
        }
        Identifier texture=DynamicTextureCache.instance.getSillonSignage(spec).identifier;
        Direction facing=IBlock.getStatePropertySafe(world,pos,BlockSillonSignage.FACING);
        double w=spec.width(),h=spec.height();
        int faces=spec.doubleSided()?2:1;
        for(int face=0;face<faces;face++){
            g.push();
            try {
                double pivotY="standing".equals(spec.mount())?.5+h/2:
                    "ceiling".equals(spec.mount())?1-h/2:
                    "hanging".equals(spec.mount())?.78-h/2:.50;
                g.translate(.5,.5+(pivotY-.5),.5);
                g.rotateYDegrees(-facing.asRotation()+spec.yaw()+face*180);
                g.rotateXDegrees(spec.pitch());
                g.rotateZDegrees(spec.roll()+180);
                double z="flag".equals(spec.mount())?.35:.499;
                g.translate(-w/2,-h/2,z);
                // Pixel dimensions here are world units; only the texture is rasterized.
                g.createVertexConsumer(spec.illuminated()
                    ?MoreRenderLayers.getLight2(texture):MoreRenderLayers.getExterior(texture));
                IDrawing.drawTexture(g,0,0,(float)w,(float)h,0,0,1,1,Direction.NORTH,-1,
                    spec.illuminated()?GraphicsHolder.getDefaultLight():light);
            }finally {g.pop();}
        }
    }
    @Override public boolean rendersOutsideBoundingBox2(BlockSillonSignage.BlockEntity entity){return true;}
    @Override public boolean isInRenderDistance(BlockSillonSignage.BlockEntity entity,Vector3d camera){
        BlockPos pos=entity.getPos2();
        double dx=camera.getXMapped()-pos.getX(),dy=camera.getYMapped()-pos.getY(),dz=camera.getZMapped()-pos.getZ();
        return dx*dx+dy*dy+dz*dz<128*128;
    }
}
