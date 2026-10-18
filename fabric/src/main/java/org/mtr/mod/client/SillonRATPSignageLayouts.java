package org.mtr.mod.client;

import org.mtr.mod.data.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import static org.mtr.mod.client.SillonSignagePainter.*;

/** Separate compositions for plaques, corridor wayfinding, sectors, maps and displays. */
public final class SillonRATPSignageLayouts {
    private SillonRATPSignageLayouts(){}
    public static boolean draw(Graphics2D g,SillonSignageSpec s,int w,int h) {
        g.setColor(new Color(s.background()));g.fillRect(0,0,w,h);
        switch(s.style()) {
            case "metro","metro_subtitle","gpe"->station(g,s,w,h);
            case "rer"->rer(g,s,w,h);
            case "direction","transfer","gpe_direction"->directions(g,s,w,h);
            case "exit"->exits(g,s,w,h,false);
            case "sector"->exits(g,s,w,h,true);
            case "interchange"->stack(g,s,w,h);
            case "vertical","totem"->vertical(g,s,w,h);
            case "platform","route"->{
                SillonSignageSpec drawn=s;
                if(s.plan().lines().isEmpty()&&!s.effectiveRows("text").isEmpty()){
                    var badge=ids(s.icons()).stream().map(SillonSignageLineBadge::parse).filter(java.util.Objects::nonNull).findFirst().orElse(new SillonSignageLineBadge("metro",s.accent(),""));
                    var stops=s.effectiveRows("text").stream().map(r->new SillonSignagePlan.Stop(0,r.text(),r.detail(),r.icons(),0,0,false)).toList();
                    drawn=s.materialize(s.title(),s.subtitle(),s.icons(),s.accent(),s.rows(),new SillonSignagePlan(List.of(new SillonSignagePlan.Line(0,badge.label(),badge.mode(),badge.color(),stops)),-1,s.binding().layout()));
                }
                SillonSignagePlanPainter.route(g,drawn,w,h);
            }
            case "map"->{if(s.stationAuto())SillonSignagePlanPainter.network(g,s,w,h);else plan(g,s,w,h);}
            case "services"->services(g,s,w,h);
            case "digital"->digital(g,s,w,h);
            default->custom(g,s,w,h);
        }
        if(!"none".equals(s.options().frame())) {
            float edge=Math.max(1,Math.min(w,h)*.006f);
            g.setColor(new Color(s.options().frameColor()));g.setStroke(new BasicStroke(edge));
            g.draw(new Rectangle2D.Float(edge/2,edge/2,w-edge,h-edge));
        }
        return true;
    }
    private static float margin(SillonSignageSpec s,int w,int h) {
        return Math.min(w*.08f,Math.min(w,h)*.1f)*s.options().padding();
    }
    private static Font face(SillonSignageSpec s,boolean strong,float size) {
        return font(s.options().font(),strong,size*s.options().textScale());
    }
    private static String align(SillonSignageSpec s,String fallback) {
        return "auto".equals(s.options().align())?fallback:s.options().align();
    }
    private static void title(Graphics2D g,SillonSignageSpec s,String text,float size,int color,float x,float y,float w,float h,String alignment) {
        SillonSignagePainter.text(g,text,face(s,true,size),color,x,y,w,h,align(s,alignment));
    }
    private static void station(Graphics2D g,SillonSignageSpec s,int w,int h) {
        float pad=margin(s,w,h);
        boolean band="metro_subtitle".equals(s.style())&&!s.subtitle().isBlank();
        float bh=band?h*s.options().bandRatio():0;
        if(band) {g.setColor(new Color(s.accent()));g.fill(new Rectangle2D.Float(0,h-bh,w,bh));}
        title(g,s,s.title(),(h-bh)*(s.title().contains("|")?.49f:.66f),s.foreground(),
            pad,pad*.35f,w-2*pad,h-bh-pad*.7f,"center");
        if(band)title(g,s,s.subtitle(),bh*.72f,s.foreground(),pad,h-bh,w-pad*2,bh,"center");
        else if(!s.subtitle().isBlank()&&"gpe".equals(s.style())) {
            // Secondary text is an explicit second tier; never overprint the station name.
            g.setColor(new Color(s.background()));g.fill(new Rectangle2D.Float(0,h*.73f,w,h*.27f));
            title(g,s,s.subtitle(),h*.16f,s.foreground(),pad,h*.73f,w-2*pad,h*.23f,"center");
        }
    }
    private static void rer(Graphics2D g,SillonSignageSpec s,int w,int h) {
        float pad=margin(s,w,h),left=0,right=w-pad;
        String sector=ids(s.icons()).stream().filter(x->Set.of("ratp_forum","ratp_rivoli","ratp_seine").contains(x)).findFirst().orElse("");
        if(!sector.isEmpty()){
            float block=w*s.options().bandRatio();
            BufferedImage image=icon(sector);
            if(image!=null) {
                // The source is a complete lockup including "secteur". Do not duplicate its text.
                g.setColor(new Color(s.options().freePalette()?s.accent():image.getRGB(image.getWidth()/16,image.getHeight()/16)));g.fill(new Rectangle2D.Float(0,0,block,h));
                float scale=s.options().iconScale(),iw=(block-pad*.8f)*scale,ih=(h-pad*.8f)*scale;
                Shape clip=g.getClip();g.clip(new Rectangle2D.Float(0,0,block,h));
                image(g,sectorLockup(sector),(block-iw)/2,(h-ih)/2,iw,ih);g.setClip(clip);
            }
            left=block+pad;
        }else left=pad;
        if(ids(s.icons()).contains("ratp_logo")||ids(s.icons()).contains("ratp_logo_white")) {
            float side=Math.min(h*.48f,h*.38f*s.options().iconScale());icons(g,"ratp_logo_white",w-pad-side,h*.50f,side,side,side);right-=side+pad;
        }
        title(g,s,s.title(),h*(s.title().contains("|")?.42f:.58f),s.foreground(),left,pad*.5f,right-left,h-pad-(s.subtitle().isBlank()?0:h*.2f),"center");
        if(!s.subtitle().isBlank())text(g,s.subtitle(),face(s,false,h*.15f),s.foreground(),left,h*.77f,right-left,h*.20f,"center");
    }
    private static List<SillonSignageRow> directionRows(SillonSignageSpec s) {
        if(!s.rows().isEmpty())return s.rows();
        List<SillonSignageRow> rows=new ArrayList<>();
        rows.add(new SillonSignageRow("transport","",s.title(),s.subtitle(),s.icons(),s.arrow()));
        rows.addAll(SillonSignageRow.legacy(s.body(),"transport"));return rows;
    }
    private static void directions(Graphics2D g,SillonSignageSpec s,int w,int h) {
        List<SillonSignageRow> rows=directionRows(s);
        float rh=h/(float)Math.max(1,rows.size());
        for(int i=0;i<rows.size();i++)destination(g,s,rows.get(i),0,i*rh,w,rh,s.background(),s.foreground());
    }
    private static void destination(Graphics2D g,SillonSignageSpec s,SillonSignageRow row,float x,float y,float w,float h,int bg,int fg) {
        g.setColor(new Color(bg));g.fill(new Rectangle2D.Float(x,y,w,h));
        float pad=Math.min(h*.13f,w*.045f)*s.options().padding();
        float arrowSize=Math.min(h*.55f,w*.095f),left=x+pad,right=x+w-pad;
        int direction=row.arrow();
        boolean arrowRight=direction==1||direction==5||direction==7;
        if(direction>=0) {
            float ax=arrowRight?right-arrowSize*.5f:left+arrowSize*.5f;
            arrow(g,direction,ax,y+h*.5f,arrowSize,fg);
            if(arrowRight)right-=arrowSize+pad;else left+=arrowSize+pad;
        }
        if(!row.number().isBlank()) {
            float box=Math.min(h*.5f,(right-left)*.14f);
            g.setColor(new Color(fg));g.setStroke(new BasicStroke(Math.max(1,h*.016f)));
            g.draw(new Rectangle2D.Float(left,y+(h-box)/2,box,box));
            text(g,row.number(),face(s,true,box*.69f),fg,left+box*.08f,y+(h-box)/2,box*.84f,box,"center");
            left+=box+pad;
        }
        if(!row.icons().isBlank()) {
            float height=h*.62f*s.options().iconScale();
            float maxIconW=Math.min((right-left)*(row.text().isBlank()?.95f:.37f),h*4.8f);
            float used=icons(g,row.icons(),left,y+(h-height)/2,maxIconW,height,height);
            left+=used+pad;
        }
        boolean detail=!row.detail().isBlank();
        title(g,s,row.text(),h*(detail?.37f:.51f),fg,left,y+h*(detail?.10f:.09f),right-left,h*(detail?.5f:.82f),"left");
        if(detail)text(g,row.detail(),face(s,false,h*.24f),fg,left,y+h*.57f,right-left,h*.31f,align(s,"left"));
    }
    private static void exits(Graphics2D g,SillonSignageSpec s,int w,int h,boolean sector) {
        float top=0,pad=margin(s,w,h);
        String sectorIcon=ids(s.icons()).stream().filter(x->Set.of("ratp_rivoli","ratp_forum","ratp_seine").contains(x)).findFirst().orElse("");
        String modes=String.join(",",ids(s.icons()).stream().filter(x->!x.equals(sectorIcon)).toList());
        if(sector) {
            float band=h*(s.stationAuto()?.25f:s.options().bandRatio());
            BufferedImage image=s.stationAuto()?null:icon(sectorIcon);
            if(image!=null) {
                g.setColor(new Color(image.getRGB(image.getWidth()/16,image.getHeight()/16)));g.fill(new Rectangle2D.Float(0,0,w,band));
                float scale=s.options().iconScale(),iw=(w-pad*2)*scale,ih=band*.82f*scale;
                Shape clip=g.getClip();g.clip(new Rectangle2D.Float(0,0,w,band));
                image(g,sectorLockup(sectorIcon),(w-iw)/2,(band-ih)/2,iw,ih);g.setClip(clip);
            }else {
                g.setColor(new Color(s.options().freePalette()?s.accent():GREY));g.fill(new Rectangle2D.Float(0,0,w,band));
                title(g,s,s.subtitle(),band*.53f,WHITE,pad,0,w-pad*2,band,"left");
            }
            top+=band;
        }
        if(!modes.isBlank()) {
            float band=Math.min(h*.12f,w*.18f);
            g.setColor(new Color(s.options().freePalette()?s.accent():WHITE));g.fill(new Rectangle2D.Float(0,top,w,band));
            icons(g,modes,pad,top+band*.07f,w-pad*2,band*.86f,band*.72f*s.options().iconScale());top+=band;
        }
        float remaining=h-top;
        boolean explicit=!s.rows().isEmpty();
        List<SillonSignageRow> rows=s.effectiveRows("exit");
        float heading=remaining*(rows.isEmpty()?.65f:.19f);
        SillonSignageRow header=new SillonSignageRow("exit","",s.title().isBlank()?"Sortie":s.title(),sector?"":s.subtitle(),"",s.arrow());
        destination(g,s,header,0,top,w,heading,s.background(),s.foreground());
        top+=heading;
        if(rows.isEmpty())return;
        float rh=Math.min((h-top)/rows.size(),h*.095f);
        for(int i=0;i<rows.size();i++) {
            SillonSignageRow row=rows.get(i);
            // Legacy body rows have no independent arrows; explicit rows retain theirs.
            destination(g,s,row,0,top+i*rh,w,rh,s.background(),s.foreground());
        }
    }
    private static void stack(Graphics2D g,SillonSignageSpec s,int w,int h) {
        List<SillonSignageRow> rows=s.rows();
        if(rows.isEmpty()) {
            List<SillonSignageRow> build=new ArrayList<>();
            build.add(new SillonSignageRow("transport","",s.title().isBlank()?"Correspondances":s.title(),"",s.icons(),s.arrow()));
            if(!s.subtitle().isBlank())build.add(new SillonSignageRow("service","",s.subtitle(),"","",s.arrow()));
            List<SillonSignageRow> exits=s.effectiveRows("exit");
            if(exits.isEmpty())build.add(new SillonSignageRow("exit","","Sortie","","",s.arrow()));else build.addAll(exits);
            rows=build;
        }
        float unit=h/(float)rows.size();
        for(int i=0;i<rows.size();i++) {
            SillonSignageRow r=rows.get(i);int bg,fg;
            if(s.options().freePalette()||"custom".equals(s.style())) {
                bg="service".equals(r.kind())?s.accent():s.background();fg=s.foreground();
            }else switch(r.kind()) {
                case "exit"->{bg=s.background();fg=s.foreground();}
                case "service"->{bg=s.accent();fg=WHITE;}
                case "sector"->{bg=GREY;fg=WHITE;}
                default->{bg=WHITE;fg=DARK;}
            }
            destination(g,s,r,0,i*unit,w,unit,bg,fg);
        }
    }
    private static void vertical(Graphics2D g,SillonSignageSpec s,int w,int h) {
        float pad=margin(s,w,h);List<String> symbols=ids(s.icons());
        String mode=symbols.stream().filter(id->!id.startsWith("line_")).findFirst().orElse("ratp_metro");
        List<String> lines=symbols.stream().filter(id->id.startsWith("line_")||id.matches("idfm_m[0-9].*")).toList();
        float diameter=Math.min(w*.96f,h*.27f);
        icons(g,mode,(w-diameter)/2,h*.035f,diameter,diameter,diameter*s.options().iconScale());
        float lineTop=h*.35f,unit=Math.min(w*.90f,h*.32f/Math.max(1,lines.size()));
        for(String id:lines){icons(g,id,(w-unit)/2,lineTop,unit,unit,unit*s.options().iconScale());lineTop+=unit*1.05f;}
        if(!s.title().isBlank())title(g,s,s.title(),Math.min(h*.09f,w*.25f),s.foreground(),pad,h*.65f,w-pad*2,h*.13f,"center");
        arrow(g,s.arrow(),w*.5f,h*.86f,Math.min(w*.64f,h*.19f),s.foreground());
    }
    private static void route(Graphics2D g,SillonSignageSpec s,int w,int h) {
        float pad=margin(s,w,h),head=h*.22f;
        destination(g,s,new SillonSignageRow("transport","",s.title(),s.subtitle(),s.icons(),s.arrow()),
            0,0,w,head,s.background(),s.foreground());
        List<SillonSignageRow> stops=s.effectiveRows("text");
        if(stops.isEmpty())return; // MTR diagram is supplied by the world renderer when auto is enabled.
        int n=stops.size();float top=head+h*.045f,bottom=h-pad;
        if(w/(float)h<2.8f) {
            int cols=w/(float)h>2?2:1,per=(n+cols-1)/cols;
            for(int c=0;c<cols;c++){
                int first=c*per,last=Math.min(n,first+per);if(first>=n)break;
                float x=pad+c*(w-pad*2)/cols+pad*.4f,cy=top;
                float step=(bottom-top)/Math.max(1,last-first);
                g.setColor(new Color(s.accent()));g.setStroke(new BasicStroke(Math.max(3,step*.09f)));
                g.draw(new Line2D.Float(x,cy+step*.5f,x,cy+(last-first-.5f)*step));
                for(int i=first;i<last;i++){
                    float y=cy+(i-first+.5f)*step;stop(g,s,x,y,step*.19f);
                    text(g,stops.get(i).text(),face(s,true,step*.69f),s.foreground(),x+step*.32f,y-step*.4f,
                        (w-pad*2)/cols-step*.5f,step*.8f,"left");
                }
            }
        }else {
            float step=(w-2*pad)/Math.max(1,n),xl=pad+step*.5f,xr=w-pad-step*.5f,y=top+(bottom-top)*.22f;
            g.setColor(new Color(s.accent()));g.setStroke(new BasicStroke(Math.max(3,h*.026f)));
            g.draw(new Line2D.Float(xl,y,xr,y));
            Font labels=face(s,false,h*.13f);float widest=1;
            for(SillonSignageRow stop:stops)for(String line:wrapped(stop.text()).split("\\|"))
                widest=Math.max(widest,(float)labels.getStringBounds(line,g.getFontRenderContext()).getWidth());
            labels=labels.deriveFont(labels.getSize2D()*Math.min(1,step*.92f/widest));
            float lineHeight=labels.getLineMetrics("ÉAgjp",g.getFontRenderContext()).getHeight();
            for(int i=0;i<n;i++){
                float x=xl+i*step;stop(g,s,x,y,h*.033f);
                String name=wrapped(stops.get(i).text());
                int lines=name.split("\\|",-1).length;
                text(g,name,labels,s.foreground(),x-step*.46f,y+h*.06f,
                    step*.92f,Math.min(bottom-y-h*.07f,lineHeight*lines),"center");
            }
        }
    }
    private static String wrapped(String text) {
        if(text.length()<15)return text;
        int middle=text.length()/2,at=-1,best=999;
        for(int i=2;i<text.length()-2;i++)if(text.charAt(i)==' '&&Math.abs(i-middle)<best){at=i;best=Math.abs(i-middle);}
        return at<0?text:text.substring(0,at)+"|"+text.substring(at+1);
    }
    private static void stop(Graphics2D g,SillonSignageSpec s,float x,float y,float r) {
        g.setColor(new Color(s.background()));g.fill(new Ellipse2D.Float(x-r,y-r,r*2,r*2));
        g.setColor(new Color(s.accent()));g.setStroke(new BasicStroke(Math.max(2,r*.30f)));g.draw(new Ellipse2D.Float(x-r,y-r,r*2,r*2));
    }
    private static void plan(Graphics2D g,SillonSignageSpec s,int w,int h) {
        float pad=margin(s,w,h),top=h*.18f;
        destination(g,s,new SillonSignageRow("text","",s.title(),s.subtitle(),"",-1),pad,0,w-pad*2,top,s.background(),s.foreground());
        BufferedImage im=resource(s.options().image());
        if(im!=null)image(g,im,pad,top,w-2*pad,h-top-pad);
        else if(!s.rows().isEmpty()||!s.body().isBlank()) {
            List<SillonSignageRow> rows=s.effectiveRows("text");float rh=(h-top-pad)/Math.max(1,rows.size());
            for(int i=0;i<rows.size();i++)destination(g,s,rows.get(i),pad,top+i*rh,w-pad*2,rh,s.background(),s.foreground());
        }
    }
    private static void services(Graphics2D g,SillonSignageSpec s,int w,int h) {
        if(!s.rows().isEmpty()){directions(g,s,w,h);return;}
        destination(g,s,new SillonSignageRow("service","",s.title(),s.subtitle(),s.icons(),s.arrow()),0,0,w,h,s.background(),s.foreground());
    }
    private static void digital(Graphics2D g,SillonSignageSpec s,int w,int h) {
        float head=h*.20f;destination(g,s,new SillonSignageRow("transport","",s.title(),s.subtitle(),s.icons(),-1),0,0,w,head,s.background(),s.foreground());
        List<SillonSignageRow> rows=s.effectiveRows("text");float rh=Math.min(h*.16f,(h-head)/Math.max(1,rows.size()));
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);float y=head+i*rh,pad=w*.025f;
            if(i%2==1){g.setColor(new Color(s.background()).brighter());g.fill(new Rectangle2D.Float(0,y,w,rh));}
            icons(g,row.icons(),pad,y+rh*.12f,w*.14f,rh*.76f,rh*.68f*s.options().iconScale());
            title(g,s,row.text(),rh*.44f,s.foreground(),w*.19f,y,w*.55f,rh,"left");
            text(g,row.number(),face(s,true,rh*.39f),s.foreground(),w*.76f,y,w*.21f,rh,"right");
        }
    }
    private static void custom(Graphics2D g,SillonSignageSpec s,int w,int h) {
        if(!s.options().image().isBlank()){
            BufferedImage im=resource(s.options().image());if(im!=null){image(g,im,0,0,w,h);return;}
        }
        if(!s.rows().isEmpty()){stack(g,s,w,h);return;}
        float pad=margin(s,w,h);
        if(!s.icons().isBlank())icons(g,s.icons(),pad,pad,w-pad*2,h*.24f,h*.20f*s.options().iconScale());
        title(g,s,s.title(),h*.40f,s.foreground(),pad,h*.28f,w-pad*2,h*.40f,"center");
        text(g,s.subtitle(),face(s,false,h*.16f),s.foreground(),pad,h*.68f,w-pad*2,h*.22f,align(s,"center"));
    }
}
