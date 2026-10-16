package org.mtr.mod.screen;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockPanamDisplayEntity;
import org.mtr.mod.block.BlockPANAMScreen;
import org.mtr.mod.data.PanamScreenSettings;
import org.mtr.mod.packet.PacketUpdatePanamScreen;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.packet.PacketUpdatePIDSConfig;

/**
 * PANAM-only brush screen. Vanilla MTR PIDS continue using PIDSConfigScreen.
 * Network-wide notices are edited through permission-protected /mtr panam
 * commands, never through client-trusted world-wide packets.
 */
public final class PanamConfigScreen extends ScreenExtension implements IGui {
    private static final int MAX_MESSAGE_LENGTH = 512;
    private final BlockPos pos;
    private final LongAVLTreeSet platforms;
    private final String[] messages = new String[3];
    private final boolean[] hidden = new boolean[3];
    private final TextFieldWidgetExtension[] localMessages = new TextFieldWidgetExtension[3];
    private final CheckboxWidgetExtension[] hideBoxes = new CheckboxWidgetExtension[3];
    private final TextFieldWidgetExtension pageInput;
    private final CheckboxWidgetExtension autoPlatforms;
    private final CheckboxWidgetExtension lastTrain;
    private final CheckboxWidgetExtension suspended;
    private final ButtonWidgetExtension filterButton;
    private final ButtonWidgetExtension tabArrivals;
    private final ButtonWidgetExtension tabNews;
    private final boolean screenOnly;
    private final PanamScreenSettings initialSettings;
    private final TextFieldWidgetExtension[] displayFields=new TextFieldWidgetExtension[11];
    private final ButtonWidgetExtension tabDisplay,resetDisplay,saveButton,cancelButton,sizeTab,positionTab;
    private final ButtonWidgetExtension[] minus=new ButtonWidgetExtension[11],plus=new ButtonWidgetExtension[11];
    private int displayGroup,left,panelWidth,column,rowSpacing;
    private boolean cancel,saved;
    private boolean initialized;
    private int tab;
    private final int initialPage;

