package org.mtr.mod.screen;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.builder.*;
import org.mtr.mod.client.*;
import org.mtr.mod.packet.PacketBuilderRail;
import org.mtr.mod.resource.RailResource;
import java.util.*;

/** Local draft. Applying/undoing uses an acknowledged compare-and-set server transaction. */
public final class BuilderRailScreen extends ScreenExtension {
    private final String railId;private Rail baseline,draft,undo;private String pending="",message="Aperçu local : la voie reste inchangée.";private long sent;
    private final List<RailResource> styles=new ArrayList<>();private int stylePage;private TextFieldWidgetExtension radiusField;
    public BuilderRailScreen(String id){railId=id;baseline=MinecraftClientData.getInstance().railIdMap.get(id);draft=baseline;styles.addAll(CustomResourceLoader.getRails());}
    private void refresh(){MinecraftClient.getInstance().openScreen(new Screen(this));}
    private void button(int x,int y,int w,String label,Runnable r){addChild(new ClickableWidget(BuilderUi.button(x,y,w,label,r)));}
    @Override protected void init2(){super.init2();if(draft==null){button(12,height-28,100,"Fermer",this::onClose2);return;}
        int half=width/2;
        button(12,38,half-18,draft.railMath.getShape()==Rail.Shape.QUADRATIC?"Courbe quadratique":"Deux rayons",()->{draft=Rail.copy(draft,draft.railMath.getShape()==Rail.Shape.QUADRATIC?Rail.Shape.TWO_RADII:Rail.Shape.QUADRATIC,draft.railMath.getVerticalRadius());refresh();});
        radiusField=new TextFieldWidgetExtension(half+6,38,width-half-18,20,24,TextCase.DEFAULT,"[^\\d\\.]","Rayon vertical (m)");radiusField.setText2(Double.toString(draft.railMath.getVerticalRadius()));radiusField.setChangedListener2(s->{try{double n=Double.parseDouble(s);if(Double.isFinite(n)&&n>=0&&n<=baseline.railMath.getMaxVerticalRadius()){draft=Rail.copy(draft,draft.railMath.getShape(),n);message="Aperçu local : cliquer Appliquer pour enregistrer.";}else message="Rayon entre 0 et "+String.format(Locale.ROOT,"%.2f",baseline.railMath.getMaxVerticalRadius())+" m.";}catch(RuntimeException e){message="Saisir un rayon valide.";}});addChild(new ClickableWidget(radiusField));
        int rows=Math.max(1,(height-190)/23);stylePage=Math.min(stylePage,Math.max(0,(styles.size()-1)/rows));
        for(int n=0;n<rows&&stylePage*rows+n<styles.size();n++){var resource=styles.get(stylePage*rows+n);boolean selected=draft.getStyles().stream().map(RailResource::getIdWithoutDirection).anyMatch(resource.getId()::equals);button(12,94+n*23,half-18,BuilderUi.fit((selected?"✓ ":"+ ")+resource.getName(),half-28),()->{var list=new ObjectArrayList<String>(draft.getStyles());if(selected)list.removeIf(s->RailResource.getIdWithoutDirection(s).equals(resource.getId()));else list.add(resource.getId().equals(CustomResourceLoader.DEFAULT_RAIL_ID)?resource.getId():resource.getId()+"_1");draft=Rail.copy(draft,list);refresh();});}
        int sy=height-86;button(12,sy,30,"‹",()->{stylePage=Math.max(0,stylePage-1);refresh();});button(46,sy,30,"›",()->{stylePage++;refresh();});button(80,sy,half-86,"Inverser",()->{var list=new ObjectArrayList<String>();draft.getStyles().forEach(s->list.add(s.endsWith("_1")?s.substring(0,s.length()-1)+"2":s.endsWith("_2")?s.substring(0,s.length()-1)+"1":s));draft=Rail.copy(draft,list);refresh();});
        int bw=(width-36)/4;button(12,height-28,bw,"Annuler",this::onClose2);button(16+bw,height-28,bw,"Classique",()->MinecraftClient.getInstance().openScreen(new Screen(new RailModifierScreen(railId))));button(20+2*bw,height-28,bw,"Défaire",this::undo);button(24+3*bw,height-28,bw,"Appliquer",this::apply);
    }
    private void send(Rail from,Rail to){if(!pending.isEmpty())return;pending=UUID.randomUUID().toString();sent=System.currentTimeMillis();InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketBuilderRail(new BuilderRailRequest(from,to,pending)));message="Enregistrement en cours…";}
    private void apply(){if(draft!=null&&draft.isValid()&&baseline!=null)send(baseline,draft);else message="Géométrie invalide.";}
    private void undo(){if(undo==null){message="Aucune modification à rétablir dans cette session.";return;}Rail current=MinecraftClientData.getInstance().railIdMap.get(railId);if(current!=null){draft=undo;send(baseline,undo);}}
    @Override public void tick2(){if(radiusField!=null)radiusField.tick2();if(!pending.isEmpty()){var receipt=PacketBuilderRail.take(pending);if(receipt!=null){pending="";message=receipt.message();if(receipt.success()){Rail previous=baseline;baseline=MinecraftClientData.getInstance().railIdMap.get(railId);draft=baseline;undo=previous;refresh();}}else if(System.currentTimeMillis()-sent>10000){pending="";message="Serveur sans réponse : rouvrir avant de réessayer.";}}}
    private void graph(GraphicsHolder g,int x,int y,int w,int h,boolean profile){BuilderUi.rect(g,x,y,w,h,BuilderUi.PANEL);if(draft==null||baseline==null)return;double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Rail r:new Rail[]{baseline,draft})for(int i=0;i<=96;i++){Vector v=r.railMath.getPosition(r.railMath.getLength()*i/96,false);double a=profile?r.railMath.getLength()*i/96:v.x,c=profile?-v.y:v.z;b[0]=Math.min(b[0],a);b[1]=Math.min(b[1],c);b[2]=Math.max(b[2],a);b[3]=Math.max(b[3],c);}
        double scale=Math.min((w-16)/Math.max(1,b[2]-b[0]),(h-20)/Math.max(1,b[3]-b[1]));GuiDrawing d=new GuiDrawing(g);d.beginDrawingRectangle();for(Rail r:new Rail[]{baseline,draft}){Vector prev=null;for(int i=0;i<=96;i++){Vector v=r.railMath.getPosition(r.railMath.getLength()*i/96,false);if(prev!=null){double a0=profile?r.railMath.getLength()*(i-1)/96:prev.x,a1=profile?r.railMath.getLength()*i/96:v.x;BuilderUi.line(d,x+w/2+(a0-(b[0]+b[2])/2)*scale,y+h/2+((profile?-prev.y:prev.z)-(b[1]+b[3])/2)*scale,x+w/2+(a1-(b[0]+b[2])/2)*scale,y+h/2+((profile?-v.y:v.z)-(b[1]+b[3])/2)*scale,r==draft?2:4,r==draft?BuilderUi.ACCENT:0xFF526779);}prev=v;}}d.finishDrawingRectangle();}
    @Override public void render(GraphicsHolder g,int mx,int my,float dt){BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"SILLON  /  ÉDITEUR DE VOIE",12,12,BuilderUi.ACCENT);if(draft==null)BuilderUi.text(g,"Voie indisponible : rouvrir avec le brush.",12,45,BuilderUi.TEXT);else{int x=width/2+6,w=width-x-12,h=Math.max(25,(height-190)/2);BuilderUi.text(g,"Styles de voie",12,77,BuilderUi.TEXT);BuilderUi.text(g,"Plan / profil vertical",x,77,BuilderUi.MUTED);graph(g,x,94,w,h,false);graph(g,x,100+h,w,h,true);BuilderUi.text(g,String.format(Locale.ROOT,"%.1f m · rayon %.1f m",draft.railMath.getLength(),draft.railMath.getVerticalRadius()),x,height-75,BuilderUi.MUTED);BuilderUi.text(g,"Gris : actuel  •  Vert : aperçu",12,height-55,BuilderUi.MUTED);}BuilderUi.text(g,BuilderUi.fit(message,width-24),12,height-41,0xFFFFD09A);super.render(g,mx,my,dt);}
    @Override public void onClose2(){MinecraftClient.getInstance().openScreen(null);}
    @Override public boolean isPauseScreen2(){return false;}
}
