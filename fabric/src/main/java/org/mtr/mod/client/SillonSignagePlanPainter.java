package org.mtr.mod.client;
import org.mtr.mod.data.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import static org.mtr.mod.client.SillonSignagePainter.*;

/** Route compositions measured against the attached horizontal and vertical RATP references. */
public final class SillonSignagePlanPainter {
    private SillonSignagePlanPainter(){}
    private static Font font(SillonSignageSpec s,boolean strong,float size){return SillonSignagePainter.font(SillonRATPSignageLayouts.family(s),strong,size*s.options().textScale());}
    public static void route(Graphics2D g,SillonSignageSpec s,int w,int h){
        if(s.plan().lines().isEmpty())return;
        var line=s.plan().lines().get(0);var stops=line.stops();if(stops.isEmpty())return;
        boolean horizontal="horizontal".equals(s.plan().layout())||"auto".equals(s.plan().layout())&&w/(float)h>=2.5;
        float pad=Math.min(w,h)*.045f*s.options().padding();
        if(horizontal)horizontal(g,s,line,w,h,pad);else vertical(g,s,line,w,h,pad);
    }
    private static void header(Graphics2D g,SillonSignageSpec s,SillonSignagePlan.Line l,float x,float y,float w,float h,boolean caption){
        String symbols=SillonSignageLineBadge.modeIcon(l.mode())+","+new SillonSignageLineBadge(l.mode(),l.color(),l.label()).token();
        float used=icons(g,symbols,x,y,Math.min(w*.38f,h*2.8f),h,h*.8f*s.options().iconScale());
        if(caption)text(g,s.title(),font(s,true,h*.52f),s.foreground(),x+used+h*.2f,y,w-used-h*.2f,h,"left");
    }
    private static int current(SillonSignageSpec s,List<SillonSignagePlan.Stop> stops){for(int i=0;i<stops.size();i++)if(stops.get(i).stationId()==s.plan().currentStation())return s.design().passed()?i:-1;return -1;}
    private static void vertical(Graphics2D g,SillonSignageSpec s,SillonSignagePlan.Line line,int w,int h,float pad){
        var stops=line.stops();float head=h*.12f;header(g,s,line,pad,pad,w-2*pad,head-pad,true);
        float top=head+h*.025f,bottom=h-pad,step=(bottom-top)/stops.size(),spine=w*.30f;
        int here=current(s,stops);float stroke=Math.max(3,Math.min(w*.014f,step*.25f));
        for(int i=0;i<stops.size()-1;i++){
            g.setColor(new Color(here>=0&&i<here?0xA2A4A8:line.color()));g.setStroke(new BasicStroke(stroke));
            g.draw(new Line2D.Float(spine,top+(i+.5f)*step,spine,top+(i+1.5f)*step));
        }
        float labelSize=step*.50f;
        for(var stop:stops){float tw=(float)font(s,true,labelSize).getStringBounds(stop.name(),g.getFontRenderContext()).getWidth();labelSize*=Math.min(1,(w-pad-spine-Math.max(step*.38f,w*.025f))/Math.max(1,tw));}
        for(int i=0;i<stops.size();i++){
            var stop=stops.get(i);float cy=top+(i+.5f)*step,r=Math.min(step*.12f,stroke*.62f),left=spine+Math.max(step*.38f,w*.025f);
            boolean terminus=i==0||i==stops.size()-1;
            g.setColor(new Color(terminus?line.color():0xD4D5D7));g.fill(new Ellipse2D.Float(spine-r,cy-r,r*2,r*2));
            g.setColor(new Color(i==here?s.foreground():line.color()));g.setStroke(new BasicStroke(Math.max(1.5f,stroke*.28f)));g.draw(new Ellipse2D.Float(spine-r,cy-r,r*2,r*2));
            if(i==here){g.setStroke(new BasicStroke(2));g.draw(new Ellipse2D.Float(spine-r-3,cy-r-3,r*2+6,r*2+6));}
            int ink=here>=0&&i<here?0x85878B:s.foreground();
            boolean detail=!stop.detail().isBlank();
            text(g,stop.name(),font(s,true,labelSize),ink,left,cy-step*(detail?.43f:.38f),w-pad-left,step*(detail?.58f:.76f),"left");
            if(detail)text(g,stop.detail(),font(s,false,step*.23f),ink,left,cy+step*.14f,w-pad-left,step*.31f,"left");
            g.setColor(new Color(s.foreground(),false));g.setStroke(new BasicStroke(Math.max(.6f,h*.0005f)));
            g.draw(new Line2D.Float(left,top+(i+1)*step,w-pad,top+(i+1)*step));
            int count=ids(stop.icons()).size();float unit=Math.min(step*.48f,(spine-pad*2)/Math.max(1,count*1.15f));
            float group=count*unit*1.15f;
            icons(g,stop.icons(),Math.max(pad,spine-pad-group),cy-unit/2,spine-pad*2,unit,unit*s.options().iconScale());
        }
    }
    private static void horizontal(Graphics2D g,SillonSignageSpec s,SillonSignagePlan.Line line,int w,int h,float pad){
        var stops=line.stops();float head=h*.14f;header(g,s,line,pad,pad,w-2*pad,head-pad,"platform".equals(s.style()));
        float left=Math.max(pad,w*.035f),right=w-Math.max(pad,w*.105f),y=h*.61f,step=(right-left)/Math.max(1,stops.size()-1);
        int here=current(s,stops);float stroke=Math.max(3,h*.020f),r=Math.max(3,h*.032f);
        for(int i=0;i<stops.size()-1;i++){
            g.setColor(new Color(here>=0&&i<here?0xA2A4A8:line.color()));g.setStroke(new BasicStroke(stroke));
            g.draw(new Line2D.Float(left+i*step,y,left+(i+1)*step,y));
        }
        float size=Math.min(h*.085f,Math.max(6,step*.25f));
        for(int i=0;i<stops.size();i++){
            var stop=stops.get(i);float x=left+i*step;boolean end=i==0||i==stops.size()-1;
            g.setColor(new Color(here>=0&&i<here?0xA7A9AD:0xC0C2C5));g.fill(new Ellipse2D.Float(x-r,y-r,r*2,r*2));
            g.setColor(new Color(i==here?s.foreground():0x96999D));g.setStroke(new BasicStroke(Math.max(1.5f,stroke*.4f)));g.draw(new Ellipse2D.Float(x-r,y-r,r*2,r*2));
            Graphics2D name=(Graphics2D)g.create();
            try{
                name.translate(x,y-r-h*.025f);name.rotate(Math.toRadians(-32));
                float max=Math.max(20,Math.min((y-head-h*.03f)/.53f,(w-pad-x)/.85f));
                Font face=font(s,true,size);float tw=(float)face.getStringBounds(stop.name(),g.getFontRenderContext()).getWidth();
                if(tw>max)face=face.deriveFont(Math.max(1,face.getSize2D()*max/tw));
                float width=(float)face.getStringBounds(stop.name(),g.getFontRenderContext()).getWidth()+h*.035f;
                if(end){name.setColor(new Color(s.foreground()));name.fill(new Rectangle2D.Float(-h*.012f,-face.getSize2D()*1.12f,width,face.getSize2D()*1.35f));}
                name.setFont(face);name.setColor(new Color(end?s.background():s.foreground()));name.drawString(stop.name(),0,0);
                if(!stop.detail().isBlank())text(name,stop.detail(),font(s,false,size*.52f),s.foreground(),0,face.getSize2D()*.20f,max,face.getSize2D()*.8f,"left");
            }finally{name.dispose();}
            List<String> symbols=ids(stop.icons());int cols=Math.min(4,Math.max(1,symbols.size()));
            float unit=Math.min(h*.071f,step*.83f/cols),gap=unit*.10f;
            for(int j=0;j<symbols.size();j++)icons(g,symbols.get(j),x-(cols*unit+(cols-1)*gap)/2+(j%cols)*(unit+gap),y+r+h*.045f+(j/cols)*unit*1.16f,
                unit,unit,unit*s.options().iconScale());
        }
    }
    public static void network(Graphics2D g,SillonSignageSpec s,int w,int h){SillonSignageNetworkMap.draw(g,s,w,h);}
}
