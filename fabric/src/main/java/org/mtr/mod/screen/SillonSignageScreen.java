package org.mtr.mod.screen;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockSillonSignage;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.DynamicTextureCache;
import org.mtr.mod.data.IGui;
import org.mtr.mod.data.SillonSignageSpec;
import org.mtr.mod.data.SillonSignageIconCatalog;
import org.mtr.mod.data.SillonSignagePresets;
import java.util.List;
import org.mtr.mod.packet.PacketUpdateSillonSignage;

/** Unified brush editor: content, preset, icons, appearance, world geometry. */
public final class SillonSignageScreen extends ScreenExtension implements IGui {
    private static final String[] LABELS={
        "Nom principal","Sous-titre","Texte / itinéraire (séparateur |)","Icônes MTR France (séparateur ,)",
        "Fond (hex)","Texte (hex)","Accent (hex)",
        "Largeur en blocs","Hauteur en blocs","Rotation Y","Inclinaison X","Rotation Z"
    };
    private final BlockPos pos;
    private final TextFieldWidgetExtension[] fields=new TextFieldWidgetExtension[LABELS.length];
    private final ButtonWidgetExtension[] tabs=new ButtonWidgetExtension[3];
    private final ButtonWidgetExtension styleButton,mountButton,arrowButton,saveButton,cancelButton;
    private final ButtonWidgetExtension browseIcon,copyButton,pasteButton,presetButton;
    private final TextFieldWidgetExtension iconSearch;
    private int iconCursor,presetCursor;
    private static SillonSignageSpec clipboard;
    private final CheckboxWidgetExtension stationAuto,illuminated,doubleSided;
    private int styleIndex,mountIndex,arrow,tab,widthLeft,left,column;
    private boolean initialized,done,cancel;

