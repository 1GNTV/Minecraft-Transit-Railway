package org.mtr.mod.client;

import org.mtr.mapping.holder.NativeImage;
import org.mtr.mapping.holder.NativeImageFormat;
import org.mtr.mod.data.SillonSignageSpec;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signage's software compositor. Uses Sillon's bundled Parisine OTF fonts;
 * source-provided MTR France pictograms are optional and never copied into
 * the Sillon distribution. The existing dynamic-texture queue performs GPU
 * uploads only when a document changes.
 */
public final class SillonSignageCanvas {
    private static volatile Font normal,bold;
    private static final Map<String,BufferedImage> icons=new ConcurrentHashMap<>();
    private SillonSignageCanvas(){}

    private static Font loadFont(String name,boolean heavy){
        try(InputStream is=SillonSignageCanvas.class.getClassLoader().getResourceAsStream(
            "assets/mtr/font/panam/"+name)){
            return is==null?new Font(Font.SANS_SERIF,heavy?Font.BOLD:Font.PLAIN,64)
                :Font.createFont(Font.TRUETYPE_FONT,is);
        }catch(Exception ignored){return new Font(Font.SANS_SERIF,heavy?Font.BOLD:Font.PLAIN,64);}
    }
    private static void ensureFonts(){
        if(normal==null) synchronized(SillonSignageCanvas.class){
            if(normal==null){
                Font regular=loadFont("Parisine-Regular.otf",false);
                bold=loadFont("Parisine-Bold.otf",true);
                normal=regular;
            }
        }
    }
    private static Font font(boolean strong,float size){
        ensureFonts();
        return (strong?bold:normal).deriveFont(size);
    }
    public static NativeImage generate(SillonSignageSpec spec){
        int width=Math.max(256,Math.min(1536,Math.round(spec.width()*512f)));
        int height=Math.max(160,Math.min(1536,Math.round(spec.height()*512f)));
        BufferedImage img=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            draw(g,spec,width,height);
        }finally{g.dispose();}
        NativeImage nativeImage=new NativeImage(NativeImageFormat.getAbgrMapped(),width,height,false);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int rgba=img.getRGB(x,y);
            nativeImage.setPixelColor(x,y,(rgba&0xFF00FF00)|((rgba>>16)&255)|((rgba&255)<<16));
        }
        img.flush();
        return nativeImage;
    }
    private static int background(SillonSignageSpec s){
        return switch(s.style()){
            case "vertical","platform","route","map","services"->0xFFF8F7F1;
            case "digital"->0xFF152543;
            case "direction","exit"->0xFF0A1A4C;
            case "custom"->0xFF000000|s.background();
            default->0xFF13235A;
        };
    }
    private static int foreground(SillonSignageSpec s){
        return switch(s.style()){
            case "vertical","platform","route","map","services"->0xFF111B43;
            case "custom"->0xFF000000|s.foreground();
            default->0xFFFFFFFF;
        };
    }
    private static void draw(Graphics2D g,SillonSignageSpec s,int w,int h){
        final int navy=background(s),white=foreground(s),margin=Math.max(16,Math.min(w,h)/16);
        g.setColor(new Color(navy,true));
        g.fill(new RoundRectangle2D.Float(1,1,w-2,h-2,Math.min(h/12f,28),Math.min(h/12f,28)));
        switch(s.style()){
            case "metro","metro_subtitle","rer"->{
                boolean band=!s.subtitle().isBlank() && !"metro".equals(s.style());
                int footer=band?Math.max(42,h/5):0;
                if("rer".equals(s.style()))badge(g,"RER",margin,margin,Math.max(52,h/3),0xFFF5F5F5,0xFF142252);
                center(g,s.title(),font(true,h*.29f),white,w/2,(h-footer)/2f+h*.10f,margin,w-margin);
                if(band){
                    g.setColor(new Color("rer".equals(s.style())?0xFF9B7840:0xFF805827,true));
                    g.fillRect(0,h-footer,w,footer);
                    center(g,s.subtitle(),font(true,footer*.51f),0xFFFFFFFF,w/2,h-footer*.28f,margin,w-margin);
                }
            }
            case "direction","exit"->{
                int iconSize=Math.min(110,h/3);
                drawIcons(g,s.icons(),margin,margin,iconSize,8,w-margin*2);
                String title="exit".equals(s.style())?"Sortie":s.title();
                left(g,title,font(true,h*.27f),white,margin,Math.min(h*.52f,h-margin),w-2*margin-130);
                drawArrow(g,s.arrow(),w-margin-60,h*.56f,Math.min(95,h*.24f),white);
                rows(g,"exit".equals(s.style())?s.body():s.subtitle()+"|"+s.body(),
                    margin,h*.67f,w-margin,Math.max(14,h*.095f),white);
            }
            case "vertical"->{
                drawIcons(g,s.icons(),margin,margin,Math.min(w/3,h/4),8,w-margin*2);
                center(g,s.title(),font(true,Math.min(h*.19f,w*.13f)),white,w/2,h*.49f,margin,w-margin);
                drawArrow(g,s.arrow(),w/2,h*.82f,Math.min(w,h)*.16f,white);
            }
            case "platform","route"->{
                drawIcons(g,s.icons(),margin,margin,Math.min(110,h/4),8,w-margin*2);
                center(g,s.title(),font(true,h*.22f),white,w/2,h*.42f,margin,w-margin);
                drawRoute(g,s.body(),margin,h*.63f,w-margin,h*.85f,
                    0xFF352E7C,0xFF111B43);
            }
            case "map"->{
                left(g,s.title(),font(true,h*.16f),white,margin,h*.20f,w-margin*2);
                drawMap(g,s.body(),margin,h*.3f,w-margin,h-margin,white);
            }
            case "services"->{
                drawIcons(g,s.icons(),margin,margin,Math.min(128,h/3),12,w-margin*2);
                left(g,s.title(),font(true,h*.24f),white,margin,h*.65f,w-margin*2);
                rows(g,s.body(),margin,h*.77f,w-margin,h*.115f,white);
            }
            case "totem"->{
                center(g,"M",font(true,Math.min(w,h)*.46f),white,w/2,h*.44f,margin,w-margin);
                drawIcons(g,s.icons(),margin,h/2,Math.min(100,w/3),9,w-margin*2);
                drawArrow(g,s.arrow(),w/2,h*.86f,Math.min(w,h)*.12f,white);
            }
            case "digital"->{
                left(g,s.title(),font(true,h*.22f),white,margin,h*.29f,w-margin*2);
                rows(g,s.body().isBlank()?s.subtitle():s.body(),margin,h*.53f,w-margin,
                    Math.max(16,h*.12f),0xFFFFFFCE);
                drawIcons(g,s.icons(),margin,h-margin-70,Math.min(60,h/6),6,w-margin*2);
            }
            default->{
                center(g,s.title(),font(true,h*.32f),white,w/2,h*.48f,margin,w-margin);
                center(g,s.subtitle(),font(false,h*.12f),white,w/2,h*.67f,margin,w-margin);
                rows(g,s.body(),margin,h*.80f,w-margin,Math.max(13,h*.09f),white);
                drawIcons(g,s.icons(),margin,margin,Math.min(90,h/4),8,w-margin*2);
            }
        }
        g.setColor(new Color(0x44000000,true));
        g.setStroke(new BasicStroke(Math.max(2,h/180f)));
        g.draw(new RoundRectangle2D.Float(2,2,w-4,h-4,Math.min(h/12f,28),Math.min(h/12f,28)));
    }
    private static void left(Graphics2D g,String text,Font font,int color,float x,float y,float available){
        if(text==null||text.isBlank())return;
        fit(g,text,font,available);
        g.setColor(new Color(color,true));
        g.drawString(text,x,y);
    }
    private static void center(Graphics2D g,String text,Font font,int color,float x,float y,float xmin,float xmax){
        if(text==null||text.isBlank())return;
        fit(g,text,font,xmax-xmin);
        g.setColor(new Color(color,true));
        g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2f,y);
    }
    private static void fit(Graphics2D g,String text,Font start,float maxWidth){
        Font f=start;
        for(int i=0;i<20&&f.getSize2D()>9;i++){
            g.setFont(f);
            if(g.getFontMetrics().stringWidth(text)<=maxWidth)break;
            f=f.deriveFont(f.getSize2D()*.91f);
        }
        g.setFont(f);
    }
    private static void rows(Graphics2D g,String text,int x,float y,int xmax,float size,int color){
        int n=0;
        for(String line:text.split("\\|",-1)){
            if(line.isBlank())continue;
            if(++n>6)break;
            left(g,line.trim(),font(n==1,size*(n==1?1f:.86f)),color,x,y+(n-1)*size*1.33f,xmax-x);
        }
    }
    private static void badge(Graphics2D g,String label,int x,int y,int size,int bg,int fg){
        g.setColor(new Color(bg,true));g.fillOval(x,y,size,size);
        g.setColor(new Color(fg,true));g.setStroke(new BasicStroke(Math.max(2,size/36f)));g.drawOval(x+2,y+2,size-4,size-4);
        center(g,label,font(true,size*.44f),fg,x+size/2f,y+size*.64f,x+3,x+size-3);
    }
    private static int colorFor(String id){
        return switch(id){
            case "idfm_m1"->0xFFF2C500;
            case "idfm_m4"->0xFFBE7895;
            case "idfm_m14"->0xFF662483;
            case "idfm_m11"->0xFF8D5E2A;
            case "idfm_m7"->0xFFF3A4BA;
            default->0xFF4D5D9E;
        };
    }
    private static BufferedImage icon(String id){
        if(icons.containsKey(id))return icons.get(id);
        String family=id.startsWith("ratp_")?"ratp":id.startsWith("sncf_")?"sncf":"idfm";
        String root="assets/mtr/mtr_france_sign/"+family+"/";
        String[] paths={root+id+".png",root+"voies/"+id+".png",
            "assets/mtr/mtr_france_sign/sncf/voies/"+id+".png"};
        for(String path:paths)try(InputStream input=SillonSignageCanvas.class.getClassLoader().getResourceAsStream(path)){
            if(input==null)continue;
            BufferedImage img=ImageIO.read(input);
            if(img!=null){icons.put(id,img);return img;}
        }catch(Exception ignored){}
        return null;
    }
    private static void drawIcons(Graphics2D g,String values,int x,int y,int size,int gap,int maxWidth){
        if(values.isBlank())return;
        int count=0;
        for(String id:values.split(",")){
            id=id.trim().toLowerCase(java.util.Locale.ROOT);
            if(id.isBlank()||!id.matches("[a-z0-9_/-]+"))continue;
            if(++count>16||x+size>maxWidth)break;
            BufferedImage raster=icon(id);
            if(raster!=null)g.drawImage(raster,x,y,size,size,null);
            else{
                String name=id.replaceFirst("^(idfm_|ratp_|sncf_)","");
                name=name.replaceFirst("^(metro_|rer_)","");
                if(name.startsWith("m")&&name.length()>1&&Character.isDigit(name.charAt(1)))name=name.substring(1);
                badge(g,name.toUpperCase(java.util.Locale.ROOT),x,y,size,colorFor(id),0xFFFEFEFE);
            }
            x+=size+gap;
        }
    }
    private static void drawArrow(Graphics2D g,int direction,float x,float y,float size,int color){
        if(direction<0)return;
        g.setColor(new Color(color,true));
        g.setStroke(new BasicStroke(Math.max(5,size*.13f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        float dx=0,dy=0;
        final float diagonal=(float)(size/Math.sqrt(2.0));
        switch(direction){
            case 0->dx=-size;
            case 1->dx=size;
            case 2->dy=-size;
            case 3->dy=size;
            case 4->{dx=-diagonal;dy=-diagonal;}
            case 5->{dx=diagonal;dy=-diagonal;}
            case 6->{dx=-diagonal;dy=diagonal;}
            case 7->{dx=diagonal;dy=diagonal;}
            default->{return;}
        }
        float nx=dx/size,ny=dy/size;
        g.drawLine(Math.round(x-dx*.35f),Math.round(y-dy*.35f),Math.round(x+dx*.35f),Math.round(y+dy*.35f));
        float hx=x+dx*.35f,hy=y+dy*.35f;
        g.drawLine(Math.round(hx),Math.round(hy),Math.round(hx-nx*size*.36f-ny*size*.30f),Math.round(hy-ny*size*.36f+nx*size*.30f));
        g.drawLine(Math.round(hx),Math.round(hy),Math.round(hx-nx*size*.36f+ny*size*.30f),Math.round(hy-ny*size*.36f-nx*size*.30f));
    }
    private static void drawRoute(Graphics2D g,String body,int x,float y,int xmax,float bottom,int rail,int labelColor){
        String[] names=body.isBlank()?new String[]{"Station","Correspondance","Terminus"}:body.split("\\|");
        int n=Math.min(12,names.length);
        if(n==0)return;
        float step=n<=1?0:(xmax-x)/(float)(n-1);
        g.setColor(new Color(rail,true));g.setStroke(new BasicStroke(7,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.drawLine(x,Math.round(y),xmax,Math.round(y));
        for(int i=0;i<n;i++){
            int sx=Math.round(x+step*i);
            g.setColor(Color.WHITE);g.fillOval(sx-9,Math.round(y)-9,18,18);
            g.setColor(new Color(rail,true));g.setStroke(new BasicStroke(3));g.drawOval(sx-9,Math.round(y)-9,18,18);
            center(g,names[i].trim(),font(true,Math.max(11,Math.min(24,(bottom-y)*.33f))),labelColor,
                sx,Math.min(bottom,y+36),Math.max(0,sx-step*.5f),Math.min(xmax,sx+step*.5f));
        }
    }
    private static void drawMap(Graphics2D g,String body,int x,float top,int xmax,float bottom,int color){
        String[] lines=body.isBlank()?new String[]{"Entrée","Billetterie","Quais","Sortie"}:body.split("\\|");
        int n=Math.min(12,lines.length);
        int center=Math.round((top+bottom)/2f);
        g.setColor(new Color(0xFF637AA2,true));g.setStroke(new BasicStroke(7));
        g.drawLine(x+30,center,xmax-35,center);
        for(int i=0;i<n;i++){
            int sx=x+30+(int)((xmax-x-65)*i/(float)Math.max(1,n-1));
            g.setColor(new Color(0xFFF3A334,true));g.fillOval(sx-10,center-10,20,20);
            center(g,lines[i].trim(),font(true,Math.max(12,(bottom-top)*.07f)),color,sx,
                (i%2==0?center-27:center+39),Math.max(x,sx-85),Math.min(xmax,sx+85));
        }
    }
}
