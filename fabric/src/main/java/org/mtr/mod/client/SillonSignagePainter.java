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
            case "idf_voyageur"->"signage/fonts/IDFVoyageur-"+(bold?"Bold":"Regular")+".otf";
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
        if(id!=null&&id.startsWith("ink_")){
            String[] parts=id.split("_",3);
            if(parts.length==3&&parts[1].matches("[0-9a-f]{6}")){
                var line=SillonSignageLineBadge.parse(parts[2]);int colour=Integer.parseInt(parts[1],16);
                if(line!=null)return IMAGES.computeIfAbsent(id,key->Optional.of(lineBadge(line,colour))).orElse(null);
                if(MODE_ICONS.contains(parts[2])){BufferedImage source=icon(parts[2]);if(source==null)return null;return IMAGES.computeIfAbsent(id,key->{
                    BufferedImage result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)result.setRGB(x,y,(source.getRGB(x,y)&0xFF000000)|colour);
                    return Optional.of(result);
                }).orElse(null);}
            }
        }
        SillonSignageLineBadge badge=id==null?null:SillonSignageLineBadge.parse(id);
        if(badge!=null)return IMAGES.computeIfAbsent(id,key->Optional.of(lineBadge(badge))).orElse(null);
        if(id==null||!id.matches("[a-zA-Z0-9_/-]+"))return null;
        return resource("signage/icons/"+id.toLowerCase(Locale.ROOT)+".png");
    }
    public static final Set<String> MODE_ICONS=Set.of("ratp_metro","ratp_rer","idfm_metro_2","idfm_rer_2","idfm_train_2","idfm_bus_2","idfm_tram_2","idfm_cable_2","idfm_ferry_2");
    private static BufferedImage lineBadge(SillonSignageLineBadge badge){return lineBadge(badge,0x000000);}
    private static BufferedImage lineBadge(SillonSignageLineBadge badge,int ink) {
        boolean surface=Set.of("tram","cable","boat").contains(badge.mode()),bus="bus".equals(badge.mode());
        int width=bus?806:512;BufferedImage image=new BufferedImage(width,512,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{quality(g);int colour=badge.color();String label=badge.label();
            if(surface){
                g.setColor(new Color(colour));g.fill(new RoundRectangle2D.Float(0,0,512,77,52,52));g.fill(new RoundRectangle2D.Float(0,435,512,77,52,52));
                String prefix=switch(badge.mode()){case "tram"->"T";case "cable"->"C";default->"F";};
                if(!label.toUpperCase(Locale.ROOT).startsWith(prefix))label=prefix+label;
                inkText(g,label,font("idf_voyageur",true,400),ink,22,140,468,232,"center");
            }else{
                Shape shape="metro".equals(badge.mode())?new Ellipse2D.Float(0,0,512,512):new RoundRectangle2D.Float(0,0,width,512,bus?77:230,bus?77:230);
                g.setColor(new Color(colour));g.fill(shape);
                double luminance=.299*((colour>>16)&255)+.587*((colour>>8)&255)+.114*(colour&255);
                int text=luminance>150?0x000000:0xFFFFFF;
                inkText(g,label,font("metro".equals(badge.mode())?"parisine":"idf_voyageur",true,400),text,width*.12f,512*.225f,width*.76f,512*.55f,"center");
            }
        }finally{g.dispose();}return image;
    }
    /** Actual ink box, rather than font line-height, for fixed-size symbols and station plaques. */
    public static void inkText(Graphics2D g,String value,Font face,int color,float x,float y,float w,float h,String align){
        if(value==null||value.isBlank()||w<=0||h<=0)return;
        String[] lines=value.replace('\n','|').split("\\|",-1);float gap=lines.length>1?h*.10f:0,lineH=(h-gap*(lines.length-1))/lines.length;
        float factor=1;java.util.List<Shape> shapes=new ArrayList<>();
        for(String line:lines){Shape shape=face.createGlyphVector(g.getFontRenderContext(),line.isEmpty()?" ":line).getOutline();shapes.add(shape);Rectangle2D b=shape.getBounds2D();factor=Math.min(factor,(float)Math.min(w/Math.max(1,b.getWidth()),lineH/Math.max(1,b.getHeight())));}
        Shape clip=g.getClip();g.clip(new Rectangle2D.Float(x,y,w,h));g.setColor(new Color(color));
        for(int i=0;i<shapes.size();i++){Shape shape=shapes.get(i);Rectangle2D b=shape.getBounds2D();double tw=b.getWidth()*factor,th=b.getHeight()*factor;
            double left=align.equals("center")?x+(w-tw)/2:align.equals("right")?x+w-tw:x;
            AffineTransform tx=new AffineTransform();tx.translate(left-b.getX()*factor,y+i*(lineH+gap)+(lineH-th)/2-b.getY()*factor);tx.scale(factor,factor);g.fill(tx.createTransformedShape(shape));}
        g.setClip(clip);
    }
    public static BufferedImage sectorSymbol(String id){
        if(id==null||id.isBlank())return null;BufferedImage im=icon(id);if(im==null)return null;
        return LOCKUPS.computeIfAbsent("symbol/"+id,key->{
            int x1=im.getWidth(),x2=0,y1=im.getHeight(),y2=0;
            for(int y=0;y<im.getHeight()*.55;y++)for(int x=0;x<im.getWidth();x++){int c=im.getRGB(x,y);if((c>>>24)>128&&(c&255)>210&&((c>>>8)&255)>210&&((c>>>16)&255)>210){x1=Math.min(x1,x);x2=Math.max(x2,x);y1=Math.min(y1,y);y2=Math.max(y2,y);}}
            if(x2<=x1||y2<=y1)return im;BufferedImage out=new BufferedImage(x2-x1+1,y2-y1+1,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++){int c=im.getRGB(x+x1,y+y1);int alpha=Math.max(0,Math.min(255,((c&255)-130)*255/125));out.setRGB(x,y,(alpha<<24)|0xFFFFFF);}return out;
        });
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
    public static String contrastSymbols(String symbols,int background,int foreground){
        return String.join(",",ids(symbols).stream().map(id->{
            if(id.startsWith("ink_"))return id;
            var badge=SillonSignageLineBadge.parse(id);
            return MODE_ICONS.contains(id)||badge!=null&&Set.of("tram","cable","boat").contains(badge.mode())?"ink_"+String.format(Locale.ROOT,"%06x",foreground&0xFFFFFF)+"_"+id:id;
        }).toList());
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
    /** Official vector-derived arrows retain the exact diagonal forms without raster rotation. */
    public static void arrow(Graphics2D g,int direction,float x,float y,float size,int color){
        if(direction<0||direction>7||size<=0)return;String key="arrow/"+direction+"/"+color;
        BufferedImage original=icon("official_arrow_"+direction);if(original==null)return;
        BufferedImage tinted=IMAGES.computeIfAbsent(key,k->{
            BufferedImage source=original;
            BufferedImage mask=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D a=mask.createGraphics();
            a.drawImage(source,0,0,null);a.setComposite(AlphaComposite.SrcIn);a.setColor(new Color(color));a.fillRect(0,0,mask.getWidth(),mask.getHeight());a.dispose();return Optional.of(mask);
        }).orElse(null);image(g,tinted,x-size/2,y-size/2,size,size);
    }
}
