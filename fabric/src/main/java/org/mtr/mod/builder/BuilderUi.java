package org.mtr.mod.builder;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;

public final class BuilderUi {
    public static final int BG=0xFF101923,PANEL=0xFF1B2936,BORDER=0xFF34485B,TEXT=0xFFEAF1F7,MUTED=0xFF9BB1C3,ACCENT=0xFF61D4C4;
    private BuilderUi(){}
    public static void rect(GraphicsHolder g,double x,double y,double w,double h,int color){GuiDrawing d=new GuiDrawing(g);d.beginDrawingRectangle();d.drawRectangle(x,y,x+w,y+h,color);d.finishDrawingRectangle();}
    public static void text(GraphicsHolder g,String s,int x,int y,int color){g.drawText(TextHelper.literal(s),x,y,color,false,GraphicsHolder.getDefaultLight());}
    public static String fit(String s,int pixels){int max=Math.max(2,pixels/6);return s.length()>max?s.substring(0,max-1)+"…":s;}
    public static ButtonWidgetExtension button(int x,int y,int w,String label,Runnable run){return new ButtonWidgetExtension(x,y,w,20,TextHelper.literal(label),b->run.run());}
    public static void line(GuiDrawing d,double x1,double y1,double x2,double y2,double width,int color){double len=Math.hypot(x2-x1,y2-y1);if(len<.01)return;double nx=-(y2-y1)*width/(2*len),ny=(x2-x1)*width/(2*len);d.drawRectangle(x1+nx,y1+ny,x2+nx,y2+ny,x2-nx,y2-ny,x1-nx,y1-ny,color);}
}
