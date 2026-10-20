package org.mtr.mod.screen;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.InitClient;
import org.mtr.mod.builder.*;
import org.mtr.mod.client.BuilderDiagnosticsClient;
import org.mtr.mod.packet.PacketBuilderPlan;
import java.util.function.BiConsumer;
import org.mtr.core.data.Position;
public final class BuilderDiagnosticsScreen extends ScreenExtension {
    private final Screen previous;private final BiConsumer<Position,Position> locate;private int page,rows;private long sent;
    public BuilderDiagnosticsScreen(Screen previous,BiConsumer<Position,Position> locate){this.previous=previous;this.locate=locate;request();}
    private void request(){var p=MinecraftClient.getInstance().getPlayerMapped();if(p!=null){sent=System.currentTimeMillis();InitClient.REGISTRY_CLIENT.sendPacketToServer(PacketBuilderPlan.diagnostics(p.getX(),p.getY(),p.getZ()));}}
    @Override protected void init2(){super.init2();rows=Math.max(1,(height-112)/49);addChild(new ClickableWidget(BuilderUi.button(12,height-32,80,"Retour",this::onClose2)));addChild(new ClickableWidget(BuilderUi.button(100,height-32,90,"Actualiser",this::request)));addChild(new ClickableWidget(BuilderUi.button(width-100,height-32,38,"‹",()->page=Math.max(0,page-1))));addChild(new ClickableWidget(BuilderUi.button(width-56,height-32,38,"›",()->{if((page+1)*rows<BuilderDiagnosticsClient.issues.size())page++;})));}
    @Override public void render(GraphicsHolder g,int mx,int my,float dt){BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"DIAGNOSTIC DU RÉSEAU",12,15,BuilderUi.ACCENT);boolean waiting=BuilderDiagnosticsClient.received<sent;BuilderUi.text(g,waiting?"Analyse sur le serveur…":BuilderDiagnosticsClient.issues.isEmpty()?"Aucune erreur signalée par les contrôles disponibles.":BuilderDiagnosticsClient.issues.size()+" point(s) à vérifier · cliquer pour localiser",12,35,BuilderUi.MUTED);var issues=BuilderDiagnosticsClient.issues;page=Math.min(page,Math.max(0,(issues.size()-1)/rows));for(int n=0;n<rows&&page*rows+n<issues.size();n++){var p=issues.get(page*rows+n);int y=57+n*49;BuilderUi.rect(g,12,y,width-24,44,BuilderUi.PANEL);BuilderUi.text(g,BuilderUi.fit(p.title(),width-44),20,y+7,0xFFFFC888);BuilderUi.text(g,BuilderUi.fit(p.detail(),width-44),20,y+23,BuilderUi.TEXT);}super.render(g,mx,my,dt);}
    @Override public boolean mouseClicked2(double x,double y,int b){if(super.mouseClicked2(x,y,b))return true;int n=(int)((y-57)/49),i=page*rows+n;if(x>=12&&x<width-12&&y>=57&&n<rows&&i<BuilderDiagnosticsClient.issues.size()){var p=BuilderDiagnosticsClient.issues.get(i);if(p.a()!=null){MinecraftClient.getInstance().openScreen(previous);locate.accept(p.a(),p.b());}return true;}return false;}
    @Override public void onClose2(){MinecraftClient.getInstance().openScreen(previous);}
    @Override public boolean isPauseScreen2(){return false;}
}
