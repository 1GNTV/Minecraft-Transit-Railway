package org.mtr.mod.screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mtr.mod.client.ChantierClient;
import org.mtr.mod.chantier.*;
import java.util.*;
import static org.mtr.mod.chantier.ChantierPlan.*;

/** A small tool panel, not a form to fill before drawing. Escape saves the draft, never builds. */
public final class ChantierScreen extends Screen {
    private int tab,projectIndex;private boolean confirmUndo;private final List<EditBox> fields=new ArrayList<>();
    public ChantierScreen(){super(Component.literal("Sillon / Chantier"));}
    private Button button(int x,int y,int w,String text,Runnable action){Button b=Button.builder(Component.literal(text),v->{try{action.run();}catch(RuntimeException e){ChantierClient.message=e.getMessage();}}).bounds(x,y,w,20).build();addRenderableWidget(b);return b;}
    private void refresh(){rebuildWidgets();}
    private void pair(int y,String left,Runnable a,String right,Runnable b){int w=(width-32)/2;button(12,y,w,left,a);button(20+w,y,w,right,b);}
    private void alter(java.util.function.UnaryOperator<Part> fn){Part p=ChantierClient.part();if(p==null)return;ChantierClient.checkpoint();ChantierClient.edit(fn.apply(p));refresh();}
    @Override protected void init(){fields.clear();int tw=(width-30)/4;String[] tabs={"Dimensions","Matières","Volumes","Chantier"};for(int n=0;n<4;n++){int chosen=n;button(12+n*(tw+2),30,tw,tabs[n],()->{if(!materials())return;tab=chosen;confirmUndo=false;refresh();});}
        Part p=ChantierClient.part();int y=58;
        if(tab==0){
            if(p==null){pair(y,"Dessiner une station",()->draw(Kind.STATION),"Dessiner un tunnel",()->draw(Kind.TUNNEL));}
            else{
                pair(y,"‹ Volume "+(ChantierClient.selected+1)+" / "+ChantierClient.plan.parts().size(),()->{ChantierClient.selected=Math.floorMod(ChantierClient.selected-1,ChantierClient.plan.parts().size());refresh();},"Volume suivant ›",()->{ChantierClient.selected=(ChantierClient.selected+1)%ChantierClient.plan.parts().size();refresh();});
                pair(y+24,"Largeur − · "+p.width()+" m",()->alter(q->q.dimensions(q.width()-1,q.height())),"Largeur +",()->alter(q->q.dimensions(q.width()+1,q.height())));
                pair(y+48,"Hauteur − · "+p.height()+" m",()->alter(q->q.dimensions(q.width(),q.height()-1)),"Hauteur +",()->alter(q->q.dimensions(q.width(),q.height()+1)));
                pair(y+72,"Niveau − · Y "+(int)p.a().y(),()->alter(q->q.moved(0,-1,0)),"Niveau +",()->alter(q->q.moved(0,1,0)));
                pair(y+96,"Plafond : "+new String[]{"Plat","Voûté","Rond"}[p.profile()],()->alter(q->new Part(q.kind(),q.a(),q.b(),q.width(),q.height(),(q.profile()+1)%3,q.layout())),p.kind()==Kind.STATION?(p.layout()==0?"Quais latéraux":"Quai central"):p.kind()==Kind.TUNNEL?(p.layout()==0?"Créer la voie MTR":"Voie existante"):"Disposition automatique",()->{if(p.kind()==Kind.STATION||p.kind()==Kind.TUNNEL)alter(q->new Part(q.kind(),q.a(),q.b(),q.width(),q.height(),q.profile(),1-q.layout()));});
            }
        }else if(tab==1){
            if(ChantierClient.plan==null){ChantierClient.message="Dessine d’abord un volume.";}
            else{pair(y,"Quartz / pierre",()->palette("minecraft:smooth_quartz","minecraft:smooth_stone","minecraft:white_concrete"),"Béton / ardoise",()->palette("minecraft:light_gray_concrete","minecraft:polished_deepslate","minecraft:gray_concrete"));String[] names={"Murs","Sol","Plafond","Quai"},values={ChantierClient.plan.wall(),ChantierClient.plan.floor(),ChantierClient.plan.roof(),ChantierClient.plan.platform()};for(int n=0;n<4;n++){EditBox field=new EditBox(font,80,y+26+n*24,width-92,20,Component.literal(names[n]));field.setMaxLength(128);field.setValue(values[n]);fields.add(field);addRenderableWidget(field);}}
        }else if(tab==2){
            pair(y,"+ Station",()->draw(Kind.STATION),"+ Tunnel",()->draw(Kind.TUNNEL));
            pair(y+24,"+ Salle / mezzanine",()->draw(Kind.HALL),"+ Escalier",()->draw(Kind.STAIRS));
            pair(y+48,"Tirer un accès",()->{ChantierClient.extension(false);onClose();},"Ajouter un étage",()->{ChantierClient.extension(true);onClose();});
            pair(y+72,"Suivre la voie visée",()->{ChantierClient.rail();onClose();},"Tunnels entrée / sortie",()->{ChantierClient.mouths();onClose();});
            pair(y+96,"Éclairage : "+(ChantierClient.plan==null?8:ChantierClient.plan.spacing())+" m",()->{var q=ChantierClient.plan;if(q!=null){ChantierClient.checkpoint();ChantierClient.change(new ChantierPlan(q.parts(),q.wall(),q.floor(),q.roof(),q.platform(),q.spacing()>=16?4:q.spacing()+2,q.pillars()));refresh();}},"Piliers : "+(ChantierClient.plan!=null&&ChantierClient.plan.pillars()?"oui":"non"),()->{var q=ChantierClient.plan;if(q!=null){ChantierClient.checkpoint();ChantierClient.change(new ChantierPlan(q.parts(),q.wall(),q.floor(),q.roof(),q.platform(),q.spacing(),!q.pillars()));refresh();}});
        }else{
            pair(y,"Construire / mettre à jour",()->{ChantierClient.send(0);refresh();},"Arrêter l’opération",()->ChantierClient.send(2));
            pair(y+24,confirmUndo?"Confirmer la restauration":"Restaurer le terrain initial",()->{if(confirmUndo){ChantierClient.send(1);confirmUndo=false;}else confirmUndo=true;refresh();},"Nouveau chantier",()->{if(!ChantierClient.busy){ChantierClient.newProject();tab=0;refresh();}});
            List<String> saved=ChantierClient.projects();pair(y+48,"Projet précédent",()->load(saved,-1),"Projet suivant · "+saved.size(),()->load(saved,1));
            pair(y+72,"Annuler le dernier réglage",()->{ChantierClient.undoDraft(false);refresh();},"Rétablir le réglage",()->{ChantierClient.undoDraft(true);refresh();});
            var v=ChantierClient.preview();if(v!=null&&v.error().isEmpty())ChantierClient.message=v.cells()+" blocs dans l’enveloppe. Blocs techniques et constructions protégés.";
        }
        button(12,height-26,width-24,"Revenir dans le monde · Échap conserve le plan",this::onClose);
    }
    private void palette(String wall,String floor,String roof){var q=ChantierClient.plan;ChantierClient.checkpoint();ChantierClient.change(new ChantierPlan(q.parts(),wall,floor,roof,q.platform(),q.spacing(),q.pillars()));refresh();}
    private void load(List<String> ids,int direction){if(ids.isEmpty())return;projectIndex=Math.floorMod(projectIndex+direction,ids.size());ChantierClient.selectProject(ids.get(projectIndex));refresh();}
    private void draw(Kind kind){ChantierClient.mode=kind;ChantierClient.cancelPoint();onClose();}
    private boolean materials(){if(fields.isEmpty()||ChantierClient.plan==null)return true;try{var q=ChantierClient.plan;var next=new ChantierPlan(q.parts(),fields.get(0).getValue(),fields.get(1).getValue(),fields.get(2).getValue(),fields.get(3).getValue(),q.spacing(),q.pillars());if(!next.equals(q)){ChantierClient.checkpoint();ChantierClient.change(next);}return true;}catch(RuntimeException e){ChantierClient.message=e.getMessage();return false;}}
    @Override public void onClose(){if(!materials())return;ChantierClient.save();minecraft.setScreen(null);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE&&(tab==0||tab==2)){ChantierClient.remove();refresh();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xE8101923);g.drawString(font,"SILLON / CHANTIER",12,11,0xFF61D4C4,false);Part p=ChantierClient.part();if(p!=null&&width>470){String text=p.kind()+" · "+Math.round(p.length())+" × "+p.width()+" × "+p.height()+" m";g.drawString(font,text,Math.max(160,width-font.width(text)-12),11,0xFFEAF1F7,false);}if(tab==1&&fields.size()==4){String[] labels={"Murs","Sol","Plafond","Quai"};for(int n=0;n<4;n++)g.drawString(font,labels[n],12,90+n*24,0xFFBDD3DF,false);}String feedback=ChantierClient.message;var v=ChantierClient.preview();if(v!=null&&!v.error().isEmpty())feedback=v.error();if(font.width(feedback)>width-24)feedback=font.plainSubstrByWidth(feedback,width-34)+"…";g.drawString(font,feedback,12,height-42,0xFFFFD879,false);if(ChantierClient.busy){g.fill(12,height-48,width-12,height-46,0xFF34485B);g.fill(12,height-48,12+(width-24)*ChantierClient.progress/100,height-46,0xFF61D4C4);}super.render(g,mx,my,dt);}
}
