package org.mtr.mod.screen;

import java.util.*;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.data.PanamBulletins;
import org.mtr.mod.data.PanamNewsClient;
import org.mtr.mod.packet.PacketPanamBulletinEdit;

/** Searchable MTR-native operations centre. Both columns reuse DashboardList,
 * including route-name search, line colour badges, result paging and row selection. */
public final class PanamControlScreen extends ScreenExtension implements IGui {
    private final DashboardList routes;
    private final DashboardList announcements;
    private final TextFieldWidgetExtension message;
    private final ButtonWidgetExtension prevType,nextType,newButton,save,delete,close;
    private static final String[] KINDS={"Info","Travaux","Danger","Arrêt complet"};
    private static final int[] COLOURS={0x80B8FF,0xFFC046,0xF87855,0xE63545};
    private String routeFilter="",noticeFilter="";
    private long selectedRoute=-1,revision=-1,networkRevision=-1;
    private int selectedNotice=-1,type,margin,leftWidth,rightX,editorY;
    private String feedback="Utilisez la loupe pour sélectionner une ligne ou une annonce.";

    public PanamControlScreen() {
        routes=new DashboardList(this::selectRoute,null,null,null,null,null,null,()->routeFilter,s->routeFilter=s);
        announcements=new DashboardList(this::selectNotice,null,null,null,null,null,null,()->noticeFilter,s->noticeFilter=s);
        message=new TextFieldWidgetExtension(0,0,0,SQUARE_SIZE,512,TextCase.DEFAULT,null,"Texte de l'annonce…");
        prevType=button("< Type",()->type=Math.floorMod(type-1,KINDS.length));
        nextType=button("Type >",()->type=Math.floorMod(type+1,KINDS.length));
        newButton=button("+ Nouvelle",this::newNotice);
        save=button("Enregistrer",this::saveNotice);
        delete=button("Supprimer",this::deleteNotice);
        close=button("Terminé",this::onClose2);
    }
    private static ButtonWidgetExtension button(String text,Runnable run){
        ButtonWidgetExtension b=new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,ignored->run.run());
        b.setMessage2(new Text(TextHelper.literal(text).data));return b;
    }
    private List<PanamBulletins.Bulletin> current(){
        return PanamBulletins.decode(selectedRoute==-1?PanamNewsClient.global():PanamNewsClient.forRoute(selectedRoute));
    }
    private void updateRoutes(){
        ObjectArrayList<DashboardListItem> data=new ObjectArrayList<>();
        data.add(new DashboardListItem(-1,"Tout le réseau ["+PanamBulletins.decode(PanamNewsClient.global()).size()+"]",0xF5F5F5));
        for(SimplifiedRoute r:MinecraftClientData.getInstance().simplifiedRouteIdMap.values()){
            int count=PanamBulletins.decode(PanamNewsClient.forRoute(r.getId())).size();
            data.add(new DashboardListItem(r.getId(),r.getName()+(count>0?" ["+count+"]":""),r.getColor()));
        }
        routes.setData(data,true,false,false,false,false,false);
        networkRevision=MinecraftClientData.getInstance().networkRevision;
    }
    private void updateNotices(){
        List<PanamBulletins.Bulletin> list=current();
        ObjectArrayList<DashboardListItem> data=new ObjectArrayList<>();
        for(int i=0;i<list.size();i++){
            PanamBulletins.Bulletin b=list.get(i);
            String shortMessage=b.message.replace('\n',' ').replace('\r',' ');
            if(shortMessage.length()>70)shortMessage=shortMessage.substring(0,67)+"…";
            data.add(new DashboardListItem(i,(i==selectedNotice?"▶ ":"")+(i+1)+". ["+KINDS[b.type]+"] "+shortMessage,COLOURS[b.type]));
        }
        announcements.setData(data,true,false,false,false,false,false);
        revision=PanamNewsClient.revision();
    }
    private void selectRoute(DashboardListItem row,int ignored){
        selectedRoute=row.id;selectedNotice=-1;noticeFilter="";announcements.clearSearch();message.setText2("");
        feedback="Sélectionnez une annonce dans la liste de droite, ou créez-en une.";
        updateNotices();
    }
    private void selectNotice(DashboardListItem row,int ignored){
        int n=(int)row.id;List<PanamBulletins.Bulletin> list=current();
        if(n<0||n>=list.size())return;
        selectedNotice=n;type=list.get(n).type;message.setText2(list.get(n).message);
        feedback="Annonce "+(n+1)+" sélectionnée : modifiez le texte puis Enregistrer.";
        updateNotices();
    }
    private void newNotice(){
        selectedNotice=-1;type=0;message.setText2("");
        feedback="Nouvelle annonce : choisissez le type et saisissez un texte.";
        updateNotices();
    }
    private void saveNotice(){
        String s=message.getText2().trim();List<PanamBulletins.Bulletin> list=current();
        if(s.isEmpty()){feedback="Le texte de l'annonce ne peut pas être vide.";return;}
        if(selectedNotice<0&&list.size()>=8){feedback="Maximum huit annonces par ligne ou réseau.";return;}
        int action=selectedNotice<0?0:1;
        int target=selectedNotice<0?0:selectedNotice;
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketPanamBulletinEdit(selectedRoute,action,target,type,s));
        if(action==0)selectedNotice=list.size();
        feedback="Enregistrement envoyé au serveur…";
    }
    private void deleteNotice(){
        if(selectedNotice<0||selectedNotice>=current().size()){
            feedback="Sélectionnez d'abord l'annonce à supprimer.";return;
        }
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketPanamBulletinEdit(selectedRoute,2,selectedNotice,type,""));
        selectedNotice=-1;message.setText2("");feedback="Suppression envoyée au serveur…";
    }
    @Override protected void init2(){
        super.init2();
        margin=Math.max(8,width/40);leftWidth=Math.max(115,(width-3*margin)/2);rightX=leftWidth+2*margin;
        editorY=Math.max(120,height-93);
        routes.x=margin;routes.y=26;routes.width=leftWidth;routes.height=Math.max(58,editorY-32);
        announcements.x=rightX;announcements.y=26;announcements.width=leftWidth;announcements.height=routes.height;
        routes.init(this::addChild);announcements.init(this::addChild);
        IDrawing.setPositionAndWidth(prevType,margin,editorY,leftWidth/3);
        IDrawing.setPositionAndWidth(nextType,rightX+leftWidth*2/3,editorY,leftWidth/3);
        IDrawing.setPositionAndWidth(message,margin,editorY+20,width-2*margin);
        int y=editorY+43,cell=(width-2*margin)/4;
        IDrawing.setPositionAndWidth(newButton,margin,y,cell);
        IDrawing.setPositionAndWidth(save,margin+cell,y,cell);
        IDrawing.setPositionAndWidth(delete,margin+cell*2,y,cell);
        IDrawing.setPositionAndWidth(close,margin+cell*3,y,width-2*margin-cell*3);
        for(ButtonWidgetExtension b:new ButtonWidgetExtension[]{prevType,nextType,newButton,save,delete,close})addChild(new ClickableWidget(b));
        addChild(new ClickableWidget(message));
        updateRoutes();updateNotices();
    }
    @Override public void tick2(){
        routes.tick();announcements.tick();message.tick2();
        if(networkRevision!=MinecraftClientData.getInstance().networkRevision)updateRoutes();
        if(revision!=PanamNewsClient.revision()){
            List<PanamBulletins.Bulletin> list=current();
            if(selectedNotice>=list.size())selectedNotice=-1;
            if(selectedNotice>=0){
                PanamBulletins.Bulletin selected=list.get(selectedNotice);
                message.setText2(selected.message);type=selected.type;
            }
            updateRoutes();updateNotices();
            feedback="Annonces synchronisées.";
        }
    }
    @Override public void render(GraphicsHolder g,int mouseX,int mouseY,float delta){
        renderBackground(g);
        g.drawText("PANAM — Informations voyageurs",margin,5,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText("LIGNES (recherche MTR)",margin,17,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText("ANNONCES EXISTANTES",rightX,17,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        String selection="Tout le réseau";
        SimplifiedRoute sr=MinecraftClientData.getInstance().simplifiedRouteIdMap.get(selectedRoute);
        if(sr!=null)selection=sr.getName();
        g.drawText("Diffusion : "+selection,margin+leftWidth/3+6,editorY+5,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText("Type : "+KINDS[type],rightX+6,editorY+5,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText(feedback,margin,Math.min(height-10,editorY+67),ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        routes.render(g);announcements.render(g);
        super.render(g,mouseX,mouseY,delta);
    }
    @Override public void mouseMoved2(double mouseX,double mouseY){
        routes.mouseMoved(mouseX,mouseY);announcements.mouseMoved(mouseX,mouseY);
    }
    @Override public boolean mouseScrolled2(double mouseX,double mouseY,double amount){
        routes.mouseScrolled(mouseX,mouseY,amount);announcements.mouseScrolled(mouseX,mouseY,amount);
        return super.mouseScrolled2(mouseX,mouseY,amount);
    }
    @Override public boolean isPauseScreen2(){return false;}
}
