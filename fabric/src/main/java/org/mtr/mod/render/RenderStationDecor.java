package org.mtr.mod.render;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.block.*;
import org.mtr.mod.client.DecorBrushClient;
import org.mtr.mod.decor.*;
import org.mtr.mod.client.IDrawing;
import java.util.*;

/** Geometry, normals and material groups are prepared once, rather than for every frame. */
public final class RenderStationDecor extends BlockEntityRenderer<BlockStationDecor.BlockEntity> {
    private record Quad(DecorMesh.Point a,DecorMesh.Point b,DecorMesh.Point c,DecorMesh.Point d,float u,float v,int rgb,Direction normal){}
    private record Group(String surface,Identifier texture,List<Quad> quads){}
    private static final Map<String,Identifier> TEXTURES=new HashMap<>();
    private static final LinkedHashMap<DecorSpec,List<Group>> BAKED=new LinkedHashMap<>(32,.75f,true);
    private static int retainedFaces;
    private record Retained(DecorSpec spec,List<Group> groups){}
    private static final Map<BlockStationDecor.BlockEntity,Retained> LOADED=new WeakHashMap<>();
    public RenderStationDecor(Argument dispatcher){super(dispatcher);}
    private static List<Group> prepare(DecorSpec spec){
        List<Group> result=BAKED.get(spec);if(result!=null)return result;
        Map<String,List<Quad>> materials=new LinkedHashMap<>();
        for(var f:DecorMesh.get(spec).faces()){
            var a=f.a();var b=f.b();var c=f.c();var d=f.d();
            if(f.boxUv()){a=f.d();b=f.a();c=f.b();d=f.c();}
            else if(f.surface().equals("poster")||f.surface().equals("perforated")||f.surface().equals("label")||f.surface().equals("journey")){b=f.d();d=f.b();}
            float nx=(b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
            float ny=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
            float nz=(b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
            Direction normal=Math.abs(ny)>Math.abs(nx)&&Math.abs(ny)>Math.abs(nz)?ny>0?Direction.UP:Direction.DOWN:Math.abs(nx)>Math.abs(nz)?nx>0?Direction.EAST:Direction.WEST:nz>0?Direction.SOUTH:Direction.NORTH;
            materials.computeIfAbsent(f.surface(),k->new ArrayList<>()).add(new Quad(a,b,c,d,f.u(),f.v(),f.color()&0xFFFFFF,normal));
        }
        List<Group> groups=new ArrayList<>();
        materials.forEach((surface,quads)->groups.add(new Group(surface,surface.equals("poster")&&!spec.image().isEmpty()?new Identifier(spec.image()):TEXTURES.computeIfAbsent(surface,m->new Identifier("mtr","textures/decor/"+m+".png")),List.copyOf(quads))));
        groups.sort(Comparator.comparing(g->g.surface().equals("glass")));result=List.copyOf(groups);
        BAKED.put(spec,result);retainedFaces+=result.stream().mapToInt(g->g.quads().size()).sum();
        var it=BAKED.entrySet().iterator();while((BAKED.size()>96||retainedFaces>180000)&&BAKED.size()>1&&it.hasNext()){var old=it.next();retainedFaces-=old.getValue().stream().mapToInt(g->g.quads().size()).sum();it.remove();}
        return result;
    }
    public static void draw(GraphicsHolder g,DecorSpec spec,int light,int tint){draw(g,spec,prepare(spec),light,tint);}
    private static void draw(GraphicsHolder g,DecorSpec spec,List<Group> groups,int light,int tint){
        for(Group group:groups){
            boolean glass=group.surface().equals("glass"),glow=(group.surface().equals("glow")||group.surface().equals("journey"))&&spec.illuminated();
            Identifier texture=group.surface().equals("journey")?org.mtr.mod.client.DynamicTextureCache.instance.getSignText(spec.label().isBlank()?"ITINÉRAIRE|Choisir une destination":spec.label(),org.mtr.mod.data.IGui.HorizontalAlignment.CENTER,0.1f,0x142C42,0xFFFFFF).identifier:group.surface().equals("label")?org.mtr.mod.client.DynamicTextureCache.instance.getSignText(spec.label(),org.mtr.mod.data.IGui.HorizontalAlignment.CENTER,0.1f,spec.accent(),0xFFFFFF).identifier:group.texture();
            g.createVertexConsumer(glass||tint!=255?MoreRenderLayers.getExteriorTranslucent(texture):glow?MoreRenderLayers.getLight2(texture):MoreRenderLayers.getExterior(texture));
            for(Quad q:group.quads()){
                var a=q.a();var b=q.b();var c=q.c();var d=q.d();int color=q.rgb()|((glass?Math.min(tint,96):tint)<<24);
                IDrawing.drawTexture(g,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),c.x(),c.y(),c.z(),d.x(),d.y(),d.z(),0,0,q.u(),q.v(),q.normal(),color,glow?GraphicsHolder.getDefaultLight():light);
            }
        }
    }
    @Override public void render(BlockStationDecor.BlockEntity entity,float tick,GraphicsHolder g,int light,int overlay){
        World world=entity.getWorld2();if(world==null)return;DecorSpec s=entity.spec();if(s.kind()==DecorKind.WAYPOINT){var player=MinecraftClient.getInstance().getPlayerMapped();if(player==null||!player.isHolding(org.mtr.mod.Items.BRUSH.get().asItem()))return;}BlockPos pos=entity.getPos2();
        Direction facing=IBlock.getStatePropertySafe(world,pos,BlockStationDecor.FACING);
        var retained=LOADED.get(entity);if(retained==null||!retained.spec().equals(s)){retained=new Retained(s,prepare(s));LOADED.put(entity,retained);}
        g.push();try{g.translate(.5+s.x(),s.y(),.5+s.z());g.rotateYDegrees(-facing.asRotation()+s.yaw());g.rotateXDegrees(s.pitch());draw(g,s,retained.groups(),light,255);}finally{g.pop();}
        if(!OptimizedRenderer.renderingShadows())DecorBrushClient.drawGhosts(pos,g,light);
    }
    @Override public boolean rendersOutsideBoundingBox2(BlockStationDecor.BlockEntity entity){return false;}
    @Override public boolean isInRenderDistance(BlockStationDecor.BlockEntity e,Vector3d camera){BlockPos p=e.getPos2();double dx=camera.getXMapped()-p.getX(),dy=camera.getYMapped()-p.getY(),dz=camera.getZMapped()-p.getZ();return dx*dx+dy*dy+dz*dz<112*112;}
}
