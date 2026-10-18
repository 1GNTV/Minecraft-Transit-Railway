package org.mtr.mod.screen;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockSillonSignage;
import org.mtr.mod.client.DynamicTextureCache;
import org.mtr.mod.data.*;
import org.mtr.mod.packet.PacketUpdateSillonSignage;
import java.util.*;
import java.util.List;

/** Responsive brush editor with visual libraries, independent rows and explicit save/cancel. */
public final class SillonSignageScreen extends ScreenExtension {
    private static final String[] TABS={"Modèles","Texte","Lignes","Icônes","Aspect","Pose"};
    private final BlockPos pos;
    private final SillonSignageEditorModel model;
    private final Map<String,Input> inputs=new LinkedHashMap<>();
    private final Button[] buttons=new Button[64],tiles=new Button[96],tabs=new Button[6];
    private final Button undo,redo,copy,paste,save,cancel;
    private final Input filter;
    private final Deque<SillonSignageSpec> history=new ArrayDeque<>(),future=new ArrayDeque<>();
    private static SillonSignageSpec clipboard;
    private SillonSignageSpec preview,lastRecorded;
    private SillonSignageEditorLayout layout;
    private int tab,page,pages=1,buttonCount,tileCount,category;
    private boolean loading,ready,rowIcons,selectedOnly,closed;
    private String selectedIcon="";
    private long lastPreview;
    private String status="",lastFilter="";
    private static final int BG=0xFF101722,PANEL=0xFF1A2432,EDGE=0xFF354354,INK=0xFFE9EFF7,MUTED=0xFFA5B3C5,ACCENT=0xFF5BBCE8;

