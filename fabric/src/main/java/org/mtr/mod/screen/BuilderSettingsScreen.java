package org.mtr.mod.screen;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.builder.*;
public final class BuilderSettingsScreen extends ScreenExtension {
    private final Screen previous;
    public BuilderSettingsScreen(Screen previous){this.previous=previous;}
    @Override protected void init2(){super.init2();String[][] opts={{"map","Carte Sillon"},{"composer","Compositeur de rames"},{"railEditor","Éditeur de voies"},{"doorGuides","Repères de portes au brush"}};for(int i=0;i<opts.length;i++){String key=opts[i][0],name=opts[i][1];addChild(new ClickableWidget(BuilderUi.button(20,43+i*29,width-40,name+" : "+(BuilderPreferences.enabled(key)?"Oui":"Classique / désactivé"),()->{BuilderPreferences.set(key,!BuilderPreferences.enabled(key));MinecraftClient.getInstance().openScreen(new Screen(this));})));}addChild(new ClickableWidget(BuilderUi.button(20,height-36,90,"Retour",this::onClose2)));}
    @Override public void render(GraphicsHolder g,int mx,int my,float dt){BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"INTERFACES SILLON",20,18,BuilderUi.ACCENT);BuilderUi.text(g,BuilderUi.fit("Chaque interface classique MTR reste disponible.",width-40),20,height-60,BuilderUi.MUTED);super.render(g,mx,my,dt);}
    @Override public void onClose2(){MinecraftClient.getInstance().openScreen(previous);}
    @Override public boolean isPauseScreen2(){return false;}
}
