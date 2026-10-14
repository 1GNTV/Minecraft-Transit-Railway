package org.mtr.mod.screen;

import java.util.*;
import org.mtr.core.data.SimplifiedRoute;
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

/** In-game network-wide bulletin centre, opened by /mtr panam. */
public final class PanamControlScreen extends ScreenExtension implements IGui {
    private final ArrayList<Long> routes=new ArrayList<>();
    private final ButtonWidgetExtension previousScope,nextScope,previousBulletin,nextBulletin,previousType,nextType,add,save,remove;
    private final TextFieldWidgetExtension text;
    private final String[] kinds={"Information","Travaux","Danger","Arrêt complet"};
    private int scopeIndex,bulletinIndex,type;
    private long seenRevision=-1;
    private boolean filling;
    private int x, contentWidth;

    public PanamControlScreen(){
        super();
        routes.add(-1L);
        for(long route : MinecraftClientData.getInstance().simplifiedRouteIdMap.keySet()) routes.add(route);
        Collections.sort(routes.subList(1,routes.size()));
        previousScope=button("< Ligne",()->changeScope(-1));
        nextScope=button("Ligne >",()->changeScope(1));
        previousBulletin=button("< Préc.",()->changeBulletin(-1));
        nextBulletin=button("Suiv. >",()->changeBulletin(1));
        previousType=button("< Type",()->setType(type+3));
        nextType=button("Type >",()->setType(type+1));
        add=button("+ Ajouter",()->edit(0));
        save=button("Enregistrer",()->edit(1));
        remove=button("Supprimer",()->edit(2));
        text=new TextFieldWidgetExtension(0,0,0,SQUARE_SIZE,512,TextCase.DEFAULT,null,"");
    }
    private static ButtonWidgetExtension button(String name,Runnable task){
        ButtonWidgetExtension b=new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,widget->task.run());
        b.setMessage2(new Text(TextHelper.literal(name).data));
        return b;
    }
    private long scope(){return routes.get(scopeIndex);}
    private List<PanamBulletins.Bulletin> current(){
        String stored=scope()==-1?PanamNewsClient.global():PanamNewsClient.forRoute(scope());
        return PanamBulletins.decode(stored);
    }
    private void changeScope(int delta){
        scopeIndex=Math.floorMod(scopeIndex+delta,routes.size());
        bulletinIndex=0;load();
    }
    private void changeBulletin(int delta){
        List<PanamBulletins.Bulletin> list=current();
        if(!list.isEmpty())bulletinIndex=Math.floorMod(bulletinIndex+delta,list.size());
        load();
    }
    private void setType(int newType){type=newType%4;}
    private void load(){
        List<PanamBulletins.Bulletin> list=current();
        filling=true;
        if(!list.isEmpty()){
            bulletinIndex=Math.min(bulletinIndex,list.size()-1);
            PanamBulletins.Bulletin item=list.get(bulletinIndex);
            text.setText2(item.message);type=item.type;
        }else{text.setText2("");type=0;bulletinIndex=0;}
        filling=false;
        seenRevision=PanamNewsClient.revision();
    }
    private void edit(int action){
        List<PanamBulletins.Bulletin> list=current();
        if(action==0 && list.size()>=PanamBulletins.LIMIT)return;
        if(action!=0 && list.isEmpty())return;
        String message=text.getText2();
        if(action!=2 && message.trim().isEmpty())return;
        if(action==0)bulletinIndex=list.size();
        InitClient.REGISTRY_CLIENT.sendPacketToServer(
                new PacketPanamBulletinEdit(scope(),action,bulletinIndex,type,message));
    }
    @Override protected void init2(){
        super.init2();
        x=Math.max(14,width/6);contentWidth=Math.max(220,width-x*2);
        IDrawing.setPositionAndWidth(previousScope,x,50,contentWidth/3);
        IDrawing.setPositionAndWidth(nextScope,x+contentWidth*2/3,50,contentWidth/3);
        IDrawing.setPositionAndWidth(previousBulletin,x,113,contentWidth/3);
        IDrawing.setPositionAndWidth(nextBulletin,x+contentWidth*2/3,113,contentWidth/3);
        IDrawing.setPositionAndWidth(previousType,x,174,contentWidth/3);
        IDrawing.setPositionAndWidth(nextType,x+contentWidth*2/3,174,contentWidth/3);
        IDrawing.setPositionAndWidth(text,x,220,contentWidth);
        IDrawing.setPositionAndWidth(add,x,267,contentWidth/3);
        IDrawing.setPositionAndWidth(save,x+contentWidth/3,267,contentWidth/3);
        IDrawing.setPositionAndWidth(remove,x+contentWidth*2/3,267,contentWidth/3);
        addChild(new ClickableWidget(previousScope));addChild(new ClickableWidget(nextScope));
        addChild(new ClickableWidget(previousBulletin));addChild(new ClickableWidget(nextBulletin));
        addChild(new ClickableWidget(previousType));addChild(new ClickableWidget(nextType));
        addChild(new ClickableWidget(text));addChild(new ClickableWidget(add));
        addChild(new ClickableWidget(save));addChild(new ClickableWidget(remove));
        load();
    }
    @Override public void tick2(){
        text.tick2();
        if(!filling&&seenRevision!=PanamNewsClient.revision())load();
    }
    @Override public void render(GraphicsHolder g,int mouseX,int mouseY,float delta){
        renderBackground(g);
        g.drawText("SILLON PANAM — Centre d'information voyageurs",x,16,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        String scopeName="Tout le réseau";
        if(scope()!=-1){
            SimplifiedRoute route=MinecraftClientData.getInstance().simplifiedRouteIdMap.get(scope());
            scopeName=route==null?"Ligne "+scope() : route.getName();
        }
        g.drawText("Diffusion : "+scopeName,x+contentWidth/3+4,57,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        List<PanamBulletins.Bulletin> list=current();
        g.drawText("Annonce : "+(list.isEmpty()?"aucune":(bulletinIndex+1)+"/"+list.size()),x+contentWidth/3+4,121,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText("Icône : "+kinds[type],x+contentWidth/3+4,181,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText("Message (jusqu'à 512 caractères)",x,202,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText("Les PANAM concernés alternent automatiquement les annonces toutes les 10 secondes.",x,319,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        g.drawText("Gestion réservée aux opérateurs du serveur. Les messages restent sauvegardés.",x,334,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        super.render(g,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen2(){return false;}
}
