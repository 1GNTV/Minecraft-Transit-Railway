package org.mtr.mod.client;
import org.mtr.mod.data.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import static org.mtr.mod.client.SillonSignagePainter.*;

/** Compositions from photographed RATP supports and IDFM V3.1, not a common stretched row. */
public final class SillonRATPSignageLayouts {
    private SillonRATPSignageLayouts(){}
    public static String profile(SillonSignageSpec s){return !s.design().profile().equals("auto")?s.design().profile():s.style().startsWith("gpe")||s.options().font().equals("grand_paris")?"gpe":s.options().font().equals("idf_voyageur")?"idfm":"ratp";}
    public static String family(SillonSignageSpec s){return s.design().profile().equals("auto")?s.options().font():switch(profile(s)){case "gpe"->"grand_paris";case "idfm"->"idf_voyageur";default->"parisine";};}
    private static Font face(SillonSignageSpec s,boolean bold,float size){return font(family(s),bold,size*s.options().textScale());}
    private static float pad(SillonSignageSpec s,int w,int h){return Math.min(w*.045f,h*.10f)*s.options().padding();}
    private static void ink(Graphics2D g,SillonSignageSpec s,String v,int colour,float x,float y,float w,float h,String align){float scaled=h*s.options().textScale();inkText(g,v,font(family(s),true,scaled*2.1f),colour,x,y+(h-scaled)/2,w,scaled,align);}
    public static boolean draw(Graphics2D g,SillonSignageSpec s,int w,int h){
        g.setColor(new Color(s.background()));g.fillRect(0,0,w,h);
        switch(s.style()){
            case "metro","metro_subtitle","gpe"->station(g,s,w,h);
            case "rer"->rer(g,s,w,h);
            case "direction","transfer","gpe_direction"->directions(g,s,w,h);
            case "exit","sector"->exits(g,s,w,h,s.style().equals("sector"));
            case "interchange"->stack(g,s,w,h);
            case "vertical","totem"->vertical(g,s,w,h);
            case "route","platform"->{
                var draw=s;
                if(s.plan().lines().isEmpty()&&!s.effectiveRows("text").isEmpty()){
                    var badge=ids(s.icons()).stream().map(SillonSignageLineBadge::parse).filter(Objects::nonNull).findFirst().orElse(new SillonSignageLineBadge("metro",s.accent(),""));
                    var stops=s.effectiveRows("text").stream().map(r->new SillonSignagePlan.Stop(0,r.text(),r.detail(),r.icons(),0,0,false)).toList();
                    draw=s.materialize(s.title(),s.subtitle(),s.icons(),s.accent(),s.rows(),new SillonSignagePlan(List.of(new SillonSignagePlan.Line(0,badge.label(),badge.mode(),badge.color(),stops)),-1,s.binding().layout()));
                }
                SillonSignagePlanPainter.route(g,draw,w,h);
            }
            case "map"->{if(s.stationAuto())SillonSignagePlanPainter.network(g,s,w,h);else plan(g,s,w,h);}
            case "services"->services(g,s,w,h);
            case "digital"->digital(g,s,w,h);
            default->{if(!s.rows().isEmpty())stack(g,s,w,h);else if(!s.options().image().isBlank())plan(g,s,w,h);else station(g,s,w,h);}
        }
        if(!s.options().frame().equals("none")){float edge=Math.max(1,Math.min(w,h)*.003f);g.setStroke(new BasicStroke(edge));g.setColor(new Color(s.options().frameColor()));g.draw(new Rectangle2D.Float(edge/2,edge/2,w-edge,h-edge));}
        return true;
    }
    private static void station(Graphics2D g,SillonSignageSpec s,int w,int h){
        float pad=pad(s,w,h);boolean band=s.style().equals("metro_subtitle")&&!s.subtitle().isBlank();
        float bh=band?h*s.options().bandRatio():0,main=h-bh;String name=s.title();
        float top=main*.20f,cap=main*.58f;
        if(s.style().equals("gpe")){top=main*.25f;cap=main*.48f;}
        if(!s.subtitle().isBlank()&&s.style().equals("gpe")){cap=main*.43f;top=main*.17f;}
        ink(g,s,name,s.foreground(),pad,top,w-2*pad,cap,s.options().align().equals("auto")?"center":s.options().align());
        if(band){g.setColor(new Color(s.accent()));g.fill(new Rectangle2D.Float(0,h-bh,w,bh));ink(g,s,s.subtitle(),s.foreground(),pad,h-bh+bh*.24f,w-2*pad,bh*.53f,"center");}
        else if(s.style().equals("gpe")&&!s.subtitle().isBlank())text(g,s.subtitle(),face(s,false,h*.15f),s.foreground(),pad,h*.68f,w-2*pad,h*.21f,"center");
    }
    private static int sectorColour(SillonSignageSpec s){return s.options().freePalette()?s.accent():switch(s.design().sector()){case "forum"->0x92915D;case "seine"->0x007DA5;default->0x6F6760;};}
    private static void sectorHeader(Graphics2D g,SillonSignageSpec s,String fallback,float x,float y,float w,float h){
        g.setColor(new Color(sectorColour(s)));g.fill(new Rectangle2D.Float(x,y,w,h));
        var symbol=sectorSymbol(s.design().symbol());String name=s.design().label(fallback);float bottom=y+h*.73f;
        if(symbol!=null)image(g,symbol,x+w*.20f,y+h*.10f,w*.60f,h*.43f);else bottom=y+h*.44f;
        Font regular=face(s,false,h*.17f),strong=face(s,true,h*.23f);
        float a=(float)regular.getStringBounds("secteur ",g.getFontRenderContext()).getWidth(),b=(float)strong.getStringBounds(name,g.getFontRenderContext()).getWidth();
        float scale=Math.min(1,w*.88f/Math.max(1,a+b));float left=x+(w-(a+b)*scale)/2;
        text(g,"secteur ",regular.deriveFont(regular.getSize2D()*scale),WHITE,left,bottom-h*.13f,a*scale,h*.3f,"left");
        text(g,name,strong.deriveFont(strong.getSize2D()*scale),WHITE,left+a*scale,bottom-h*.13f,b*scale,h*.3f,"left");
    }
    private static void rer(Graphics2D g,SillonSignageSpec s,int w,int h){
        float pad=pad(s,w,h),left=pad,right=w-pad;
        if(!s.design().sector().equals("none")){float sw=w*Math.max(.25f,s.options().bandRatio());sectorHeader(g,s,s.subtitle(),0,0,sw,h);left=sw+pad;}
        if(s.design().operator()){float unit=h*.27f;icons(g,"ratp_logo_white",w-pad-unit,h*.57f,unit,unit,unit);right-=unit+pad;}
        String name=s.title();if(!name.contains("|")&&name.length()>23)name=wrap(g,s,name,h*.40f,right-left);
        ink(g,s,name,s.foreground(),left,h*.15f,right-left,h*(name.contains("|")?.68f:.57f),"center");
        if(!s.subtitle().isBlank()&&s.design().sector().equals("none"))text(g,s.subtitle(),face(s,false,h*.15f),s.foreground(),left,h*.75f,right-left,h*.18f,"center");
    }
    private static String wrap(Graphics2D g,SillonSignageSpec s,String name,float fontSize,float available){
        if(name.contains("|")||face(s,true,fontSize).getStringBounds(name,g.getFontRenderContext()).getWidth()<=available)return name;
        int best=-1;double cost=Double.MAX_VALUE;
        for(int i=1;i<name.length()-1;i++)if(name.charAt(i)==' '){String a=name.substring(0,i),b=name.substring(i+1);double d=Math.max(a.length(),b.length())+Math.abs(a.length()-b.length())*.2;if(d<cost){cost=d;best=i;}}
        return best<0?name:name.substring(0,best)+"|"+name.substring(best+1);
    }
    private static List<SillonSignageRow> rows(SillonSignageSpec s){return s.rows().isEmpty()?List.of(new SillonSignageRow("transport","",s.title(),s.subtitle(),s.icons(),s.arrow())):s.rows();}
    private static void directions(Graphics2D g,SillonSignageSpec s,int w,int h){
        var rows=rows(s);float unit=h/(float)Math.max(1,rows.size());
        for(int i=0;i<rows.size();i++)destination(g,s,rows.get(i),0,i*unit,w,unit,s.background(),s.foreground(),true);
        // Shared arrow column for consecutive destinations in the same direction.
        for(int i=0;i<rows.size();){int end=i+1;while(end<rows.size()&&rows.get(end).arrow()==rows.get(i).arrow())end++;
            int direction=rows.get(i).arrow();if(direction>=0){float box=Math.min(unit*.9f,w*.17f),x=direction==1||direction==5||direction==7?w-box:0;
                g.setColor(new Color(s.background()));g.fill(new Rectangle2D.Float(x,i*unit,box,(end-i)*unit));
                arrow(g,direction,x+box/2,(i+end)*unit/2,Math.min(box*.70f,unit*.65f),s.foreground());
                if(profile(s).equals("gpe")){g.setColor(new Color(s.foreground()));g.setStroke(new BasicStroke(Math.max(1,h*.006f)));g.draw(new Rectangle2D.Float(x,i*unit,box,(end-i)*unit));}
            }i=end;
        }
    }
    private static void destination(Graphics2D g,SillonSignageSpec s,SillonSignageRow row,float x,float y,float w,float h,int bg,int fg,boolean arrowColumn){
        g.setColor(new Color(bg));g.fill(new Rectangle2D.Float(x,y,w,h));float pad=Math.min(h*.20f,w*.035f)*s.options().padding(),left=x+pad,right=x+w-pad;
        int direction=row.arrow();float box=Math.min(h*.9f,w*.17f);
        if(direction>=0){boolean rtl=direction==1||direction==5||direction==7;float ax=rtl?x+w-box/2:x+box/2;
            if(!arrowColumn)arrow(g,direction,ax,y+h/2,Math.min(h*.65f,box*.70f),fg);
            if(rtl)right=x+w-box-pad;else left=x+box+pad*.4f;
        }
        if(!row.number().isBlank()){float unit=h*.59f;g.setColor(new Color(fg));g.setStroke(new BasicStroke(Math.max(1,unit*.035f)));g.draw(new Rectangle2D.Float(left,y+(h-unit)/2,unit,unit));ink(g,s,row.number(),fg,left+unit*.08f,y+(h-unit)/2+unit*.12f,unit*.84f,unit*.76f,"center");left+=unit+pad*.7f;}
        if(!row.icons().isBlank()){float unit=h*.67f*s.options().iconScale();float used=icons(g,contrastSymbols(row.icons(),bg,fg),left,y+(h-unit)/2,Math.min((right-left)*.43f,h*4),unit,unit);left+=used+pad*.8f;}
        boolean detail=!row.detail().isBlank();String name=wrap(g,s,row.text(),h*.56f,right-left);
        String align=direction==1||direction==5||direction==7?"right":"left";
        if(detail){ink(g,s,name,fg,left,y+h*.19f,right-left,h*.33f,align);text(g,row.detail(),face(s,false,h*.22f),fg,left,y+h*.58f,right-left,h*.25f,align);}
        else ink(g,s,name,fg,left,y+h*.25f,right-left,h*.49f,align);
        if(profile(s).equals("gpe")){g.setColor(new Color(fg));g.setStroke(new BasicStroke(Math.max(1,h*.006f)));g.draw(new Rectangle2D.Float(x,y,w,h));}
    }
    private static void exits(Graphics2D g,SillonSignageSpec s,int w,int h,boolean sector){
        float top=0,pad=w*.045f*s.options().padding();
        if(sector){float band=h*s.options().bandRatio();sectorHeader(g,s,s.subtitle(),0,0,w,band);top=band;}
        if(!s.icons().isBlank()){float band=h*(sector?.115f:.12f);g.setColor(Color.WHITE);g.fill(new Rectangle2D.Float(0,top,w,band));float unit=band*.76f*s.options().iconScale();icons(g,contrastSymbols(s.icons(),WHITE,s.foreground()==WHITE?NAVY:s.foreground()),pad,top,w-2*pad,band,unit);top+=band;}
        var rows=s.effectiveRows("exit");float heading=Math.min(h*.14f,(h-top)*.24f);
        ink(g,s,s.title().isBlank()?"Sortie":s.title(),s.foreground(),pad,top+heading*.14f,w-pad*2,heading*.65f,"left");
        if(s.arrow()>=0&&!sector)arrow(g,s.arrow(),w-pad-heading*.30f,top+heading*.49f,heading*.60f,s.foreground());
        top+=heading;float rh=Math.min(h*.067f*s.design().rowScale(),(h-top-h*.06f)/Math.max(1,rows.size()));
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);float y=top+i*rh,box=rh*.82f,left=pad;
            if(!row.number().isBlank()){g.setStroke(new BasicStroke(Math.max(1,box*.027f)));g.setColor(new Color(s.foreground()));g.draw(new Rectangle2D.Float(left,y+rh*.04f,box,box));ink(g,s,row.number(),s.foreground(),left+box*.07f,y+rh*.04f+box*.10f,box*.86f,box*.78f,"center");left+=box+rh*.38f;}
            boolean detail=!row.detail().isBlank();ink(g,s,row.text(),s.foreground(),left,y+rh*(detail?.03f:.13f),w-pad-left,rh*(detail?.47f:.64f),"left");
            if(detail)text(g,row.detail(),face(s,false,rh*.22f),s.foreground(),left,y+rh*.56f,w-pad-left,rh*.26f,"left");
        }
    }
    private static void stack(Graphics2D g,SillonSignageSpec s,int w,int h){
        var rows=rows(s);float unit=h/(float)Math.max(1,rows.size());
        for(int i=0;i<rows.size();i++){var row=rows.get(i);if(row.kind().equals("service"))row=translateService(s,row);boolean exit=row.kind().equals("exit")||row.kind().equals("sector");int bg=exit?s.background():WHITE,fg=exit?s.foreground():profile(s).equals("ratp")?NAVY:0x000000;
            if(s.options().freePalette()){bg=s.background();fg=s.foreground();}
            destination(g,s,row,0,i*unit,w,unit,bg,fg,false);
            if(i>0){g.setColor(new Color(exit?s.foreground():0xA5A5A5));g.setStroke(new BasicStroke(Math.max(1,h*.001f)));g.draw(new Line2D.Float(0,i*unit,w,i*unit));}
        }
    }
    private static void vertical(Graphics2D g,SillonSignageSpec s,int w,int h){
        var symbols=ids(s.icons());String mode=symbols.stream().filter(x->SillonSignagePainter.MODE_ICONS.contains(x)).findFirst().orElse("ratp_metro");
        var lines=symbols.stream().filter(x->SillonSignageLineBadge.parse(x)!=null||x.matches("idfm_[mrt][0-9a-z_]+" )&&!SillonSignagePainter.MODE_ICONS.contains(x)).toList();
        float diameter=Math.min(w*1.04f,h*.31f),center=w*.53f;
        icons(g,contrastSymbols(mode,s.background(),s.foreground()),center-diameter/2,h*.035f,diameter,diameter,diameter*s.options().iconScale());
        float unit=Math.min(w*1.01f,h*.30f/Math.max(1,lines.size())),top=h*.36f;
        for(String line:lines){icons(g,contrastSymbols(line,s.background(),s.foreground()),center-unit/2,top,unit,unit,unit*s.options().iconScale());top+=unit*1.12f;}
        arrow(g,s.arrow(),w*.5f,h*.865f,Math.min(w*.70f,h*.22f),s.foreground());
    }
    private static void services(Graphics2D g,SillonSignageSpec s,int w,int h){
        var data=rows(s);float unit=h/(float)Math.max(1,data.size());
        for(int i=0;i<data.size();i++){var r=translateService(s,data.get(i));
            destination(g,s,r,0,i*unit,w,unit,s.background(),s.foreground(),false);
        }
    }
    private static SillonSignageRow translateService(SillonSignageSpec s,SillonSignageRow r){
        if(!s.design().translations())return r;
        String translated=switch(r.text().toLowerCase(Locale.ROOT)){case "ascenseur"->"Lift · Ascensore";case "escalator"->"Escalator · Escalera mecánica";case "escalier"->"Stairs · Escalera";default->"";};
        if(translated.isBlank()||r.detail().contains(translated))return r;
        return new SillonSignageRow("service",r.number(),r.text(),translated+(r.detail().isBlank()?"":"  /  "+r.detail()),r.icons(),r.arrow());
    }
    private static void plan(Graphics2D g,SillonSignageSpec s,int w,int h){float pad=pad(s,w,h);ink(g,s,s.title(),s.foreground(),pad,h*.04f,w-pad*2,h*.10f,"left");var im=resource(s.options().image());if(im!=null)image(g,im,pad,h*.18f,w-pad*2,h*.78f);}
    private static void digital(Graphics2D g,SillonSignageSpec s,int w,int h){
        int blue=NAVY,amber=0xFFC334;float edge=Math.min(w,h)*.02f,head=h*.29f;
        g.setColor(Color.BLACK);g.fillRect(0,0,w,h);g.setColor(Color.WHITE);g.fill(new Rectangle2D.Float(edge,edge,w-2*edge,head-edge));
        float used=icons(g,contrastSymbols(s.icons(),WHITE,blue),edge*2,edge*1.5f,w*.19f,head-edge*2,h*.22f*s.options().iconScale());
        ink(g,s,s.title(),blue,edge*2+used+h*.045f,head*.28f,w-used-w*.17f-edge*4,head*.48f,"left");
        String clock=s.subtitle().matches("[0-9]{2}:[0-9]{2}")?s.subtitle():"";
        if(!clock.isBlank()){g.setColor(Color.BLACK);g.fill(new RoundRectangle2D.Float(w-w*.15f-edge*2,edge,w*.15f,head*.72f,8,8));ink(g,s,clock,amber,w-w*.145f-edge*2,edge*1.6f,w*.14f,head*.43f,"center");}
        var arrivals=s.effectiveRows("transport").stream().filter(r->!r.number().isBlank()).toList();float mid=w*.51f,bottom=h*.94f,bodyTop=head+h*.02f;
        g.setColor(Color.WHITE);g.fill(new RoundRectangle2D.Float(mid,bodyTop,w-mid-edge,bottom-bodyTop,12,12));
        String mode=ids(s.icons()).stream().map(SillonSignageLineBadge::parse).filter(Objects::nonNull).map(SillonSignageLineBadge::mode).findFirst().orElse("metro");
        String vehicle=switch(mode){case "metro"->"métro";case "tram"->"tram";case "bus"->"bus";case "boat"->"bateau";case "cable"->"cabine";default->"train";};
        for(int i=0;i<2;i++){float x=edge+i*(mid-edge)/2,cw=(mid-edge)/2;
            String ordinal=vehicle.equals("cabine")?(i==0?"1re":"2e"):(i==0?"1er":"2e");
            text(g,ordinal+" "+vehicle,face(s,false,h*.085f),WHITE,x+cw*.07f,bodyTop+h*.015f,cw*.86f,h*.13f,"left");
            String wait=i<arrivals.size()?arrivals.get(i).number().replace(" min","").replace("À l’approche","0"):"–";
            ink(g,s,wait,amber,x+cw*.09f,bodyTop+h*.22f,cw*.82f,h*.34f,"center");
        }
        g.setColor(Color.WHITE);g.setStroke(new BasicStroke(Math.max(1,h*.006f)));g.draw(new Line2D.Float(mid/2,bodyTop+h*.24f,mid/2,bottom-h*.08f));
        float tx=mid+w*.025f,tw=w-mid-w*.05f;icons(g,contrastSymbols(s.icons(),WHITE,blue),tx,bodyTop+h*.045f,tw,h*.16f,h*.14f);
        String info=!s.design().notice().isBlank()?s.design().notice():!s.body().isBlank()?s.body():arrivals.isEmpty()?"Aucun départ annoncé":arrivals.get(0).text()+(arrivals.get(0).detail().isBlank()?"":"|"+arrivals.get(0).detail());
        text(g,paragraph(g,info,face(s,false,h*.105f),tw),face(s,false,h*.105f),0x111111,tx,bodyTop+h*.23f,tw,h*.36f,"left");
        g.setColor(new Color(s.options().freePalette()?s.accent():0xC98B19));g.fill(new Rectangle2D.Float(0,h*.955f,w,h*.045f));
    }
    private static String paragraph(Graphics2D g,String value,Font face,float width){
        List<String> lines=new ArrayList<>();
        for(String paragraph:value.replace('\n','|').split("\\|",-1)){String line="";for(String word:paragraph.split("\\s+")){String next=line.isBlank()?word:line+" "+word;if(!line.isBlank()&&face.getStringBounds(next,g.getFontRenderContext()).getWidth()>width){lines.add(line);line=word;}else line=next;}lines.add(line);}
        return String.join("|",lines);
    }
}