    public PanamConfigScreen(BlockPos pos, BlockPanamDisplayEntity entity) {
        super();
        this.pos = pos;
        screenOnly=entity instanceof BlockPANAMScreen.BlockEntity;
        initialSettings=screenOnly?((BlockPANAMScreen.BlockEntity)entity).settings():PanamScreenSettings.DEFAULT;
        tab=screenOnly?2:0;
        tabDisplay=new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,button->setTab(2));
        tabDisplay.setMessage2(new Text(TextHelper.literal("Affichage").data));
        resetDisplay=new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,button->setDisplayValues(PanamScreenSettings.DEFAULT));
        resetDisplay.setMessage2(new Text(TextHelper.literal("Réinitialiser").data));
        sizeTab=button("Taille et image",()->setDisplayGroup(0));
        positionTab=button("Position et angles",()->setDisplayGroup(1));
        saveButton=button("Enregistrer",this::onClose2);
        cancelButton=button("Annuler",()->{cancel=true;onClose2();});
        for(int i=0;i<displayFields.length;i++) {
            final int index=i;
            displayFields[i]=new TextFieldWidgetExtension(0,0,120,SQUARE_SIZE,16,TextCase.DEFAULT,"[^\\d.,-]",null);
            displayFields[i].setMessage2(new Text(TextHelper.literal(PanamDisplayForm.LABELS[i]).data));
            minus[i]=button("−",()->adjust(index,-1));
            plus[i]=button("+",()->adjust(index,1));
        }
        this.platforms = new LongAVLTreeSet(entity.getPlatformIds());
        initialPage = entity.getDisplayPage();
        for (int i=0;i<3;i++) {
            StringBuilder text = new StringBuilder();
            for (String component : entity.getMessage(i).split("\\|")) {
                String s = component.trim();
                if (s.equalsIgnoreCase("[panam:last]") || s.equalsIgnoreCase("[panam:suspended]")) continue;
                if (!s.isEmpty()) {
                    if (text.length() > 0) text.append(" | ");
                    text.append(s);
                }
            }
            messages[i] = text.toString();
            hidden[i] = entity.getHideArrival(i);
            localMessages[i] = new TextFieldWidgetExtension(0,0,0,SQUARE_SIZE,MAX_MESSAGE_LENGTH,TextCase.DEFAULT,null,"");
            hideBoxes[i] = new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,checked->{});
            hideBoxes[i].setMessage2(new Text(TextHelper.literal("Masquer le départ " + (i+1)).data));
        }
        boolean initialLast = false, initialSuspended = false;
        for (int i=0;i<3;i++) for(String s : entity.getMessage(i).split("\\|")) {
            if (s.trim().equalsIgnoreCase("[panam:last]")) initialLast = true;
            if (s.trim().equalsIgnoreCase("[panam:suspended]")) initialSuspended = true;
        }
        final boolean last = initialLast, stopped = initialSuspended;
        autoPlatforms = new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,checked->{});
        autoPlatforms.setMessage2(new Text(TextHelper.literal("Détecter automatiquement les quais").data));
        filterButton = PIDSConfigScreen.getPlatformFilterButton(pos,autoPlatforms,platforms,this);
        filterButton.setMessage2(new Text(TextHelper.literal("Choisir les quais…").data));
        pageInput = new TextFieldWidgetExtension(0,0,0,SQUARE_SIZE,3,TextCase.DEFAULT,"\\D","1");
        lastTrain = new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,checked->{});
        lastTrain.setMessage2(new Text(TextHelper.literal("Dernier métro confirmé").data));
        suspended = new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,checked->{});
        suspended.setMessage2(new Text(TextHelper.literal("Circulation interrompue").data));
        tabArrivals = new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,button->setTab(0));
        tabArrivals.setMessage2(new Text(TextHelper.literal("Départs et quais").data));
        tabNews = new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,button->setTab(1));
        tabNews.setMessage2(new Text(TextHelper.literal("Infos voyageurs").data));
        // The GUI is reconstructed on reopen, not every frame.
        autoPlatforms.setChecked(platforms.isEmpty());
        lastTrain.setChecked(last);
        suspended.setChecked(stopped);
    }

    private static ButtonWidgetExtension button(String label,Runnable action) {
        ButtonWidgetExtension b=new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,ignored->action.run());
        b.setMessage2(new Text(TextHelper.literal(label).data));
        return b;
    }

    @Override
    protected void init2() {
        super.init2();
        panelWidth=Math.min(700,width-24);
        left=(width-panelWidth)/2;
        column=(panelWidth-12)/2;
        rowSpacing=height>=250?36:32;
        int tabWidth=panelWidth/(screenOnly?3:2);
        place(tabArrivals,left,20,tabWidth-4);
        place(tabNews,left+tabWidth,20,tabWidth-4);
        if(screenOnly) {
            place(tabDisplay,left+2*tabWidth,20,tabWidth-4);
            place(sizeTab,left,46,column);
            place(positionTab,left+column+12,46,column);
            // Snapshot text before resizing; populate only AFTER each widget has a usable width.
            String[] values=initialized?displayTexts():PanamDisplayForm.values(initialSettings);
            for(int i=0;i<displayFields.length;i++) {
                place(displayFields[i],left,97,column-48);
                displayFields[i].setText2(values[i]);
                revealStart(displayFields[i]);
                place(minus[i],left,97,20);
                place(plus[i],left,97,20);
            }
        }
        int footerWidth=(panelWidth-12)/3;
        place(resetDisplay,left,height-26,footerWidth);
        place(cancelButton,left+footerWidth+6,height-26,footerWidth);
        place(saveButton,left+2*(footerWidth+6),height-26,footerWidth);
        IDrawing.setPositionAndWidth(autoPlatforms,left,58,panelWidth);
        place(filterButton,left,88,column);
        place(pageInput,left+column+12,88,column);
        if(!initialized)pageInput.setText2(Integer.toString(initialPage+1));
        revealStart(pageInput);
        addChild(new ClickableWidget(autoPlatforms));
        for(int i=0;i<3;i++) {
            IDrawing.setPositionAndWidth(hideBoxes[i],left,120+i*24,panelWidth);
            if(!initialized)hideBoxes[i].setChecked(hidden[i]);
            addChild(new ClickableWidget(hideBoxes[i]));
            String value=initialized?localMessages[i].getText2():messages[i];
            place(localMessages[i],left,64+i*32,panelWidth);
            localMessages[i].setText2(value);
            revealStart(localMessages[i]);
        }
        IDrawing.setPositionAndWidth(lastTrain,left,155,panelWidth);
        IDrawing.setPositionAndWidth(suspended,left,177,panelWidth);
        addChild(new ClickableWidget(lastTrain));
        addChild(new ClickableWidget(suspended));
        initialized=true;
        setTab(tab);
    }

    private void place(ButtonWidgetExtension widget,int x,int y,int w) {
        widget.setX2(x);widget.setY2(y);widget.setWidth2(w);
        addChild(new ClickableWidget(widget));
    }
    private void place(TextFieldWidgetExtension widget,int x,int y,int w) {
        widget.setX2(x);widget.setY2(y);widget.setWidth2(w);
        addChild(new ClickableWidget(widget));
    }
    private static void revealStart(TextFieldWidgetExtension field) {
        // setText at width 0 leaves vanilla's horizontal scroll beyond the text.
        field.setSelectionEnd2(0);
        field.setSelectionStart2(0);
    }
    private void setDisplayGroup(int value) {
        displayGroup=value;
        setTab(2);
    }
    private void setTab(int value) {
        tab=value;
        tabArrivals.setActiveMapped(tab!=0);
        tabNews.setActiveMapped(tab!=1);
        tabDisplay.setActiveMapped(tab!=2);
        sizeTab.visible=positionTab.visible=resetDisplay.visible=screenOnly&&tab==2;
        sizeTab.setActiveMapped(displayGroup!=0);
        positionTab.setActiveMapped(displayGroup!=1);
        for(int i=0;i<displayFields.length;i++) {
            displayFields[i].visible=false;
            displayFields[i].setTextFieldFocused2(false);
            minus[i].visible=plus[i].visible=false;
        }
        if(screenOnly&&tab==2) {
            int[] indices=PanamDisplayForm.GROUPS[displayGroup];
            for(int slot=0;slot<indices.length;slot++) {
                int i=indices[slot],x=left+(slot%2)*(column+12),y=97+(slot/2)*rowSpacing;
                displayFields[i].setX2(x+24);displayFields[i].setY2(y);
                minus[i].setX2(x);minus[i].setY2(y);
                plus[i].setX2(x+column-20);plus[i].setY2(y);
                displayFields[i].visible=minus[i].visible=plus[i].visible=true;
            }
        }
        autoPlatforms.visible=filterButton.visible=pageInput.visible=tab==0;
        pageInput.setTextFieldFocused2(false);
        for(int i=0;i<3;i++) {
            localMessages[i].visible=tab==1;
            localMessages[i].setTextFieldFocused2(false);
            hideBoxes[i].visible=tab==0;
        }
        lastTrain.visible=suspended.visible=tab==1;
    }
    private void adjust(int i,int direction) {
        displayFields[i].setText2(PanamDisplayForm.adjust(i,displayFields[i].getText2(),direction));
        revealStart(displayFields[i]);
    }
    private String[] displayTexts() {
        String[] values=new String[11];
        for(int i=0;i<values.length;i++)values[i]=displayFields[i].getText2();
        return values;
    }
    private boolean validPage() {
        try {int p=Integer.parseInt(pageInput.getText2());return p>=1&&p<=999;}
        catch(NumberFormatException e) {return false;}
    }

    @Override
    public void tick2() {
        pageInput.tick2();
        for(TextFieldWidgetExtension field:displayFields)field.tick2();
        for(TextFieldWidgetExtension message:localMessages)message.tick2();
    }
    private void text(GraphicsHolder holder,String value,int x,int y,int maxWidth,int colour) {
        if(GraphicsHolder.getTextWidth(value)>maxWidth) {
            while(!value.isEmpty()&&GraphicsHolder.getTextWidth(value+"…")>maxWidth)value=value.substring(0,value.length()-1);
            value+="…";
        }
        holder.drawText(value,x,y,colour,false,GraphicsHolder.getDefaultLight());
    }

    @Override
    public void render(GraphicsHolder holder,int mouseX,int mouseY,float delta) {
        renderBackground(holder);
        GuiDrawing drawing=new GuiDrawing(holder);
        drawing.beginDrawingRectangle();
        drawing.drawRectangle(left-6,2,left+panelWidth+6,height-2,0xEF171B22);
        drawing.finishDrawingRectangle();
        text(holder,"PANAM — Réglages de cet écran",left,6,panelWidth,ARGB_WHITE);
        int invalid=screenOnly?PanamDisplayForm.invalid(displayTexts()):-1;
        saveButton.setActiveMapped(invalid<0&&validPage());
        String hint="Échap enregistre aussi. Annuler ignore les modifications.";
        if(tab==0) {
            text(holder,"Quais : "+(autoPlatforms.isChecked2()?"détection automatique":platforms.size()+" sélectionné(s)"),left,46,panelWidth,ARGB_WHITE);
            text(holder,"Page de départs (1 à 999)",left+column+12,77,column,ARGB_WHITE);
            hint="Choisir les quais permet de passer en sélection manuelle.";
        } else if(tab==2) {
            text(holder,displayGroup==0?"Taille dans le monde et contenu de l'image.":"Position relative au bloc d'ancrage et rotation.",left,72,panelWidth,0xFFBFCBDD);
            int[] indices=PanamDisplayForm.GROUPS[displayGroup];
            hint="− / + : ajuster. Vous pouvez aussi saisir une valeur.";
            for(int slot=0;slot<indices.length;slot++) {
                int i=indices[slot];
                boolean valid=PanamDisplayForm.valid(i,displayFields[i].getText2());
                displayFields[i].setEditableColor2(valid?ARGB_WHITE:0xFFFF8585);
                text(holder,PanamDisplayForm.LABELS[i],left+(slot%2)*(column+12),86+(slot/2)*rowSpacing,column,ARGB_WHITE);
                if(displayFields[i].isFocused2()||displayFields[i].isHovered()||minus[i].isHovered()||plus[i].isHovered())hint=PanamDisplayForm.HELP[i];
            }
        } else {
            for(int i=0;i<3;i++)text(holder,"Annonce locale "+(i+1)+" (facultative)",left,53+i*32,panelWidth,ARGB_WHITE);
            hint="/mtr panam : gérer les annonces du réseau et des lignes.";
            for(TextFieldWidgetExtension message:localMessages)if(message.isFocused2()||message.isHovered())hint="Texte libre. | sépare les messages. Vide = aucune annonce.";
        }
        if(invalid>=0)hint="À corriger : "+PanamDisplayForm.LABELS[invalid]+". "+PanamDisplayForm.HELP[invalid];
        if(!validPage())hint="Page de départs : saisissez un entier de 1 à 999.";
        text(holder,hint,left,height-42,panelWidth,invalid>=0||!validPage()?0xFFFF8585:0xFFBFCBDD);
        super.render(holder,mouseX,mouseY,delta);
    }

    @Override
    public void onClose2() {
        if(cancel||saved){super.onClose2();return;}
        int invalid=screenOnly?PanamDisplayForm.invalid(displayTexts()):-1;
        if(invalid>=0){displayGroup=invalid<6?1:0;setTab(2);displayFields[invalid].setTextFieldFocused2(true);return;}
        if(!validPage()){setTab(0);pageInput.setTextFieldFocused2(true);return;}
        saved=true;
        for(int i=0;i<3;i++) {
            messages[i]=localMessages[i].getText2();
            hidden[i]=hideBoxes[i].isChecked2();
        }
        // Preserve the existing MTR storage protocol for world compatibility.
        String flags=(lastTrain.isChecked2()?"[panam:last]":"")+
                (suspended.isChecked2()?"|[panam:suspended]":"");
        if(!flags.isEmpty())messages[0]=messages[0].isEmpty()?flags:messages[0]+"|"+flags;
        if(autoPlatforms.isChecked2())platforms.clear();
        int page=0;
        try{page=Math.max(0,Integer.parseInt(pageInput.getText2())-1);}catch(NumberFormatException ignored){}
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdatePIDSConfig(pos,messages,hidden,platforms,page));
        if(screenOnly)InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdatePanamScreen(pos,PanamDisplayForm.settings(displayTexts())));
        super.onClose2();
    }

    private void setDisplayValues(PanamScreenSettings settings) {
        String[] values=PanamDisplayForm.values(settings);
        for(int i=0;i<values.length;i++) {
            displayFields[i].setText2(values[i]);
            revealStart(displayFields[i]);
        }
    }

    @Override
    public boolean isPauseScreen2() {return false;}
}
