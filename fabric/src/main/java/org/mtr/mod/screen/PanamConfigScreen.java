package org.mtr.mod.screen;

import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockPANAMPIDS;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.ArrivalsCacheClient;
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
    private int tab;
    private final int initialPage;
    private final long detectedRouteId;

    public PanamConfigScreen(BlockPos pos, BlockPANAMPIDS.BlockEntity entity) {
        super();
        this.pos = pos;
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
            hideBoxes[i].setMessage2(TextHelper.literal("Masquer le départ " + (i+1)));
        }
        boolean initialLast = false, initialSuspended = false;
        for (int i=0;i<3;i++) for(String s : entity.getMessage(i).split("\\|")) {
            if (s.trim().equalsIgnoreCase("[panam:last]")) initialLast = true;
            if (s.trim().equalsIgnoreCase("[panam:suspended]")) initialSuspended = true;
        }
        final boolean last = initialLast, stopped = initialSuspended;
        autoPlatforms = new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,checked->{});
        autoPlatforms.setMessage2(TextHelper.literal("Détecter automatiquement les quais"));
        filterButton = PIDSConfigScreen.getPlatformFilterButton(pos,autoPlatforms,platforms,this);
        filterButton.setMessage2(new Text(TextHelper.translatable("selectWorld.edit").data));
        pageInput = new TextFieldWidgetExtension(0,0,0,SQUARE_SIZE,3,TextCase.DEFAULT,"\\D","1");
        lastTrain = new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,checked->{});
        lastTrain.setMessage2(TextHelper.literal("Dernier métro confirmé"));
        suspended = new CheckboxWidgetExtension(0,0,0,SQUARE_SIZE,true,checked->{});
        suspended.setMessage2(TextHelper.literal("Circulation interrompue"));
        tabArrivals = new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,button->setTab(0));
        tabArrivals.setMessage2(TextHelper.literal("Départs et quais"));
        tabNews = new ButtonWidgetExtension(0,0,0,SQUARE_SIZE,button->setTab(1));
        tabNews.setMessage2(TextHelper.literal("Infos voyageurs"));
        ObjectArrayList<ArrivalResponse> arrivals = ArrivalsCacheClient.INSTANCE.requestArrivals(platforms);
        detectedRouteId = arrivals.isEmpty() ? -1 : arrivals.get(0).getRouteId();
        // The GUI is reconstructed on reopen, not every frame.
        autoPlatforms.setChecked(platforms.isEmpty());
        lastTrain.setChecked(last);
        suspended.setChecked(stopped);
    }

    @Override
    protected void init2() {
        super.init2();
        int start = Math.max(12, width / 10);
        int fieldWidth = Math.max(120, width - start*2);
        IDrawing.setPositionAndWidth(tabArrivals,start,14,fieldWidth/2-2);
        IDrawing.setPositionAndWidth(tabNews,start+fieldWidth/2+2,14,fieldWidth/2-2);
        addChild(new ClickableWidget(tabArrivals));
        addChild(new ClickableWidget(tabNews));

        IDrawing.setPositionAndWidth(autoPlatforms,start,58,fieldWidth);
        IDrawing.setPositionAndWidth(filterButton,start,84,fieldWidth/2);
        IDrawing.setPositionAndWidth(pageInput,start+fieldWidth/2+6,84,54);
        pageInput.setText2(Integer.toString(initialPage+1));
        addChild(new ClickableWidget(autoPlatforms));
        addChild(new ClickableWidget(filterButton));
        addChild(new ClickableWidget(pageInput));

        for(int i=0;i<3;i++) {
            IDrawing.setPositionAndWidth(hideBoxes[i],start,116+i*23,fieldWidth);
            hideBoxes[i].setChecked(hidden[i]);
            addChild(new ClickableWidget(hideBoxes[i]));
            IDrawing.setPositionAndWidth(localMessages[i],start,81+i*29,fieldWidth);
            localMessages[i].setText2(messages[i]);
            addChild(new ClickableWidget(localMessages[i]));
        }
        IDrawing.setPositionAndWidth(lastTrain,start,178,fieldWidth);
        IDrawing.setPositionAndWidth(suspended,start,203,fieldWidth);
        addChild(new ClickableWidget(lastTrain));
        addChild(new ClickableWidget(suspended));
        setTab(tab);
    }

    private void setTab(int value) {
        tab=value;
        autoPlatforms.visible=tab==0;
        filterButton.visible=tab==0;
        pageInput.visible=tab==0;
        for(int i=0;i<3;i++) {
            localMessages[i].visible=tab==1;
            hideBoxes[i].visible=tab==0;
        }
        lastTrain.visible=tab==1;
        suspended.visible=tab==1;
    }

    @Override
    public void tick2() {
        pageInput.tick2();
        for(TextFieldWidgetExtension message:localMessages)message.tick2();
    }

    @Override
    public void render(GraphicsHolder holder,int mouseX,int mouseY,float delta) {
        renderBackground(holder);
        int left=Math.max(12,width/10);
        holder.drawText("SILLON PANAM — Configuration",left,4,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        if(tab==0) {
            holder.drawText("Quais suivis : " + (platforms.isEmpty()?"automatique":platforms.size()),left,45,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
            holder.drawText("Page de départs",left+Math.max(120,(width-left*2)/2)+6,73,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
            holder.drawText("Les départs viennent du réseau MTR.",left,207,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        } else {
            holder.drawText("Annonces locales (séparateur : |)",left,46,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
            for(int i=0;i<3;i++)holder.drawText("Message "+(i+1),left,67+i*29,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
            holder.drawText("Annonces globales : /mtr panam global <texte>",left,236,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
            holder.drawText("Annonces ligne : /mtr panam line <ID> <texte>",left,249,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
            if(detectedRouteId>=0)holder.drawText("ID de la ligne détectée : "+detectedRouteId,left,262,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
            holder.drawText("Utilisez - pour supprimer une annonce réseau.",left,275,ARGB_WHITE,false,GraphicsHolder.getDefaultLight());
        }
        super.render(holder,mouseX,mouseY,delta);
    }

    @Override
    public void onClose2() {
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
        super.onClose2();
    }

    @Override
    public boolean isPauseScreen2() {return false;}
}
