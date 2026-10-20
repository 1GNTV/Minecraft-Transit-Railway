package org.mtr.mod.client;
import org.mtr.mapping.holder.*;
import org.mtr.mod.InitClient;
import org.mtr.mod.builder.WalkGraph;
import org.mtr.mod.packet.PacketBuilderWayfinding;
public final class WayfindingClient {
    private static Object world;
    private static WalkGraph graph=new WalkGraph();
    private static long requested,sequence=System.nanoTime();
    public static String feedback="";
    private WayfindingClient(){}
    public static WalkGraph graph(){
        var current=MinecraftClient.getInstance().getWorldMapped();Object next=current==null?null:current.data;
        if(world!=next){world=next;graph=new WalkGraph();requested=0;sequence++;feedback="";}
        long now=System.currentTimeMillis();if(next!=null&&now-requested>3000){requested=now;InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketBuilderWayfinding(0,0,sequence,""));}
        return graph;
    }
    public static void render(){
        var p=MinecraftClient.getInstance().getPlayerMapped();if(p==null||!p.isHolding(org.mtr.mod.Items.BRUSH.get().asItem()))return;
        var walking=graph();for(var node:walking.nodes())for(long id:node.links())if(node.id()<id){var next=walking.get(id);if(next==null||Math.hypot(node.x()-p.getX(),node.z()-p.getZ())>96)continue;
            org.mtr.mod.render.MainRenderer.scheduleRender(org.mtr.mod.render.QueuedRenderLayer.LINES,(g,offset)->g.drawLineInWorld((float)(node.x()-offset.getXMapped()),(float)(node.y()+.12-offset.getYMapped()),(float)(node.z()-offset.getZMapped()),(float)(next.x()-offset.getXMapped()),(float)(next.y()+.12-offset.getYMapped()),(float)(next.z()-offset.getZMapped()),0xFF61D4C4));
        }
    }
    public static void refresh(){requested=0;graph();}
    public static void accept(long token,String raw){graph();if(token!=0&&token!=sequence)return;try{graph=WalkGraph.decode(raw);SillonSignageNetwork.invalidateWayfinding();}catch(RuntimeException ignored){}}
}
