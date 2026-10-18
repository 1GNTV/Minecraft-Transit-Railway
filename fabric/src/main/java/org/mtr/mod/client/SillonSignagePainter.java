package org.mtr.mod.client;

import org.mtr.mod.data.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.imageio.ImageIO;

/** Pure compositor used unchanged by Minecraft and the headless visual checks. */
public final class SillonSignagePainter {
    public static final int NAVY=0x101D56, WHITE=0xFFFFFF, DARK=0x132047, GREY=0x6F6760;
    private static final Map<String,Optional<BufferedImage>> IMAGES=new ConcurrentHashMap<>();
    private static final Map<String,Font> FONTS=new ConcurrentHashMap<>();
    private static final Map<String,BufferedImage> LOCKUPS=new ConcurrentHashMap<>();
    private static volatile Function<String,BufferedImage> external;
    private SillonSignagePainter(){}
    public static void setResourceResolver(Function<String,BufferedImage> resolver){external=resolver;}
    public static void clearCache(){IMAGES.clear();LOCKUPS.clear();}
    public static int[] dimensions(SillonSignageSpec spec) {return dimensions(spec,2048);}
    public static int[] dimensions(SillonSignageSpec spec,int maximum) {
        double scale=Math.min(512,Math.min(maximum/Math.max(spec.width(),spec.height()),
            Math.sqrt(2097152.0/(spec.width()*spec.height()))));
        return new int[]{Math.max(16,(int)Math.round(spec.width()*scale)),Math.max(16,(int)Math.round(spec.height()*scale))};
    }
    public static BufferedImage render(SillonSignageSpec spec) {return render(spec,2048);}
    public static BufferedImage render(SillonSignageSpec spec,int maximum) {
        int[] size=dimensions(spec,Math.max(64,maximum));
        BufferedImage image=new BufferedImage(size[0],size[1],BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try{
            quality(g);
            SillonRATPSignageLayouts.draw(g,spec,size[0],size[1]);
        }finally{g.dispose();}
        return image;
    }
    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    public static Font font(String family,boolean bold,float size) {
        String file=switch(family) {
            case "grand_paris"->"signage/fonts/GrandParis-"+(bold?"Bold":"Regular")+".otf";
            case "idf_voyageur"->"signage/fonts/IDFVoyageur-"+(bold?"Medium":"Regular")+".otf";
            default->"font/panam/Parisine-"+(bold?"Bold":"Regular")+".otf";
        };
        Font f=FONTS.computeIfAbsent(file,key->{
            try(InputStream in=SillonSignagePainter.class.getClassLoader().getResourceAsStream("assets/mtr/"+key)) {
                if(in!=null)return Font.createFont(Font.TRUETYPE_FONT,in);
            }catch(Exception ignored){}
            return new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,64);
        });
        return f.deriveFont(Math.max(.5f,size));
    }
    public static BufferedImage resource(String path) {
        if(path==null||path.isBlank()||path.contains("..")||!path.matches("[a-z0-9_.:/-]+"))return null;
        return IMAGES.computeIfAbsent(path,key->{
            try{
                Function<String,BufferedImage> loader=external;
                if(loader!=null) {
                    BufferedImage found=loader.apply(key);if(found!=null)return Optional.of(found);
                }
                String full=key.contains(":")?"assets/"+key.replace(':','/'):"assets/mtr/"+key;
                try(InputStream input=SillonSignagePainter.class.getClassLoader().getResourceAsStream(full)) {
                    if(input!=null)return Optional.ofNullable(ImageIO.read(input));
                }
            }catch(Exception ignored){}
            return Optional.empty();
        }).orElse(null);
    }
    public static BufferedImage icon(String id) {
        if(id==null||!id.matches("[a-zA-Z0-9_/-]+"))return null;
        return resource("signage/icons/"+id.toLowerCase(Locale.ROOT)+".png");
    }
    public static BufferedImage sectorLockup(String id) {
        if(id==null||id.isBlank())return null;
        BufferedImage source=icon(id);if(source==null)return null;
        return LOCKUPS.computeIfAbsent(id,key->{
            int x1=source.getWidth(),y1=source.getHeight(),x2=-1,y2=-1;
            for(int y=2;y<source.getHeight()-2;y++)for(int x=2;x<source.getWidth()-2;x++){
                int pixel=source.getRGB(x,y);
                if((pixel>>>24)>128&&((pixel>>>16)&255)>210&&((pixel>>>8)&255)>210&&(pixel&255)>210) {
                    x1=Math.min(x1,x);y1=Math.min(y1,y);x2=Math.max(x2,x);y2=Math.max(y2,y);
                }
            }
            if(x2<x1)return source;
            x1=Math.max(0,x1-8);y1=Math.max(0,y1-8);x2=Math.min(source.getWidth()-1,x2+8);y2=Math.min(source.getHeight()-1,y2+8);
            // Sector artwork contains white ink over a flat original background.
            // Extract ink coverage so a chosen band colour remains fully editable.
            BufferedImage result=new BufferedImage(x2-x1+1,y2-y1+1,BufferedImage.TYPE_INT_ARGB);
            int background=source.getRGB(source.getWidth()/16,source.getHeight()/16);
            int br=(background>>>16)&255,bg=(background>>>8)&255,bb=background&255;
            float range=Math.max(1,765-br-bg-bb);
            for(int y=0;y<result.getHeight();y++)for(int x=0;x<result.getWidth();x++) {
                int pixel=source.getRGB(x+x1,y+y1);
                float coverage=(((pixel>>>16)&255)-br+((pixel>>>8)&255)-bg+(pixel&255)-bb)/range;
                int alpha=Math.round(Math.max(0,Math.min(1,coverage))*(pixel>>>24));
                result.setRGB(x,y,(alpha<<24)|0xFFFFFF);
            }
            return result;
        });
    }
    public static void image(Graphics2D g,BufferedImage img,float x,float y,float w,float h) {
        if(img==null||w<=0||h<=0)return;
        float scale=Math.min(w/img.getWidth(),h/img.getHeight());
        float dw=img.getWidth()*scale,dh=img.getHeight()*scale;
        g.drawImage(img,Math.round(x+(w-dw)/2),Math.round(y+(h-dh)/2),Math.round(dw),Math.round(dh),null);
    }
    public static List<String> ids(String text) {
        if(text==null||text.isBlank())return List.of();
        return Arrays.stream(text.split(",")).map(String::strip).filter(x->!x.isBlank()).limit(16).toList();
    }
    /** Returns width consumed. Keeps source aspect ratio and fits the entire group. */
    public static float icons(Graphics2D g,String text,float x,float y,float maxW,float maxH,float preferred) {
        List<String> ids=ids(text);if(ids.isEmpty()||maxW<=0||maxH<=0)return 0;
        float unit=Math.min(preferred,maxH),gap=unit*.15f,total=0;
        for(String id:ids) {
            BufferedImage im=icon(id);total+=unit*(im==null?1:im.getWidth()/(float)im.getHeight())+gap;
        }
        total-=gap;float scale=Math.min(1,maxW/Math.max(1,total));unit*=scale;gap*=scale;
        float start=x;
        for(String id:ids) {
            BufferedImage im=icon(id);float iw=unit*(im==null?1:im.getWidth()/(float)im.getHeight());
            if(im!=null)image(g,im,x,y+(maxH-unit)/2,iw,unit);
            else {
                g.setColor(new Color(0xD5D9DF));g.fill(new Rectangle2D.Float(x,y+(maxH-unit)/2,iw,unit));
                text(g,"?",font("parisine",true,unit*.5f),DARK,x,y,iw,maxH,"center");
            }
            x+=iw+gap;
        }
        return x-start-gap;
    }
    /** Fit by measured glyph bounds, then clip to the allocated content box. */
    public static void text(Graphics2D g,String value,Font font,int color,float x,float y,float w,float h,String align) {
        if(value==null||value.isBlank()||w<=1||h<=1)return;
        String[] lines=value.replace('\n','|').split("\\|",-1);
        float widest=1;
        for(String line:lines)widest=Math.max(widest,(float)font.getStringBounds(line,g.getFontRenderContext()).getWidth());
        LineMetricsInfo initial=metrics(g,font);
        float factor=Math.min(1,Math.min(w/widest,h/(initial.line*lines.length)));
        Font fitted=font.deriveFont(Math.max(.5f,font.getSize2D()*factor));
        LineMetricsInfo m=metrics(g,fitted);g.setFont(fitted);
        Shape clip=g.getClip();g.clip(new Rectangle2D.Float(x,y,w,h));
        g.setColor(new Color(color));
        float baseline=y+(h-m.line*lines.length)/2+m.ascent;
        for(String line:lines) {
            float tw=(float)fitted.getStringBounds(line,g.getFontRenderContext()).getWidth();
            float tx="center".equals(align)?x+(w-tw)/2:"right".equals(align)?x+w-tw:x;
            g.drawString(line,tx,baseline);baseline+=m.line;
        }
        g.setClip(clip);
    }
    private record LineMetricsInfo(float line,float ascent){}
    private static LineMetricsInfo metrics(Graphics2D g,Font f) {
        var m=f.getLineMetrics("ÉAgjp",g.getFontRenderContext());
        return new LineMetricsInfo(m.getHeight(),m.getAscent());
    }
    /** Use the original RATP arrow raster, rotated for the complete eight-direction set. */
    public static void arrow(Graphics2D g,int direction,float x,float y,float size,int color) {
        if(direction<0||size<=0)return;
        BufferedImage source=icon("ratp_left");
        Graphics2D a=(Graphics2D)g.create();
        try{
            a.translate(x,y);
            double degrees=switch(direction){case 0->0;case 1->180;case 2->90;case 3->-90;case 4->45;case 5->135;case 6->-45;default->-135;};
            a.rotate(Math.toRadians(degrees));
            if(source!=null) {
                BufferedImage mask=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
                Graphics2D tint=mask.createGraphics();tint.drawImage(source,0,0,null);
                tint.setComposite(AlphaComposite.SrcIn);tint.setColor(new Color(color));tint.fillRect(0,0,mask.getWidth(),mask.getHeight());tint.dispose();
                image(a,mask,-size/2,-size/2,size,size);mask.flush();
            }else {
                a.setColor(new Color(color));
                a.setStroke(new BasicStroke(size*.12f,BasicStroke.CAP_SQUARE,BasicStroke.JOIN_MITER));
                Path2D p=new Path2D.Float();p.moveTo(size*.37f,0);p.lineTo(-size*.37f,0);
                p.moveTo(-size*.02f,-size*.34f);p.lineTo(-size*.37f,0);p.lineTo(-size*.02f,size*.34f);a.draw(p);
            }
        }finally{a.dispose();}
    }
}
