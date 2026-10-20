package org.mtr.mod.client;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.block.*;
import org.mtr.mod.decor.*;
import org.mtr.mod.screen.StationDecorScreen;
import org.mtr.mod.render.RenderStationDecor;
import java.util.*;

/** Selection is opt-in, leaving all ordinary MTR brush interactions unchanged. */
public final class DecorBrushClient {
    public static BlockPos first,last,source;
    private static DecorSpec document;
    private static boolean selecting;
    private static int clicks;
    public static String feedback="";
    private static List<DecorPlacement.Offset> ghosts=List.of();
    public static void select(BlockPos pos,DecorSpec spec){source=pos;document=spec;selecting=true;clicks=0;first=null;last=null;feedback="Maj + clic sur les deux coins de la zone.";}
    public static boolean click(ItemUsageContext context){
        if(!selecting||context.getPlayer()==null||!context.getPlayer().isSneaking())return false;
        if(clicks++==0){first=context.getBlockPos();feedback="Premier coin choisi. Maj + clic sur le second.";context.getPlayer().sendMessage(TextHelper.literal(feedback),true);}
        else {last=context.getBlockPos();selecting=false;MinecraftClient.getInstance().openScreen(new Screen(new StationDecorScreen(source,document)));}
        return true;
    }
    public static void ghost(BlockPos pos,DecorSpec spec,int count,int axis,int step){source=pos;document=spec;ghosts=DecorPlacement.repeat(count,axis,step);}
    public static void clearGhost(){ghosts=List.of();}
    public static void drawGhosts(BlockPos pos,GraphicsHolder g,int light){
        if(source==null||document==null||source.asLong()!=pos.asLong()||ghosts.isEmpty())return;
        var client=MinecraftClient.getInstance();if(client.getPlayerMapped()==null||!client.getPlayerMapped().isHolding(org.mtr.mod.Items.BRUSH.get().asItem()))return;
        var world=client.getWorldMapped();if(world==null)return;Direction facing=IBlock.getStatePropertySafe(world,source,BlockStationDecor.FACING);
        for(var o:ghosts){g.push();try{g.translate(o.x()+.5+document.x(),o.y()+document.y(),o.z()+.5+document.z());g.rotateYDegrees(-facing.asRotation()+document.yaw());g.rotateXDegrees(document.pitch());RenderStationDecor.draw(g,document,light,100);}finally{g.pop();}}
    }
}
