package org.mtr.mod.screen;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.*;
import org.mtr.mod.decor.*;
import org.mtr.mod.packet.PacketStationDecor;
import java.util.*;
import java.util.List;

/** Four small paginated forms and a live production-mesh preview, with explicit server feedback. */
public final class StationDecorScreen extends ScreenExtension {
    private final BlockPos pos;
    private final DecorKind kind;
    private DecorSpec base,preview;
    private static DecorSpec clipboard;
    private final Map<String,Input> inputs=new LinkedHashMap<>();
    private final List<Btn> controls=new ArrayList<>();
    private final Btn[] tabs=new Btn[4];
    private final Btn save,close,previous,next,copy,paste,turn;
    private int tab,page,pages,rows,leftW,previewX,previewW,variant,material,mask=2,operation,count=4,axis,step=3,angle=30;
    private boolean lit,loading;
    private long lastPreview;
    private String error="";
    private static final String[] TAB_NAMES={"Dimensions","Finition","Pose","Groupe"};
    private static final Map<String,String> LABELS=new LinkedHashMap<>();
    static {String[][] labels={{"width","Largeur (blocs)"},{"height","Hauteur (blocs)"},{"depth","Profondeur (blocs)"},{"spacing","Pas des lames / nervures"},{"suspension","Suspension (blocs)"},{"color","Couleur principale #RRGGBB"},{"accent","Couleur d’accent #RRGGBB"},{"yaw","Rotation horizontale (°)"},{"pitch","Inclinaison (°)"},{"x","Décalage X monde"},{"y","Décalage Y monde"},{"z","Décalage Z monde"},{"image","Image du resource pack : namespace:…png"},{"label","Identification personnalisée"},{"count","Nombre de copies (1–32)"},{"step","Espacement des ancres (1–16 blocs)"}};for(String[] a:labels)LABELS.put(a[0],a[1]);}
    public StationDecorScreen(BlockPos pos,DecorSpec spec){this.pos=pos;kind=spec.kind();base=preview=spec;variant=spec.variant();material=spec.material();lit=spec.illuminated();
        LABELS.forEach((key,label)->{Input f=new Input(key);inputs.put(key,f);});sync(spec);
        for(int i=0;i<4;i++){final int n=i;tabs[i]=new Btn(TAB_NAMES[i],()->{if(valid()){tab=n;page=0;arrange();}});}
        save=new Btn("Enregistrer",this::apply);close=new Btn("Fermer",()->{DecorBrushClient.clearGhost();super.onClose2();});
        previous=new Btn("<",()->{page=Math.max(0,page-1);arrange();});next=new Btn(">",()->{page=Math.min(pages-1,page+1);arrange();});
        copy=new Btn("Copier",()->{if(valid()){clipboard=read();DecorBrushClient.feedback="Réglages copiés.";}});
        paste=new Btn("Coller",()->{if(clipboard!=null){base=base.patch(clipboard,31);sync(base);variant=base.variant();material=base.material();lit=base.illuminated();arrange();}});
        turn=new Btn("Tourner l’aperçu",()->angle=angle>=150?-30:angle+60);
    }
    private void sync(DecorSpec s){loading=true;String[] values={""+s.width(),""+s.height(),""+s.depth(),""+s.spacing(),""+s.suspension(),String.format("%06X",s.color()),String.format("%06X",s.accent()),""+s.yaw(),""+s.pitch(),""+s.x(),""+s.y(),""+s.z(),s.image(),s.label(),""+count,""+step};int i=0;for(Input f:inputs.values())f.setText2(values[i++]);loading=false;}
    @Override protected void init2(){super.init2();for(Input f:inputs.values())addChild(new ClickableWidget(f));for(Btn b:tabs)addChild(new ClickableWidget(b));for(Btn b:List.of(save,close,previous,next,copy,paste,turn))addChild(new ClickableWidget(b));for(int i=0;i<16;i++){Btn b=new Btn("",()->{});controls.add(b);addChild(new ClickableWidget(b));}arrange();}
    private List<String> fields(){return switch(tab){
        case 0 -> {var f=new ArrayList<>(List.of("width","height","depth"));if(Set.of(DecorKind.RIBBED_WALL,DecorKind.BAFFLE,DecorKind.ACOUSTIC,DecorKind.VENT).contains(kind))f.add("spacing");if(kind==DecorKind.PENDANT)f.add("suspension");yield f;}
        case 1 -> {var f=new ArrayList<>(List.of("material","color","accent","lit"));if(kind==DecorKind.POSTER)f.add("image");if(Set.of(DecorKind.HELP_POINT,DecorKind.INTERCOM,DecorKind.CABINET).contains(kind))f.add("label");yield f;}
        case 2 -> List.of("yaw","pitch","x","y","z");
        default -> operation==1?List.of("operation","axis","count","step","ghost"):operation==2?List.of("operation","select","axis"):List.of("operation","select","mask1","mask2","mask4","mask8","mask16");
    };}
    private void arrange(){
        for(Input f:inputs.values())f.visible=false;for(Btn b:controls)b.visible=false;
        int gap=8;leftW=Math.max(135,(width-36)*46/100);previewX=leftW+24;previewW=width-previewX-12;
        int tw=(width-24)/4;for(int i=0;i<4;i++){tabs[i].move(12+i*tw,34,tw-3);tabs[i].selected=tab==i;tabs[i].setActiveMapped(tab!=i);}
        rows=Math.max(1,(height-145)/38);var keys=fields();pages=Math.max(1,(keys.size()+rows-1)/rows);page=Math.min(page,pages-1);int control=0;
        for(int j=0;j<rows&&page*rows+j<keys.size();j++){String key=keys.get(page*rows+j);int y=87+j*38;
            if(inputs.containsKey(key)){Input f=inputs.get(key);f.move(12,y,leftW);f.visible=true;}
            else {Btn b=controls.get(control++);b.move(12,y,leftW);switch(key){
                case "material" -> b.configure("Matériau : "+kind.materials.split("\\|")[material],()->{material=(material+1)%4;arrange();});
                case "lit" -> b.configure("Surface lumineuse : "+(lit?"oui":"non"),()->{lit=!lit;arrange();});
                case "operation" -> b.configure(new String[]{"Modifier la sélection","Répéter l’objet","Aligner la sélection"}[operation],()->{operation=(operation+1)%3;page=0;arrange();});
                case "axis" -> b.configure("Axe monde : "+"XYZ".charAt(axis),()->{axis=(axis+1)%3;arrange();});
                case "select" -> b.configure("Choisir les deux coins au brush",()->{if(valid()){DecorBrushClient.select(pos,read());super.onClose2();}});
                case "ghost" -> b.configure("Voir la pose en jeu",()->{if(valid()){DecorBrushClient.ghost(pos,read(),integer("count",1,32),axis,integer("step",1,16));super.onClose2();}});
                default -> {int bit=Integer.parseInt(key.substring(4));String label=switch(bit){case 1->"Dimensions";case 2->"Finition";case 4->"Pose";case 8->"Variante";default->"Image / identification";};b.configure(((mask&bit)!=0?"[x] ":"[ ] ")+label,()->{mask^=bit;arrange();});}
            }}
        }
        Btn variantButton=controls.get(control++);variantButton.configure(kind.variants.split("\\|")[variant]+"  >",()->{variant=(variant+1)%4;arrange();});variantButton.move(previewX,61,previewW);
        Btn undo=controls.get(control);undo.configure("Annuler la dernière opération",()->send(3));undo.move(previewX,height-63,previewW);
        previous.move(12,height-63,25);next.move(leftW-13,height-63,25);previous.setActiveMapped(page>0);next.setActiveMapped(page<pages-1);
        close.move(12,height-29,65);save.caption(tab==3?operation==1?"Poser les copies":operation==2?"Aligner":"Appliquer au groupe":"Enregistrer l’objet");save.move(width-151,height-29,139);
        copy.move(previewX,8,Math.max(35,previewW/2-3));paste.move(previewX+previewW/2,8,Math.max(35,previewW/2));paste.setActiveMapped(clipboard!=null);
        turn.move(previewX,height-88,previewW);
        for(Input f:inputs.values())if(!f.visible)f.setTextFieldFocused2(false);
    }
    private float number(String key){float f=Float.parseFloat(inputs.get(key).getText2().trim());if(!Float.isFinite(f))throw new IllegalArgumentException("Valeur invalide : "+LABELS.get(key));return f;}
    private int integer(String key,int min,int max){int n=Integer.parseInt(inputs.get(key).getText2());if(n<min||n>max)throw new IllegalArgumentException(LABELS.get(key));return n;}
    private int color(String key){String value=inputs.get(key).getText2().replace("#","").trim();if(!value.matches("[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("Couleur attendue : 6 chiffres hexadécimaux");return Integer.parseInt(value,16);}
    private DecorSpec read(){return new DecorSpec(kind,number("width"),number("height"),number("depth"),number("spacing"),number("suspension"),number("yaw"),number("pitch"),number("x"),number("y"),number("z"),color("color"),color("accent"),material,variant,lit,inputs.get("image").getText2(),inputs.get("label").getText2());}
    private boolean valid(){try{DecorSpec s=read();if(s.width()!=number("width")||s.height()!=number("height")||s.depth()!=number("depth"))throw new IllegalArgumentException("Dimensions : largeur 0,25–16 ; hauteur 0,1–12 ; profondeur 0,035–8.");
        if(!inputs.get("image").getText2().isEmpty()&&s.image().isEmpty())throw new IllegalArgumentException("Image : namespace:chemin/texture.png");error="";return true;
    }catch(RuntimeException ex){error=ex.getMessage()==null?"Vérifie les valeurs saisies.":ex.getMessage();return false;}}
    private void apply(){if(!valid())return;if(tab==3&&operation!=1&&(DecorBrushClient.first==null||DecorBrushClient.last==null||DecorBrushClient.source==null||DecorBrushClient.source.asLong()!=pos.asLong())){error="Choisis d’abord les deux coins de la sélection.";return;}if(tab==3&&operation==0&&mask==0){error="Choisis au moins une propriété à modifier.";return;}send(tab==3?operation==1?2:operation==2?4:1:0);}
    private void send(int action){try{DecorSpec spec=read();count=integer("count",1,32);step=integer("step",1,16);InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketStationDecor(pos,action,spec.encode(),DecorBrushClient.first==null?pos:DecorBrushClient.first,DecorBrushClient.last==null?pos:DecorBrushClient.last,mask,count,axis,step));DecorBrushClient.feedback="Demande envoyée au serveur…";DecorBrushClient.clearGhost();}catch(RuntimeException ex){error="Vérifie les valeurs numériques.";}}
    @Override public void render(GraphicsHolder g,int mx,int my,float delta){
        rect(g,0,0,width,height,0xFF101722);rect(g,7,58,leftW+19,height-35,0xFF1A2432);rect(g,previewX-4,58,width-7,height-35,0xFF1A2432);
        text(g,kind.title,12,14,Math.max(90,leftW),0xFFE9EFF7);
        for(Input f:inputs.values())if(f.visible)text(g,LABELS.get(f.key),12,f.getY2()-12,leftW,0xFFA5B3C5);
        if(System.currentTimeMillis()-lastPreview>550){try{preview=read();}catch(Exception ignored){}lastPreview=System.currentTimeMillis();}
        int ph=Math.max(30,height-181),pw=previewW;var resource=DynamicTextureCache.instance.getStationDecor(preview,angle);
        float ratio=4f/3;int dw=Math.min(pw,Math.round(ph*ratio)),dh=Math.min(ph,Math.round(pw/ratio));GuiDrawing drawing=new GuiDrawing(g);drawing.beginDrawingTexture(resource.identifier);drawing.drawTexture(previewX+(pw-dw)/2,88,previewX+(pw+dw)/2,88+dh,0,0,1,1);drawing.finishDrawingTexture();
        text(g,(page+1)+" / "+pages,48,height-57,Math.max(20,leftW-70),0xFFA5B3C5);
        text(g,error.isEmpty()?DecorBrushClient.feedback:error,12,height-41,width-24,error.isEmpty()?0xFFA5B3C5:0xFFFFA794);
        super.render(g,mx,my,delta);
    }
    private static void text(GraphicsHolder g,String s,int x,int y,int width,int color){while(s.length()>1&&GraphicsHolder.getTextWidth(s)>width)s=s.substring(0,s.length()-1);g.drawText(s,x,y,color,false,GraphicsHolder.getDefaultLight());}
    private static void rect(GraphicsHolder g,int x,int y,int X,int Y,int color){GuiDrawing d=new GuiDrawing(g);d.beginDrawingRectangle();d.drawRectangle(x,y,X,Y,color);d.finishDrawingRectangle();}
    private final class Input extends TextFieldWidgetExtension {final String key;Input(String key){super(0,0,100,20,key.equals("image")?160:48,TextCase.DEFAULT,null,"");this.key=key;}void move(int x,int y,int w){setX2(x);setY2(y);setWidth2(w);}}
    private static final class Callback{Runnable action=()->{};}
    private final class Btn extends ButtonWidgetExtension {
        final Callback callback;boolean selected;
        Btn(String text,Runnable action){this(new Callback(),text,action);}Btn(Callback callback,String text,Runnable action){super(0,0,100,20,ignore->callback.action.run());this.callback=callback;configure(text,action);}
        void configure(String text,Runnable action){callback.action=action;caption(text);}
        void caption(String text){setMessage2(new Text(TextHelper.literal(text).data));}
        void move(int x,int y,int w){setX2(x);setY2(y);setWidth2(w);visible=true;}
    }
    @Override public void onClose2(){DecorBrushClient.clearGhost();super.onClose2();}
    @Override public boolean isPauseScreen2(){return false;}
}