    public SillonSignageScreen(BlockPos pos,BlockSillonSignage.BlockEntity entity){
        this.pos=pos;
        SillonSignageSpec spec=entity.document();
        styleIndex=Math.max(0,SillonSignageSpec.STYLES.indexOf(spec.style()));
        mountIndex=Math.max(0,SillonSignageSpec.MOUNTS.indexOf(spec.mount()));
        arrow=spec.arrow();
        for(int i=0;i<fields.length;i++)
            fields[i]=new TextFieldWidgetExtension(0,0,0,SQUARE_SIZE,i==2?1024:i==3?384:128,
                TextCase.DEFAULT,null,"");
        String[] defaults={
            spec.title(),spec.subtitle(),spec.body(),spec.icons(),
            String.format("%06X",spec.background()),String.format("%06X",spec.foreground()),String.format("%06X",spec.accent()),
            Float.toString(spec.width()),Float.toString(spec.height()),Float.toString(spec.yaw()),
            Float.toString(spec.pitch()),Float.toString(spec.roll())
        };
        for(int i=0;i<fields.length;i++)fields[i].setText2(defaults[i]);
        for(int i=0;i<tabs.length;i++){
            final int which=i;
            tabs[i]=button(new String[]{"Contenu","Apparence","Position"}[i],()->switchTab(which));
        }
        styleButton=button("",()->{styleIndex=(styleIndex+1)%SillonSignageSpec.STYLES.size();refreshButtons();});
        mountButton=button("",()->{mountIndex=(mountIndex+1)%SillonSignageSpec.MOUNTS.size();refreshButtons();});
        arrowButton=button("",()->{arrow=arrow>=7?-1:arrow+1;refreshButtons();});
        stationAuto=check("Nom automatique depuis MTR");
        illuminated=check("Panneau rétroéclairé");
        doubleSided=check("Double face");
        stationAuto.setChecked(spec.stationAuto());
        illuminated.setChecked(spec.illuminated());
        doubleSided.setChecked(spec.doubleSided());
        iconSearch=new TextFieldWidgetExtension(0,0,0,SQUARE_SIZE,64,TextCase.DEFAULT,null,"");
        iconSearch.setText2("idfm_m");
        browseIcon=button("Choisir une icône",this::browseIcon);
        presetButton=button("Modèles réels (photos RATP)",()->{
            SillonSignagePresets.Preset preset=SillonSignagePresets.at(presetCursor++);
            apply(preset.document());
            presetButton.setMessage2(new Text(TextHelper.literal("Photo : "+preset.name()).data));
        });
        copyButton=button("Copier le panneau",()->clipboard=spec());
        pasteButton=button("Coller le panneau",()->{if(clipboard!=null)apply(clipboard);});
        saveButton=button("Enregistrer",this::save);
        cancelButton=button("Annuler",()->{cancel=true;onClose2();});
        refreshButtons();
    }
    private static ButtonWidgetExtension button(String text,Runnable action){
        ButtonWidgetExtension b=new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,ignore->action.run());
        b.setMessage2(new Text(TextHelper.literal(text).data));
        return b;
    }
    private static CheckboxWidgetExtension check(String label){
        CheckboxWidgetExtension b=new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,value->{});
        b.setMessage2(new Text(TextHelper.literal(label).data));
        return b;
    }
    private void refreshButtons(){
        styleButton.setMessage2(new Text(TextHelper.literal("Style : "+SillonSignageSpec.STYLES.get(styleIndex)).data));
        mountButton.setMessage2(new Text(TextHelper.literal("Support : "+SillonSignageSpec.MOUNTS.get(mountIndex)).data));
        arrowButton.setMessage2(new Text(TextHelper.literal("Flèche : "+(arrow<0?"aucune":arrow==0?"←":arrow==1?"→":arrow==2?"↑":arrow==3?"↓":"diagonale "+arrow)).data));
    }
    @Override protected void init2(){
        super.init2();
        widthLeft=Math.min(660,width-28);left=(width-widthLeft)/2;column=(widthLeft-16)/2;
        for(int i=0;i<3;i++)position(tabs[i],left+i*(widthLeft/3),20,widthLeft/3-4);
        for(TextFieldWidgetExtension field:fields)position(field,left,85,column-16);
        position(styleButton,left,88,column);
        position(mountButton,left+column+16,88,column);
        position(arrowButton,left,133,column);
        position(presetButton,left,203,widthLeft);
        position(iconSearch,left,236,column);
        position(browseIcon,left+column+16,236,column);
        position(copyButton,left,272,column);
        position(pasteButton,left+column+16,272,column);
        IDrawing.setPositionAndWidth(stationAuto,left,190,column);
        IDrawing.setPositionAndWidth(illuminated,left+column+16,190,column);
        IDrawing.setPositionAndWidth(doubleSided,left,218,column);
        addChild(new ClickableWidget(iconSearch));
        addChild(new ClickableWidget(stationAuto));
        addChild(new ClickableWidget(illuminated));
        addChild(new ClickableWidget(doubleSided));
        position(saveButton,left+widthLeft/2+4,height-28,widthLeft/2-4);
        position(cancelButton,left,height-28,widthLeft/2-4);
        initialized=true;
        switchTab(tab);
    }
    private void position(ButtonWidgetExtension b,int x,int y,int w){
        b.setX2(x);b.setY2(y);b.setWidth2(w);addChild(new ClickableWidget(b));
    }
    private void position(TextFieldWidgetExtension f,int x,int y,int w){
        f.setX2(x);f.setY2(y);f.setWidth2(w);addChild(new ClickableWidget(f));
    }
    private void switchTab(int t){
        tab=t;
        for(int i=0;i<tabs.length;i++)tabs[i].setActiveMapped(tab!=i);
        for(int i=0;i<fields.length;i++){
            TextFieldWidgetExtension f=fields[i];
            f.visible=(tab==0&&i<4)||(tab==1&&i>=4&&i<=6)||(tab==2&&i>=7);
            f.setTextFieldFocused2(false);
            if(f.visible){
                int first=tab==0?0:tab==1?4:7;
                int slot=i-first;
                int x=left+(slot%2)*(column+16),y=84+(slot/2)*54;
                f.setX2(x);f.setY2(y+16);f.setWidth2(column);
            }
        }
        iconSearch.visible=browseIcon.visible=copyButton.visible=pasteButton.visible=presetButton.visible=tab==0;
        styleButton.visible=mountButton.visible=arrowButton.visible=tab==1;
        stationAuto.visible=illuminated.visible=doubleSided.visible=tab==1;
    }
    private void browseIcon(){
        List<String> results=SillonSignageIconCatalog.search(iconSearch.getText2());
        if(results.isEmpty()){
            browseIcon.setMessage2(new Text(TextHelper.literal("Aucune icône trouvée").data));
            return;
        }
        String next=results.get(iconCursor++%results.size());
        String current=fields[3].getText2().trim();
        if(!java.util.Arrays.asList(current.split(",")).contains(next))
            fields[3].setText2(current.isEmpty()?next:current+","+next);
        browseIcon.setMessage2(new Text(TextHelper.literal("Ajout : "+next).data));
    }
    private void apply(SillonSignageSpec document){
        String[] data={
            document.title(),document.subtitle(),document.body(),document.icons(),
            String.format("%06X",document.background()),
            String.format("%06X",document.foreground()),
            String.format("%06X",document.accent()),
            Float.toString(document.width()),Float.toString(document.height()),
            Float.toString(document.yaw()),Float.toString(document.pitch()),Float.toString(document.roll())
        };
        for(int i=0;i<data.length;i++)fields[i].setText2(data[i]);
        styleIndex=SillonSignageSpec.STYLES.indexOf(document.style());
        mountIndex=SillonSignageSpec.MOUNTS.indexOf(document.mount());
        arrow=document.arrow();
        stationAuto.setChecked(document.stationAuto());
        illuminated.setChecked(document.illuminated());
        doubleSided.setChecked(document.doubleSided());
        refreshButtons();
    }
    private static int hex(String source,int fallback){
        try{return (int)(Long.parseLong(source.trim().replace("#",""),16)&0xFFFFFF);}
        catch(Exception error){return fallback;}
    }
    private float number(int i,float fallback){
        try{return Float.parseFloat(fields[i].getText2().replace(',','.'));}
        catch(Exception error){return fallback;}
    }
    private SillonSignageSpec spec(){
        SillonSignageSpec d=SillonSignageSpec.DEFAULT;
        return new SillonSignageSpec(
            fields[0].getText2(),fields[1].getText2(),fields[2].getText2(),
            SillonSignageSpec.STYLES.get(styleIndex),fields[3].getText2(),
            SillonSignageSpec.MOUNTS.get(mountIndex),arrow,
            hex(fields[4].getText2(),d.background()),hex(fields[5].getText2(),d.foreground()),
            hex(fields[6].getText2(),d.accent()),
            number(7,d.width()),number(8,d.height()),number(9,d.yaw()),
            number(10,d.pitch()),number(11,d.roll()),
            stationAuto.isChecked2(),illuminated.isChecked2(),doubleSided.isChecked2());
    }
    private void save(){
        if(done)return;
        done=true;
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateSillonSignage(pos,spec()));
        super.onClose2();
    }
    @Override public void onClose2(){super.onClose2();}
    @Override public void tick2(){for(TextFieldWidgetExtension f:fields)f.tick2();}
    @Override public void render(GraphicsHolder g,int mouseX,int mouseY,float delta){
        renderBackground(g);
        GuiDrawing drawing=new GuiDrawing(g);
        drawing.beginDrawingRectangle();
        drawing.drawRectangle(left-10,4,left+widthLeft+10,height-4,0xE9131721);
        if(tab==1){
            drawing.drawRectangle(left,46,left+widthLeft,76,
                0xFF000000|hex(fields[4].getText2(),0x13235A));
        }
        drawing.finishDrawingRectangle();
        if(height>=430){
            int top=height-116;
            int x1=left+widthLeft/5,x2=left+widthLeft-widthLeft/5;
            g.drawText("Aperçu du panneau en direct",left,top-16,
                0xFFD4D9E6,false,GraphicsHolder.getDefaultLight());
            Identifier image=DynamicTextureCache.instance.getSillonSignage(spec()).identifier;
            drawing.beginDrawingTexture(image);
            drawing.drawTexture(x1,top,x2,height-44,0F,0F,1F,1F);
            drawing.finishDrawingTexture();
        }
        g.drawText("Sillon Signage — Éditeur universel",left,7,0xFFFFFFFF,false,GraphicsHolder.getDefaultLight());
        if(tab==1)g.drawText(fields[0].getText2(),left+12,55,0xFFFFFFFF,false,GraphicsHolder.getDefaultLight());
        if(tab==0){
            for(int i=0;i<4;i++)g.drawText(LABELS[i],left+(i%2)*(column+16),86+(i/2)*54,0xFFD5DBEB,false,GraphicsHolder.getDefaultLight());
            g.drawText("Recherche des 379 icônes MTR France",left,216,0xFFA8BED8,false,GraphicsHolder.getDefaultLight());
        }else if(tab==1){
            for(int i=4;i<=6;i++)g.drawText(LABELS[i],left+((i-4)%2)*(column+16),86+((i-4)/2)*54,0xFFD5DBEB,false,GraphicsHolder.getDefaultLight());
        }else{
            for(int i=7;i<12;i++)g.drawText(LABELS[i],left+((i-7)%2)*(column+16),86+((i-7)/2)*54,0xFFD5DBEB,false,GraphicsHolder.getDefaultLight());
        }
        super.render(g,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen2(){return false;}
}
