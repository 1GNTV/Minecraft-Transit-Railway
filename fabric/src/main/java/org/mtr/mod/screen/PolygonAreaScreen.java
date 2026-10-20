package org.mtr.mod.screen;
import org.mtr.core.data.*;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.builder.*;
import java.util.*;

/** Local staged contour: handles, edge insertion, point removal, pan/zoom, rectangle fallback. */
public final class PolygonAreaScreen extends ScreenExtension {
    private final AreaBase<?,?> area;
    private final ScreenExtension previous;
    private final List<RegionPolygon.Point> points=new ArrayList<>();
    private double centerX,centerZ,scale;
    private int moving=-1;
    private boolean closed=true,rectangle,pan;
    private String message="";
    public PolygonAreaScreen(AreaBase<?,?> area,ScreenExtension previous){
        this.area=area;this.previous=previous;
        if(AreaBase.validCorners(area)){points.addAll(AreaShape.outline(area).points());centerX=(area.getMinX()+area.getMaxX())/2.0;centerZ=(area.getMinZ()+area.getMaxZ())/2.0;}
        else{closed=false;var p=MinecraftClient.getInstance().getPlayerMapped();if(p!=null){centerX=p.getX();centerZ=p.getZ();}}
    }
    @Override protected void init2(){
        super.init2();if(scale==0)scale=Math.max(.1,Math.min((width-60)/Math.max(20,area.getMaxX()-area.getMinX()),(height-130)/Math.max(20,area.getMaxZ()-area.getMinZ())));
        int w=(width-36)/3;
        addChild(new ClickableWidget(BuilderUi.button(12,32,w,"Nouveau tracé",()->{points.clear();closed=false;rectangle=false;})));
        addChild(new ClickableWidget(BuilderUi.button(18+w,32,w,"Rectangle",()->{points.clear();closed=false;rectangle=true;})));
        addChild(new ClickableWidget(BuilderUi.button(24+2*w,32,w,"Retirer point",()->{if(!points.isEmpty()){points.remove(points.size()-1);closed=false;rectangle=false;}})));
        addChild(new ClickableWidget(BuilderUi.button(12,height-28,76,"Annuler",this::discard)));
        addChild(new ClickableWidget(BuilderUi.button(width-116,height-28,104,"Enregistrer",this::save)));
    }
    private double sx(double x){return width/2.0+(x-centerX)*scale;}
    private double sz(double z){return (height-12)/2.0+(z-centerZ)*scale;}
    private RegionPolygon.Point world(double x,double y){return new RegionPolygon.Point(Math.rint(centerX+(x-width/2.0)/scale),Math.rint(centerZ+(y-(height-12)/2.0)/scale));}
    private boolean inMap(double x,double y){return x>=12&&x<=width-12&&y>=60&&y<=height-66;}
    private int hit(double x,double y){for(int i=0;i<points.size();i++)if(Math.hypot(sx(points.get(i).x())-x,sz(points.get(i).z())-y)<7)return i;return -1;}
    private void save(){
        try{
            if(!closed)throw new IllegalArgumentException("Ferme le contour en cliquant sur son premier point.");
            RegionPolygon polygon=new RegionPolygon(points);AreaShape.apply(area,rectangle?null:polygon);
            if(rectangle)area.setCorners(new org.mtr.core.data.Position((long)polygon.minX(),area.getMinY(),(long)polygon.minZ()),new org.mtr.core.data.Position((long)polygon.maxX(),area.getMaxY(),(long)polygon.maxZ()));
            var data=MinecraftClientData.getDashboardInstance();var update=new UpdateDataRequest(data);
            if(area instanceof Station station)update.addStation(station);else if(area instanceof Depot depot)update.addDepot(depot);
            InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(update));discard();
        }catch(RuntimeException e){message=e.getMessage();}
    }
    private void discard(){MinecraftClient.getInstance().openScreen(new Screen(previous));}
    @Override public void onClose2(){save();}
    @Override public void render(GraphicsHolder g,int mx,int my,float dt){
        BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"SILLON  /  CONTOUR DE "+BuilderUi.fit(area.getName(),width-170),12,12,BuilderUi.ACCENT);
        BuilderUi.rect(g,12,60,width-24,height-126,BuilderUi.PANEL);
        var d=new GuiDrawing(g);d.beginDrawingRectangle();
        for(Platform platform:MinecraftClientData.getDashboardInstance().platforms){var p=platform.getMidPosition();double x=sx(p.getX()),y=sz(p.getZ());if(inMap(x-3,y-3)&&inMap(x+3,y+3))d.drawRectangle(x-3,y-3,x+3,y+3,0xFFCEDEE8);}
        try{if(closed)PolygonDrawing.draw(d,new RegionPolygon(points),this::sx,this::sz,12,60,width-12,height-66,0x403BCAB6,BuilderUi.ACCENT);}catch(RuntimeException ignored){}
        if(!closed)for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);PolygonDrawing.line(d,sx(a.x()),sz(a.z()),sx(b.x()),sz(b.z()),12,60,width-12,height-66,2,BuilderUi.ACCENT);}
        if(!closed&&!points.isEmpty()&&inMap(mx,my)){var p=points.get(points.size()-1);PolygonDrawing.line(d,sx(p.x()),sz(p.z()),mx,my,12,60,width-12,height-66,1,0x889EDAD0);}
        d.finishDrawingRectangle();
        for(int i=0;i<points.size();i++){var p=points.get(i);double x=sx(p.x()),y=sz(p.z());if(inMap(x-4,y-4)&&inMap(x+4,y+4)){BuilderUi.rect(g,x-4,y-4,8,8,i==hit(mx,my)?0xFFFFFFFF:BuilderUi.ACCENT);if(width>450)BuilderUi.text(g,Integer.toString(i+1),(int)x+6,(int)y-4,BuilderUi.TEXT);}}
        BuilderUi.text(g,BuilderUi.fit(message.isEmpty()?"Cliquer : tracer · Glisser : point · Clic droit : retirer · Molette : zoom":message,width-24),12,height-55,message.isEmpty()?BuilderUi.MUTED:0xFFFFAD82);
        if(height>300)BuilderUi.text(g,"Maj + clic sur un côté : ajouter un point · Bouton central : déplacer la carte",12,height-42,BuilderUi.MUTED);
        super.render(g,mx,my,dt);
    }
    @Override public boolean mouseClicked2(double x,double y,int button){
        if(super.mouseClicked2(x,y,button))return true;if(!inMap(x,y))return false;
        int at=hit(x,y);if(button==2){pan=true;return true;}
        if(button==1&&at>=0){points.remove(at);rectangle=false;if(points.size()<3)closed=false;return true;}
        if(button!=0)return false;
        if(!closed){
            if(at==0&&points.size()>=3){closed=true;message="";return true;}
            var p=world(x,y);if(rectangle&&points.size()==1){var a=points.get(0);if(a.x()==p.x()||a.z()==p.z()){message="Le rectangle doit avoir une surface.";return true;}points.clear();points.addAll(RegionPolygon.rectangle(Math.min(a.x(),p.x()),Math.min(a.z(),p.z()),Math.max(a.x(),p.x()),Math.max(a.z(),p.z())).points());closed=true;}
            else if(points.size()<64)points.add(p);return true;
        }
        if(at>=0){moving=at;return true;}
        if(hasShiftDown2()&&points.size()<64){
            int best=-1;double distance=8;
            for(int i=0;i<points.size();i++){var a=points.get(i);var b=points.get((i+1)%points.size());double dx=sx(b.x())-sx(a.x()),dy=sz(b.z())-sz(a.z()),t=Math.max(0,Math.min(1,((x-sx(a.x()))*dx+(y-sz(a.z()))*dy)/(dx*dx+dy*dy))),dd=Math.hypot(x-sx(a.x())-dx*t,y-sz(a.z())-dy*t);if(dd<distance){distance=dd;best=i;}}
            if(best>=0){points.add(best+1,world(x,y));moving=best+1;rectangle=false;}return true;
        }
        return true;
    }
    @Override public boolean mouseDragged2(double x,double y,int button,double dx,double dy){if(pan){centerX-=dx/scale;centerZ-=dy/scale;return true;}if(moving>=0&&inMap(x,y)){points.set(moving,world(x,y));rectangle=false;return true;}return super.mouseDragged2(x,y,button,dx,dy);}
    @Override public boolean mouseReleased2(double x,double y,int button){moving=-1;pan=false;return super.mouseReleased2(x,y,button);}
    @Override public boolean mouseScrolled2(double x,double y,double amount){if(!inMap(x,y))return false;double before=scale;scale=MapGeometry.zoom(scale,amount);centerX=MapGeometry.anchoredCenter(centerX,x-width/2.0,before,scale);centerZ=MapGeometry.anchoredCenter(centerZ,y-(height-12)/2.0,before,scale);return true;}
    @Override public boolean isPauseScreen2(){return false;}
}
