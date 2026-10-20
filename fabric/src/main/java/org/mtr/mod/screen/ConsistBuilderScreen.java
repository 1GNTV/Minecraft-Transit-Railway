package org.mtr.mod.screen;

import org.mtr.core.data.*;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.*;
import org.mtr.mod.builder.*;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.render.TrainSilhouetteCache;
import org.mtr.mod.resource.VehicleResource;
import java.util.*;

/** Staged, proportional consist editor. All mutations use the native MTR update packet. */
public final class ConsistBuilderScreen extends ScreenExtension {
    private final Siding siding;
    private final ScreenExtension previous;
    private final List<VehicleResource> catalog=new ArrayList<>();
    private final Map<String,VehicleResource> byId=new HashMap<>();
    private final List<VehicleCar> draft=new ArrayList<>();
    private final ConsistDraft<VehicleCar> edit=new ConsistDraft<>(draft);
    private List<VehicleResource> matches=List.of();
    private TextFieldWidgetExtension search,presetName;
    private int page,columns,rows,cellWidth;
    private String query="",name="",message="";
    private boolean pressed,dragging;
    private double pressX,pressY,scroll,mouseX,mouseY;
    private long lastFrame=System.nanoTime(),deletedAt;
    private final IdentityHashMap<VehicleCar,Double> animatedX=new IdentityHashMap<>();
    private List<VehicleCar> deleted=List.of();

