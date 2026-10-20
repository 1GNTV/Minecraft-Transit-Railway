package org.mtr.mod.screen;

import org.mtr.core.data.SavedRailBase;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.builder.BuilderUi;
import java.util.*;
import java.util.function.Consumer;

/** Disambiguates platforms on different floors without changing their persistent IDs. */
public final class SavedRailPickerScreen extends ScreenExtension {
    private final List<SavedRailBase<?,?>> rails;private final Consumer<SavedRailBase<?,?>> pick;private final Screen previous;private int page;
    public SavedRailPickerScreen(List<SavedRailBase<?,?>> rails,Consumer<SavedRailBase<?,?>> pick,Screen previous){this.rails=List.copyOf(rails);this.pick=pick;this.previous=previous;}
    @Override protected void init2(){super.init2();int rows=Math.max(1,(height-100)/26),start=page*rows;for(int i=start;i<Math.min(rails.size(),start+rows);i++){var r=rails.get(i);String label=r.getName()+"  ·  Y "+r.getMidPosition().getY();addChild(new ClickableWidget(BuilderUi.button(20,45+(i-start)*26,width-40,label,()->{MinecraftClient.getInstance().openScreen(previous);pick.accept(r);})));}if(page>0)addChild(new ClickableWidget(BuilderUi.button(20,height-40,70,"Précédent",()->{page--;MinecraftClient.getInstance().openScreen(new Screen(this));})));if(start+rows<rails.size())addChild(new ClickableWidget(BuilderUi.button(95,height-40,70,"Suivant",()->{page++;MinecraftClient.getInstance().openScreen(new Screen(this));})));addChild(new ClickableWidget(BuilderUi.button(width-100,height-40,80,"Retour",this::onClose2)));}
    @Override public void render(GraphicsHolder g,int x,int y,float dt){BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"Plusieurs quais ici : choisir le niveau",20,18,BuilderUi.TEXT);super.render(g,x,y,dt);}
    @Override public void onClose2(){MinecraftClient.getInstance().openScreen(previous);}
    @Override public boolean isPauseScreen2(){return false;}
}
