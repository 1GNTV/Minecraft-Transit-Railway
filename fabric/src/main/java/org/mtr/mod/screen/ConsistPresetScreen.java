package org.mtr.mod.screen;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.builder.*;
import java.util.*;
import java.util.function.Consumer;
public final class ConsistPresetScreen extends ScreenExtension {
    private final ScreenExtension previous;private final String mode;private final Consumer<String> choose;private int page;private String message="";
    public ConsistPresetScreen(ScreenExtension previous,String mode,Consumer<String> choose){this.previous=previous;this.mode=mode;this.choose=choose;}
    private void refresh(){MinecraftClient.getInstance().openScreen(new Screen(this));}
    private void button(int x,int y,int w,String label,Runnable r){addChild(new ClickableWidget(BuilderUi.button(x,y,w,label,r)));}
    @Override protected void init2(){super.init2();var list=new ArrayList<>(ConsistPresets.load(mode).entrySet());int rows=Math.max(1,(height-100)/29);page=Math.min(page,Math.max(0,(list.size()-1)/rows));for(int n=0;n<rows&&page*rows+n<list.size();n++){var e=list.get(page*rows+n);int y=43+n*29;button(12,y,width-108,BuilderUi.fit(e.getKey()+" · "+e.getValue().size()+" voitures",width-124),()->{onClose2();choose.accept(e.getKey());});button(width-90,y,78,"Supprimer",()->{try{ConsistPresets.delete(mode,e.getKey());message="Composition supprimée.";refresh();}catch(Exception error){message="Suppression impossible.";}});}button(12,height-28,90,"Retour",this::onClose2);button(width-98,height-28,38,"‹",()->{page=Math.max(0,page-1);refresh();});button(width-54,height-28,38,"›",()->{if((page+1)*rows<list.size())page++;refresh();});}
    @Override public void render(GraphicsHolder g,int x,int y,float d){BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"COMPOSITIONS ENREGISTRÉES",12,15,BuilderUi.ACCENT);if(ConsistPresets.load(mode).isEmpty())BuilderUi.text(g,"Aucune composition enregistrée pour ce mode.",12,48,BuilderUi.MUTED);BuilderUi.text(g,message,12,height-44,BuilderUi.MUTED);super.render(g,x,y,d);}
    @Override public void onClose2(){MinecraftClient.getInstance().openScreen(new Screen(previous));}
    @Override public boolean isPauseScreen2(){return false;}
}