    public SillonSignageScreen(BlockPos pos,BlockSillonSignage.BlockEntity entity) {
        this.pos=pos;model=new SillonSignageEditorModel(entity.document());preview=lastRecorded=entity.document();
        for(var f:SillonSignageEditorModel.FIELDS.values()) {
            Input input=new Input(f.key(),f.label(),128);
            input.setText2(model.values.getOrDefault(f.key(),""));inputs.put(f.key(),input);
            input.setChangedListener2(value->{if(!loading)model.values.put(f.key(),value);});
        }
        filter=new Input("filter","Rechercher un modèle ou un pictogramme",80);
        filter.setChangedListener2(v->{if(!loading){page=0;lastFilter=v;arrange();}});
        for(int i=0;i<buttons.length;i++)buttons[i]=new Button(20);
        for(int i=0;i<tiles.length;i++)tiles[i]=new Button(32);
        for(int i=0;i<tabs.length;i++) {
            final int which=i;tabs[i]=new Button(20);tabs[i].configure(TABS[i],()->selectTab(which));
        }
        undo=action("Annuler modif.",()->history(false));redo=action("Rétablir",()->history(true));
        copy=action("Copier",()->{if(valid()){clipboard=model.spec();status="Panneau copié";}});
        paste=action("Coller",()->{if(clipboard!=null)replace(clipboard);});
        save=action("Enregistrer",this::save);
        cancel=action("Annuler",()->{closed=true;super.onClose2();});
    }
    private Button action(String text,Runnable run){Button b=new Button(20);b.configure(text,run);return b;}
    @Override protected void init2() {
        super.init2();layout=SillonSignageEditorLayout.of(width,height);
        for(Input f:inputs.values())addChild(new ClickableWidget(f));addChild(new ClickableWidget(filter));
        for(Button b:buttons)addChild(new ClickableWidget(b));
        for(Button b:tiles)addChild(new ClickableWidget(b));
        for(Button b:tabs)addChild(new ClickableWidget(b));
        for(Button b:List.of(undo,redo,copy,paste,save,cancel))addChild(new ClickableWidget(b));
        ready=true;arrange();
    }
    private void selectTab(int next){record();model.saveRow();if(next==2)model.importLegacyRows();tab=next;page=0;loading=true;filter.setText2("");loading=false;sync();arrange();}
    private void sync() {
        if(model.selectedRow<0)rowIcons=false;
        loading=true;
        for(var e:inputs.entrySet())e.getValue().setText2(model.values.getOrDefault(e.getKey(),""));
        loading=false;
    }
    private void arrange() {
        if(!ready)return;
        for(Input f:inputs.values())f.visible=false;filter.visible=false;
        for(Button b:buttons)b.visible=false;for(Button b:tiles)b.visible=false;
        buttonCount=tileCount=0;
        int tw=(width-24)/6;
        for(int i=0;i<6;i++){
            tabs[i].move(12+i*tw,42,tw-3);tabs[i].selected=tab==i;
            boolean plaque=Set.of("metro","metro_subtitle","rer","gpe").contains(model.values.get("style"));
            tabs[i].enabled(!(i==2&&plaque)&&!(i==3&&plaque&&!"rer".equals(model.values.get("style"))));
        }
        int small=width<480?34:64;int right=width-12;
        for(Button b:List.of(paste,copy,redo,undo)){right-=small;b.move(right,8,small-3);}
        undo.configure(width<480?"<":"Annuler",()->history(false));redo.configure(width<480?">":"Rétablir",()->history(true));
        copy.configure(width<480?"Cop.":"Copier",()->{if(valid()){clipboard=model.spec();status="Panneau copié";}});
        paste.configure(width<480?"Col.":"Coller",()->{if(clipboard!=null)replace(clipboard);});
        paste.enabled(clipboard!=null);undo.enabled(!history.isEmpty());redo.enabled(!future.isEmpty());
        save.move(width-130,layout.footerY(),118);cancel.move(12,layout.footerY(),100);
        var area=layout.editor();
        if(tab==0)models(area);else if(tab==3)icons(area);else {
            List<String> keys=fieldsForTab();
            int toolbar=tab==2?24:0;
            if(tab==2)rowToolbar(area);
            if(tab!=2||model.selectedRow>=0) {
                int count=layout.formRows(toolbar);
                pages=Math.max(1,(keys.size()+count-1)/count);page=Math.max(0,Math.min(page,pages-1));
                for(int i=0;i<count&&page*count+i<keys.size();i++)field(keys.get(page*count+i),area.x(),area.y()+toolbar+i*38,area.w());
            }else {pages=1;page=0;}
        }
        pageControls();
        for(Input f:inputs.values())if(!f.visible)f.setTextFieldFocused2(false);
        if(!filter.visible)filter.setTextFieldFocused2(false);
    }
    private Button button(String label,Runnable action,int x,int y,int w) {
        Button b=buttons[buttonCount++];b.configure(label,action);b.move(x,y,w);b.visible=true;return b;
    }
    private Button tile(String label,Runnable action,int x,int y,int w) {
        Button b=tiles[tileCount++];b.configure(label,action);b.move(x,y,w);b.visible=true;return b;
    }
    private List<String> fieldsForTab() {
        String style=model.values.get("style");
        List<String> base=tab==1?SillonSignageEditorModel.TEXT:tab==2?SillonSignageEditorModel.ROW:tab==4?SillonSignageEditorModel.APPEARANCE:SillonSignageEditorModel.POSE;
        return base.stream().filter(key->{
            if("auto".equals(key))return Set.of("metro","metro_subtitle","rer","gpe","exit","route","platform").contains(style);
            if("arrow".equals(key))return !Set.of("metro","metro_subtitle","rer","gpe","map").contains(style);
            if("subtitle".equals(key)&&"metro".equals(style))return false;
            if("image".equals(key))return Set.of("map","custom").contains(style);
            if("bandRatio".equals(key))return Set.of("metro_subtitle","rer","sector").contains(style);
            if("iconScale".equals(key))return !Set.of("metro","metro_subtitle","gpe").contains(style);
            if("frameColor".equals(key))return !"none".equals(model.values.get("frame"));
            if("suspension".equals(key))return "hanging".equals(model.values.get("mount"));
            return true;
        }).toList();
    }
    private void pageControls() {
        int x=layout.editor().x(),w=layout.editor().w(),y=layout.pagerY();
        button("<",()->{record();page=Math.max(0,page-1);arrange();},x,y,28).enabled(page>0);
        button(">",()->{record();page=Math.min(pages-1,page+1);arrange();},x+w-28,y,28).enabled(page+1<pages);
        if(tab==3) {
            button(selectedOnly?"Biblioth.":"Sélection",()->{selectedOnly=!selectedOnly;page=0;arrange();},x+35,y,70);
            if(selectedOnly) {
                button("←",()->moveSelectedIcon(-1),x+110,y,24);
                button("→",()->moveSelectedIcon(1),x+138,y,24);
                button("X",()->{record();model.toggleIcon(rowIcons,selectedIcon);selectedIcon="";arrange();},x+166,y,24)
                    .enabled(!selectedIcon.isBlank());
            }
        }
    }
    private void field(String key,int x,int y,int w) {
        var f=SillonSignageEditorModel.FIELDS.get(key);Input input=inputs.get(key);
        input.labelX=x;input.labelY=y;input.labelWidth=w;
        if("choice".equals(f.type())) {
            String display=display(key,model.values.get(key));
            button(display,()->{record();model.cycle(key,1);sync();arrange();},x,y+12,w);
            input.visible=false;
        }else {
            boolean number="number".equals(f.type());input.move(x,y+12,w-(number?54:0));input.visible=true;
            if(number) {
                button("-",()->{record();model.adjust(key,-1);sync();},x+w-50,y+12,23);
                button("+",()->{record();model.adjust(key,1);sync();},x+w-23,y+12,23);
            }
        }
    }
    private void models(SillonSignageEditorLayout.Rect area) {
        filter.move(area.x(),area.y(),area.w());filter.visible=true;
        String q=SillonSignageIconCatalog.normalize(filter.getText2());
        List<SillonSignagePresets.Preset> list=SillonSignagePresets.ALL.stream().filter(p->SillonSignageIconCatalog.normalize(p.name()+" "+p.document().style()).contains(q)).toList();
        int rows=Math.max(1,(area.h()-24)/36),cols=area.w()>=410?2:1,per=rows*cols;
        pages=Math.max(1,(list.size()+per-1)/per);page=Math.min(page,pages-1);
        for(int i=0;i<per&&page*per+i<list.size();i++) {
            var p=list.get(page*per+i);int cw=(area.w()-4*(cols-1))/cols;
            Button b=tile(p.name(),()->{replace(p.document());status=p.reference();},area.x()+(i%cols)*(cw+4),area.y()+24+(i/cols)*36,cw);
            b.thumbnail=p.document();b.detail=SillonSignagePresets.label(p.document().style());
        }
    }
    private void rowToolbar(SillonSignageEditorLayout.Rect a) {
        int bw=28,x=a.x(),y=a.y();
        button("<",()->{record();model.selectRow(model.selectedRow-1);sync();arrange();},x,y,bw).enabled(!model.rows.isEmpty());
        int selectWidth=Math.max(66,a.w()-5*(bw+3));
        button(model.selectedRow<0?"Aucune ligne":("Ligne "+(model.selectedRow+1)+" / "+model.rows.size()),()->{
            record();model.selectRow(model.selectedRow+1);sync();arrange();},x+bw+3,y,selectWidth);
        x+=bw+selectWidth+6;
        button("+",()->{record();model.addRow();sync();page=0;arrange();},x,y,bw).enabled(model.rows.size()<32);x+=bw+3;
        button("X",()->{record();model.removeRow();sync();arrange();},x,y,bw).enabled(model.selectedRow>=0);x+=bw+3;
        button("↑",()->{record();model.moveRow(-1);sync();arrange();},x,y,bw).enabled(model.selectedRow>0);x+=bw+3;
        button("↓",()->{record();model.moveRow(1);sync();arrange();},x,y,bw).enabled(model.selectedRow>=0&&model.selectedRow+1<model.rows.size());
    }
    private void icons(SillonSignageEditorLayout.Rect a) {
        filter.move(a.x(),a.y(),Math.max(72,a.w()-148));filter.visible=true;
        button(SillonSignageIconCatalog.CATEGORIES.get(category),()->{category=(category+1)%SillonSignageIconCatalog.CATEGORIES.size();page=0;arrange();},a.right()-144,a.y(),76);
        button(rowIcons?"Ligne":"Panneau",()->{rowIcons=!rowIcons&&model.selectedRow>=0;page=0;arrange();},a.right()-64,a.y(),64);
        var found=SillonSignageIconCatalog.find(filter.getText2(),SillonSignageIconCatalog.CATEGORIES.get(category));
        Set<String> chosen=new LinkedHashSet<>(Arrays.asList(model.icons(rowIcons).split(",")));
        if(selectedOnly)found=found.stream().filter(e->chosen.contains(e.id())).toList();
        int cols=Math.max(1,a.w()/40),rows=Math.max(1,(a.h()-24)/36),per=Math.min(tiles.length,cols*rows);
        pages=Math.max(1,(found.size()+per-1)/per);page=Math.min(page,pages-1);
        int cw=(a.w()-4*(cols-1))/cols;
        for(int i=0;i<per&&page*per+i<found.size()&&i<tiles.length;i++) {
            var entry=found.get(page*per+i);
            Button b=tile(entry.label(),()->{record();if(selectedOnly)selectedIcon=entry.id();else model.toggleIcon(rowIcons,entry.id());arrange();},a.x()+(i%cols)*(cw+4),a.y()+24+(i/cols)*36,cw);
            b.icon=entry;b.selected=selectedOnly?entry.id().equals(selectedIcon):chosen.contains(entry.id());b.iconOnly=true;
        }
    }
    private void moveSelectedIcon(int delta) {
        record();List<String> current=Arrays.asList(model.icons(rowIcons).split(","));
        model.moveIcon(rowIcons,current.indexOf(selectedIcon),delta);arrange();
    }
    private String display(String key,String value) {
        if("style".equals(key))return SillonSignagePresets.label(value);
        if("arrow".equals(key)||"row_arrow".equals(key))return switch(value) {
            case "0"->"Gauche";case "1"->"Droite";case "2"->"Tout droit / haut";case "3"->"Bas";
            case "4"->"Haut gauche";case "5"->"Haut droite";case "6"->"Bas gauche";case "7"->"Bas droite";default->"Aucune";
        };
        return switch(value) {
            case "parisine"->"Parisine - RATP";case "grand_paris"->"Grand Paris - GPE";case "idf_voyageur"->"IDF Voyageur";
            case "auto"->"Selon le modèle";case "left"->"À gauche";case "center"->"Centré";
            case "wall"->"Mural";case "ceiling"->"Au plafond";case "hanging"->"Suspendu";case "flag"->"En drapeau";case "standing"->"Sur pied";
            case "none"->"Sans cadre";case "thin"->"Bord fin";case "box"->"Caisson";
            case "transport"->"Transport / correspondance";case "exit"->"Sortie";case "service"->"Service";case "sector"->"Secteur";case "text"->"Texte libre";
            default->value;
        };
    }
    private boolean valid(){String e=model.error();if(!e.isBlank()){status=e;return false;}return true;}
    private void record() {
        if(!valid())return;
        SillonSignageSpec now=model.spec();
        if(!now.equals(lastRecorded)) {
            history.addLast(lastRecorded);while(history.size()>40)history.removeFirst();
            lastRecorded=now;future.clear();
        }
    }
    private void replace(SillonSignageSpec value) {
        record();history.addLast(lastRecorded);future.clear();model.read(value);preview=lastRecorded=value;
        sync();arrange();
    }
    private void history(boolean forward) {
        record();
        Deque<SillonSignageSpec> source=forward?future:history,target=forward?history:future;
        if(source.isEmpty())return;
        target.addLast(lastRecorded);SillonSignageSpec next=source.removeLast();
        model.read(next);preview=lastRecorded=next;sync();arrange();status=forward?"Modification rétablie":"Modification annulée";
    }
    private void save() {
        if(closed||!valid())return;
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateSillonSignage(pos,model.spec()));
        closed=true;super.onClose2();
    }
    @Override public void onClose2(){closed=true;super.onClose2();}
    @Override public void tick2() {
        for(Input f:inputs.values())if(f.visible)f.tick2();if(filter.visible)filter.tick2();
        String error=model.error();save.enabled(error.isBlank());
        long now=System.currentTimeMillis();
        if(now-lastPreview>=200&&error.isBlank()) {preview=model.spec();lastPreview=now;}
        undo.enabled(!history.isEmpty()||!preview.equals(lastRecorded));redo.enabled(!future.isEmpty());
    }
    @Override public boolean mouseScrolled2(double mouseX,double mouseY,double amount) {
        if(amount==0)return false;record();page=Math.max(0,Math.min(pages-1,page+(amount<0?1:-1)));arrange();return true;
    }
    @Override public void render(GraphicsHolder g,int mouseX,int mouseY,float delta) {
        if(layout==null)return;
        renderBackground(g);rect(g,0,0,width,height,BG);rect(g,0,0,width,36,0xFF151E2B);
        label(g,"SILLON / Signalétique",12,12,Math.max(100,width-300),INK);
        String error=model.error(),hovered="";
        for(Button b:tiles)if(b.visible&&b.isHovered())hovered=b.caption;
        label(g,error.isBlank()?(!hovered.isBlank()?hovered:status.isBlank()?"Choisissez un modèle, puis adaptez son contenu.":status):error,
            12,29,width-24,error.isBlank()?MUTED:0xFFFFA18E);
        var a=layout.editor();rect(g,a.x()-5,a.y()-5,a.right()+5,layout.pagerY()+23,PANEL);
        var p=layout.preview();rect(g,p.x()-3,p.y()-3,p.right()+3,p.bottom()+3,0xFF252E3B);
        for(int x=p.x();x<p.right();x+=12)for(int y=p.y();y<p.bottom();y+=12)
            rect(g,x,y,Math.min(x+12,p.right()),Math.min(y+12,p.bottom()),((x/12+y/12)%2==0)?0xFF293342:0xFF303C4D);
        texture(g,DynamicTextureCache.instance.getSillonSignage(preview).identifier,
            preview.width()/preview.height(),p.x()+4,p.y()+4,p.w()-8,p.h()-8);
        if(width>=720) {
            label(g,"APERÇU DE FACE",p.x(),p.y()-14,p.w(),MUTED);
            label(g,preview.width()+" × "+preview.height()+" blocs",p.x(),layout.pagerY()+5,p.w(),MUTED);
        }
        if(tab==1||tab==2||tab==4||tab==5) {
            List<String> keys=fieldsForTab();
            int toolbar=tab==2?24:0,count=layout.formRows(toolbar);
            if(tab!=2||model.selectedRow>=0)for(int i=0;i<count&&page*count+i<keys.size();i++) {
                var f=SillonSignageEditorModel.FIELDS.get(keys.get(page*count+i));
                String fieldLabel=f.label();
                if("auto".equals(f.key())) {
                    if(Set.of("route","platform").contains(model.values.get("style")))fieldLabel="Desserte du quai MTR voisin";
                    else if("exit".equals(model.values.get("style")))fieldLabel="Sorties depuis la station MTR";
                }
                label(g,fieldLabel,a.x(),a.y()+toolbar+i*38,a.w()-20,MUTED);
                if("color".equals(f.type()))try{
                    int color=Integer.parseInt(model.values.get(f.key()).replace("#",""),16);
                    rect(g,a.right()-16,a.y()+toolbar+i*38,a.right(),a.y()+toolbar+i*38+8,0xFF000000|color);
                }catch(RuntimeException ignored){}
            }
            else label(g,"Ajoutez une ligne avec +.",a.x(),a.y()+30,a.w(),MUTED);
        }
        if(tab!=3)label(g,"Page "+(page+1)+" / "+pages,a.x()+38,layout.pagerY()+6,a.w()-76,MUTED);
        
        super.render(g,mouseX,mouseY,delta);
    }
    private static void rect(GraphicsHolder g,int x,int y,int right,int bottom,int color) {
        GuiDrawing d=new GuiDrawing(g);d.beginDrawingRectangle();d.drawRectangle(x,y,right,bottom,color);d.finishDrawingRectangle();
    }
    private static void label(GraphicsHolder g,String text,int x,int y,int maxWidth,int color) {
        if(maxWidth<6)return;String value=text==null?"":text;
        if(GraphicsHolder.getTextWidth(value)>maxWidth) {
            while(!value.isEmpty()&&GraphicsHolder.getTextWidth(value+"…")>maxWidth)value=value.substring(0,value.length()-1);
            value+="…";
        }
        g.drawText(value,x,y,color,false,GraphicsHolder.getDefaultLight());
    }
    private static void texture(GraphicsHolder g,Identifier image,float ratio,int x,int y,int w,int h) {
        if(w<=0||h<=0)return;int dw=Math.min(w,Math.round(h*ratio)),dh=Math.min(h,Math.round(w/ratio));
        int left=x+(w-dw)/2,top=y+(h-dh)/2;
        GuiDrawing d=new GuiDrawing(g);d.beginDrawingTexture(image);d.drawTexture(left,top,left+dw,top+dh,0,0,1,1);d.finishDrawingTexture();
    }
    private final class Input extends TextFieldWidgetExtension {
        final String key,hint;int labelX,labelY,labelWidth;
        Input(String key,String hint,int max){super(0,0,100,20,max,TextCase.DEFAULT,null,"");this.key=key;this.hint=hint;}
        void move(int x,int y,int width){setX2(x);setY2(y);setWidth2(Math.max(30,width));}
        @Override public void render(GraphicsHolder g,int mouseX,int mouseY,float delta) {
            super.render(g,mouseX,mouseY,delta);
            if(getText2().isBlank()&&"filter".equals(key))label(g,"Rechercher…",getX2()+5,getY2()+6,100,MUTED);
        }
    }
    private static final class Callback {Runnable action=()->{};}
    private final class Button extends ButtonWidgetExtension {
        private final Callback callback;private int bx,by,bw,bh;
        String caption="",detail="";boolean selected,iconOnly,enabled=true;
        SillonSignageIconCatalog.Entry icon;SillonSignageSpec thumbnail;
        Button(int height){this(new Callback(),height);}
        private Button(Callback callback,int height) {
            super(0,0,10,height,ignore->callback.action.run());this.callback=callback;bh=height;
        }
        void enabled(boolean value){enabled=value;setActiveMapped(value);}
        void configure(String text,Runnable action) {
            enabled(true);
            caption=text;callback.action=action;detail="";thumbnail=null;icon=null;selected=false;iconOnly=false;
            setMessage2(new Text(TextHelper.literal(text).data));
        }
        void move(int x,int y,int w){bx=x;by=y;bw=w;setX2(x);setY2(y);setWidth2(w);visible=true;}
        @Override public void render(GraphicsHolder g,int mouseX,int mouseY,float delta) {
            int fill=!enabled?0xFF202A37:selected?0xFF24536B:isHovered()?0xFF3A4D61:0xFF293748;
            rect(g,bx,by,bx+bw,by+bh,fill);rect(g,bx,by+bh-1,bx+bw,by+bh,selected?ACCENT:EDGE);
            if(icon!=null) {
                texture(g,new Identifier("mtr",icon.resource()),icon.width()/(float)icon.height(),bx+3,by+3,bw-6,bh-6);
                if(selected)rect(g,bx+bw-5,by+2,bx+bw-2,by+5,ACCENT);
            }else if(thumbnail!=null) {
                texture(g,DynamicTextureCache.instance.getSillonSignage(thumbnail).identifier,thumbnail.width()/thumbnail.height(),bx+3,by+3,52,bh-6);
                label(g,caption,bx+61,by+6,bw-65,INK);
                label(g,detail,bx+61,by+19,bw-65,MUTED);
            }else {
                int textWidth=GraphicsHolder.getTextWidth(caption),tx=bx+Math.max(4,(bw-textWidth)/2);
                label(g,caption,tx,by+(bh-8)/2,bw-8,enabled?INK:0xFF68798D);
            }
        }
    }
    @Override public boolean isPauseScreen2(){return false;}
}