    public ConsistBuilderScreen(Siding siding,ScreenExtension previous){
        this.siding=siding;this.previous=previous;draft.addAll(siding.getVehicleCars());
        CustomResourceLoader.iterateVehicles(siding.getTransportMode(),r->{catalog.add(r);byId.put(r.getId(),r);});
        catalog.sort(Comparator.comparing(r->r.getName().getString()));filter();
    }
    private void filter(){String q=query.toLowerCase(Locale.ROOT);matches=catalog.stream().filter(r->(r.getName().getString()+" "+r.getId()).toLowerCase(Locale.ROOT).contains(q)).toList();page=0;}
    private void refresh(){MinecraftClient.getInstance().openScreen(new Screen(this));}
    private void button(int x,int y,int w,String text,Runnable action){addChild(new ClickableWidget(BuilderUi.button(x,y,w,text,action)));}
    private int carY(){return height-100;}
    private int perPage(){return columns*rows;}
    private int cardY(int n){return 50+(n/columns)*48;}
    private int cardX(int n){return 12+(n%columns)*cellWidth;}
    @Override protected void init2(){
        super.init2();columns=Math.max(1,(width-24)/145);cellWidth=(width-24)/columns;
        rows=Math.max(1,(height-206)/48);
        search=new TextFieldWidgetExtension(12,27,width-24,20,120,TextCase.DEFAULT,null,"Rechercher un train");
        search.setText2(query);search.setChangedListener2(t->{query=t;filter();});addChild(new ClickableWidget(search));
        int pagerY=height-144;
        button(12,pagerY,25,"‹",()->page=Math.max(0,page-1));
        button(40,pagerY,25,"›",()->page=Math.min(Math.max(0,(matches.size()-1)/perPage()),page+1));
        button(width-160,pagerY,46,"↶",edit::undo);button(width-110,pagerY,46,"↷",edit::redo);button(width-60,pagerY,48,"+1",()->edit.duplicate(siding.getTransportMode().maxLength));
        button(width-82,height-121,70,"Inverser",edit::reverse);
        presetName=new TextFieldWidgetExtension(12,height-68,Math.max(65,width-190),18,64,TextCase.DEFAULT,null,"Nom de la composition");
        presetName.setText2(name);presetName.setChangedListener2(t->name=t);addChild(new ClickableWidget(presetName));
        button(width-172,height-69,76,"Enregistrer",this::savePreset);
        button(width-92,height-69,80,"Mes rames…",()->MinecraftClient.getInstance().openScreen(new Screen(new ConsistPresetScreen(this,siding.getTransportMode().name(),chosen->{name=chosen;loadPreset();refresh();}))));
        button(12,height-28,66,"Annuler",this::discard);
        button(84,height-28,94,"Classique",()->MinecraftClient.getInstance().openScreen(new Screen(new VehicleSelectorScreen(siding,previous))));
        button(width-104,height-28,92,"Appliquer",this::apply);
    }
    private void add(VehicleResource r){
        if(draft.size()>=siding.getTransportMode().maxLength){message="Nombre maximal de voitures atteint.";return;}
        edit.add(car(r));message="";scroll=0;
    }
    private static VehicleCar car(VehicleResource r){return new VehicleCar(r.getId(),r.getLength(),r.getWidth(),r.getBogie1Position(),r.getBogie2Position(),r.getCouplingPadding1(),r.getCouplingPadding2());}
    public double length(){return Siding.getTotalVehicleLength(new ObjectArrayList<>(draft));}
    private void apply(){
        if(length()>siding.getRailLength()+.001){message="Rame trop longue pour cette voie de garage.";return;}
        if(draft.stream().anyMatch(c->!byId.containsKey(c.getVehicleId()))){message="Ressource de train manquante : recharge le pack avant de valider.";return;}
        siding.setVehicleCars(new ObjectArrayList<>(draft));InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addSiding(siding)));discard();
    }
    private void discard(){MinecraftClient.getInstance().openScreen(previous==null?null:new Screen(previous));}
    private void savePreset(){try{ConsistPresets.save(siding.getTransportMode().name(),name,draft.stream().map(VehicleCar::getVehicleId).toList());message="Composition enregistrée : "+name;}catch(Exception e){message="Enregistrement impossible : "+e.getMessage();}}
    private void loadPreset(){
        var ids=ConsistPresets.load(siding.getTransportMode().name()).get(name);
        if(ids==null){message="Choisis une composition enregistrée.";return;}
        if(ids.stream().anyMatch(id->!byId.containsKey(id))){message="Pack absent : la composition actuelle est conservée.";return;}
        if(ids.size()>siding.getTransportMode().maxLength){message="Composition incompatible avec ce mode de transport.";return;}
        edit.replace(ids.stream().map(id->car(byId.get(id))).toList());scroll=0;message="Composition chargée. Échap ou Appliquer pour enregistrer.";
    }
    private void profile(GraphicsHolder g,VehicleResource r,int index,int total,int x,int y,int w,int h){
        if(w<=0||h<=0)return;
        if(r==null){BuilderUi.text(g,"?",x,y+4,0xFFFFAD82);return;}
        Identifier id=TrainSilhouetteCache.request(r,index,total);
        if(id!=null){int dw=Math.max(1,Math.min(w,h*4)),dh=Math.max(1,Math.min(h,w/4));GuiDrawing d=new GuiDrawing(g);d.beginDrawingTexture(id);d.drawTexture(x+(w-dw)/2,y+(h-dh)/2,x+(w+dw)/2,y+(h+dh)/2,0,1,1,0);d.finishDrawingTexture();}
        else if(w>65)BuilderUi.text(g,BuilderUi.fit(TrainSilhouetteCache.status(r,index,total),w),x,y+h/2,BuilderUi.MUTED);
    }
    private double pixelsPerMetre(){
        double metres=0;for(VehicleCar c:draft)metres+=Math.max(.1,c.getLength());
        return Math.max(1.7,Math.min(6,(width-32-draft.size()*3)/Math.max(1,metres)));
    }
    private double[] positions(){double[] p=new double[draft.size()+1];p[0]=14-scroll;double scale=pixelsPerMetre();for(int i=0;i<draft.size();i++)p[i+1]=p[i]+Math.max(.1,draft.get(i).getLength())*scale+3;return p;}
    private int hit(double x){double[] p=positions();for(int i=0;i<draft.size();i++)if(x>=p[i]&&x<p[i+1]-3)return i;return -1;}
    private int insertion(double x){double[] p=positions();for(int i=0;i<draft.size();i++)if(x<(p[i]+p[i+1])/2)return i;return draft.size();}
    private void deleteSelection(){deleted=edit.delete();deletedAt=System.nanoTime();message=deleted.isEmpty()?"":"Voitures supprimées · Ctrl+Z pour rétablir";}
    @Override public void render(GraphicsHolder g,int mx,int my,float dt){
        mouseX=mx;mouseY=my;TrainSilhouetteCache.pump();
        long now=System.nanoTime();double smooth=1-Math.exp(-Math.min(.1,(now-lastFrame)/1e9)*18);lastFrame=now;
        BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);
        BuilderUi.text(g,"SILLON  /  COMPOSITION DE RAME",12,11,BuilderUi.ACCENT);
        page=Math.min(page,Math.max(0,(matches.size()-1)/perPage()));
        for(int n=0;n<perPage()&&page*perPage()+n<matches.size();n++){
            VehicleResource r=matches.get(page*perPage()+n);int x=cardX(n),y=cardY(n),w=cellWidth-5;
            boolean hover=mx>=x&&mx<x+w&&my>=y&&my<y+44;
            BuilderUi.rect(g,x,y,w,44,hover?0xFF30495A:BuilderUi.PANEL);
            BuilderUi.text(g,BuilderUi.fit(r.getName().getString(),w-12),x+6,y+4,BuilderUi.TEXT);
            profile(g,r,0,1,x+6,y+15,w-12,22);
            BuilderUi.text(g,String.format(Locale.ROOT,"%.1f m",r.getLength()),x+6,y+34,BuilderUi.MUTED);
        }
        BuilderUi.text(g,(page+1)+"/"+Math.max(1,(matches.size()+perPage()-1)/perPage())+" · "+matches.size()+" modèles",70,height-138,BuilderUi.MUTED);
        BuilderUi.text(g,draft.size()+" voitures · "+String.format(Locale.ROOT,"%.1f / %.1f m",length(),siding.getRailLength()),12,height-115,length()>siding.getRailLength()?0xFFFFAD82:BuilderUi.TEXT);
        BuilderUi.rect(g,8,carY()-2,width-16,30,BuilderUi.PANEL);
        double[] p=positions();
        for(int i=0;i<draft.size();i++){
            VehicleCar c=draft.get(i);double previousX=animatedX.getOrDefault(c,p[i]),x=previousX+(p[i]-previousX)*smooth;animatedX.put(c,x);
            int w=(int)(p[i+1]-p[i]-3);
            if(x<12||x+w>width-12)continue;
            BuilderUi.rect(g,x,carY(),w,26,edit.selected(i)?0xFF32616A:0xFF243D4A);
            profile(g,byId.get(c.getVehicleId()),i,draft.size(),(int)x+2,carY()+3,w-4,21);
            if(edit.selected(i))BuilderUi.rect(g,x,carY()+25,w,1,BuilderUi.ACCENT);
        }
        animatedX.keySet().removeIf(c->!draft.contains(c));
        if(draft.isEmpty())BuilderUi.text(g,"Clique sur un train pour commencer ta rame",17,carY()+9,BuilderUi.MUTED);
        if(dragging&&my>=carY()-8&&my<carY()+38){int at=insertion(mx);BuilderUi.rect(g,p[at]-2,carY()-3,2,32,BuilderUi.ACCENT);}
        if(deletedAt!=0){double t=(now-deletedAt)/280_000_000.0;if(t<1){int alpha=(int)((1-t)*180);BuilderUi.rect(g,12,carY()-t*12,width-24,26*(1-t),(alpha<<24)|0xE75D68);}else{deleted=List.of();deletedAt=0;}}
        BuilderUi.text(g,BuilderUi.fit(message.isEmpty()?"Maj : sélection multiple · Glisser · Ctrl+Z : rétablir · Échap : enregistrer":message,width-24),12,height-42,message.isEmpty()?BuilderUi.MUTED:0xFFFFD09A);
        super.render(g,mx,my,dt);
        if(dragging){
            boolean trash=my<54;
            for(int y=0;y<54;y+=2)BuilderUi.rect(g,0,y,width,2,(((int)((1-y/54.0)*(trash?220:140)))<<24)|0xB22F45);
            int tx=width/2-45;BuilderUi.text(g,"Supprimer "+edit.selectedCount(),tx,20,0xFFFFE5E9);
            BuilderUi.rect(g,tx-18,20,10,12,0xFFFFE5E9);BuilderUi.rect(g,tx-20,16,14,2,0xFFFFE5E9);BuilderUi.rect(g,tx-15,13,4,2,0xFFFFE5E9);
            BuilderUi.rect(g,mx+8,my+8,Math.min(width-24,120),24,0xE841626F);
            BuilderUi.text(g,edit.selectedCount()+" voiture(s)",mx+14,my+16,BuilderUi.TEXT);
        }
    }
    @Override public boolean mouseClicked2(double mx,double my,int button){
        if(super.mouseClicked2(mx,my,button))return true;if(button!=0)return false;
        if(my>=carY()&&my<carY()+28){
            int i=hit(mx);if(i>=0){
                if(hasShiftDown2()||hasControlDown2()||!edit.selected(i))edit.select(i,hasShiftDown2(),hasControlDown2());
                pressed=true;pressX=mx;pressY=my;return true;
            }
        }
        for(int n=0;n<perPage()&&page*perPage()+n<matches.size();n++){
            if(mx>=cardX(n)&&mx<cardX(n)+cellWidth-5&&my>=cardY(n)&&my<cardY(n)+44){add(matches.get(page*perPage()+n));return true;}
        }
        return false;
    }
    @Override public boolean mouseDragged2(double x,double y,int button,double dx,double dy){if(pressed&&button==0){if(Math.hypot(x-pressX,y-pressY)>4)dragging=true;return true;}return super.mouseDragged2(x,y,button,dx,dy);}
    @Override public boolean mouseReleased2(double mx,double my,int button){
        if(pressed&&button==0){if(dragging){if(my<54)deleteSelection();else if(my>=carY()-8&&my<carY()+38)edit.move(insertion(mx));}pressed=dragging=false;return true;}
        return super.mouseReleased2(mx,my,button);
    }
    @Override public boolean mouseScrolled2(double x,double y,double amount){
        if(y>=carY()-4&&y<carY()+32){double[] p=positions();double total=p[p.length-1]+scroll-14;scroll=Math.max(0,Math.min(Math.max(0,total-width+32),scroll-amount*28));}
        else page=Math.max(0,Math.min(Math.max(0,(matches.size()-1)/perPage()),page+(amount>0?-1:1)));return true;
    }
    @Override public boolean keyPressed2(int key,int scan,int modifiers){
        if(search.isFocused2()||presetName.isFocused2())return super.keyPressed2(key,scan,modifiers);
        if(hasControlDown2()&&key==90){if(hasShiftDown2())edit.redo();else edit.undo();return true;}
        if(hasControlDown2()&&key==89){edit.redo();return true;}
        if(hasControlDown2()&&key==68){edit.duplicate(siding.getTransportMode().maxLength);return true;}
        if(key==261){deleteSelection();return true;}return super.keyPressed2(key,scan,modifiers);
    }
    @Override public void tick2(){search.tick2();presetName.tick2();}
    @Override public void onClose2(){apply();}
    @Override public boolean isPauseScreen2(){return false;}
}
