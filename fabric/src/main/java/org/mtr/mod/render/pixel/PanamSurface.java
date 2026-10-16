package org.mtr.mod.render.pixel;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import org.mtr.mod.data.PanamBulletins;

/** Independent raster compositor. No Minecraft or OpenGL dependencies: the visual oracle uses this exact code. */
public final class PanamSurface {
    public static final int WIDTH = 1024, HEIGHT = 466;
    public static final int NAVY = 0x0E0184, YELLOW = 0xFFBE00, WHITE = 0xFFFFFF;
    public static final float HEADER = 130f, FOOTER = 30f, BODY = HEIGHT - HEADER - FOOTER;
    // SOLIS: 2-block-wide PIDS, 12% taller, suspended slightly below
    // the original Clock level. The slightly lowered chin matches the
    // photographed RATP housing; the twin ceiling rods remain separate.
    public static final float WORLD_WIDTH = 1.95f, WORLD_HEIGHT = 13.15f / 16, TOP = 12.75f / 16;
    public static final class Spec {
        public final PanamViewport viewport;
        public final String line, destination;
        public final int color, count;
        public final boolean mixed, suspended, last;
        public final List<String> destinations, notices, previousDestinations;
        /** 0=network, 1=line notice, 3=important incident on this line. */
        public final List<Integer> noticePriorities;
        public final List<PanamNotice> noticeSources;
        public Spec(String line, String destination, int color, List<String> destinations, List<String> notices, boolean suspended, boolean last) {
            this(line,destination,color,destinations,notices,suspended,last,List.of());
        }
        public Spec(String line,String destination,int color,List<String> destinations,List<String> notices,boolean suspended,boolean last,List<String> previousDestinations){
            this(line,destination,color,destinations,notices,suspended,last,previousDestinations,
                java.util.Collections.nCopies(notices.size(),0));
        }
        public Spec(String line,String destination,int color,List<String> destinations,List<String> notices,boolean suspended,boolean last,List<String> previousDestinations,List<Integer> noticePriorities){
            this(line,destination,color,destinations,notices,suspended,last,previousDestinations,noticePriorities,null,null);
        }
        private Spec(String line,String destination,int color,List<String> destinations,List<String> notices,boolean suspended,boolean last,List<String> previousDestinations,List<Integer> noticePriorities,PanamViewport viewport,List<PanamNotice> noticeSources){
            this.viewport=viewport;
            if(notices.size()!=noticePriorities.size())throw new IllegalArgumentException("PANAM notice priorities out of sync");
            this.line = line; this.destination = destination; this.color = color & 0xFFFFFF;
            this.destinations = List.copyOf(destinations); this.notices = List.copyOf(notices);
            this.previousDestinations=List.copyOf(previousDestinations);
            this.noticePriorities=List.copyOf(noticePriorities);
            if(noticeSources==null){
                ArrayList<PanamNotice> defaults=new ArrayList<>();
                for(int i=0;i<notices.size();i++)defaults.add(new PanamNotice(line,color,notices.get(i),noticePriorities.get(i)));
                this.noticeSources=List.copyOf(defaults);
            }else{
                if(noticeSources.size()!=notices.size())throw new IllegalArgumentException("PANAM notice badges out of sync");
                for(int i=0;i<notices.size();i++)if(!noticeSources.get(i).message.equals(notices.get(i)))throw new IllegalArgumentException("PANAM notice badge/message mismatch");
                this.noticeSources=List.copyOf(noticeSources);
            }
            count = destinations.size(); mixed = destinations.stream().distinct().count() > 1;
            this.suspended = suspended; this.last = last && count == 1;
        }
        public Spec withViewport(PanamViewport viewport){return new Spec(line,destination,color,destinations,notices,suspended,last,previousDestinations,noticePriorities,viewport,noticeSources);}
        public Spec withNoticeSources(List<PanamNotice> sources){return new Spec(line,destination,color,destinations,notices,suspended,last,previousDestinations,noticePriorities,viewport,sources);}
        @Override public boolean equals(Object other) {
            if (!(other instanceof Spec)) return false;
            Spec s = (Spec) other;
            return Objects.equals(viewport,s.viewport) && color == s.color && suspended == s.suspended && last == s.last && line.equals(s.line) && destination.equals(s.destination) && destinations.equals(s.destinations) && notices.equals(s.notices)&&previousDestinations.equals(s.previousDestinations)&&noticePriorities.equals(s.noticePriorities)&&noticeSources.equals(s.noticeSources);
        }
        @Override public int hashCode() { return Objects.hash(viewport,line, destination, color, destinations, notices, suspended, last,previousDestinations,noticePriorities,noticeSources); }
    }
    public static final class Sprite {
        public final int x, y, w, h, originX, originY;
        public Sprite(int x, int y, int w, int h) { this(x,y,w,h,0,0); }
        public Sprite(int x,int y,int w,int h,int originX,int originY){this.x=x;this.y=y;this.w=w;this.h=h;this.originX=originX;this.originY=originY;}
    }
    public final Spec spec;
    public final PanamLayout layout;
    public final int rasterWidth,rasterHeight;
    public BufferedImage image;
    public final float atlasWidth, atlasHeight;
    public final Sprite base, infoIcon;
    public final Sprite[] noticeIcons;
    public final Sprite[] digits = new Sprite[10], clock = new Sprite[11], rows, notices, leavingRows;
    public final float[] digitAdvance = new float[10], clockAdvance = new float[11];
    public final Sprite dot, strikeFirst, strikeSecond;
    private final Font regular, bold;
    private int sx, sy, shelf;
    private BufferedImage canvas;

    public PanamSurface(Spec spec, Font regular, Font bold) {
        this.spec = spec; this.regular = regular; this.bold = bold;
        layout=spec.viewport==null?null:new PanamLayout(spec.viewport);
        canvas = new BufferedImage(layout==null?1024:Math.max(1024,layout.width+32), layout==null?(spec.notices.size()>3?4096:2048):8192, BufferedImage.TYPE_INT_ARGB);
        base = pack(layout==null?drawBase():drawAdaptiveBase());
        for (int i=0; i<10; i++) digits[i] = panamDigit(i);
        for (int i=0; i<10; i++) clock[i] = glyph(Integer.toString(i), regular, 43.008f, YELLOW, clockAdvance, i);
        clock[10] = glyph(":", regular, 43.008f, YELLOW, clockAdvance, 10);
        BufferedImage d = blank(38,38); Graphics2D dg = graphics(d); dg.setColor(new Color(YELLOW)); dg.fill(new Ellipse2D.Float(0,0,37,37)); dg.dispose(); dot = pack(d);
        if(spec.suspended){BufferedImage first=blank(186,186),second=blank(186,186);
            for(int i=0;i<2;i++){Graphics2D sg=graphics(i==0?first:second);sg.setColor(new Color(0xFF1400));sg.setStroke(new BasicStroke(24,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));sg.draw(new Line2D.Float(i==0?171:15,15,i==0?15:171,171));sg.dispose();}
            strikeFirst=pack(first);strikeSecond=pack(second);
        }else{strikeFirst=dot;strikeSecond=dot;}
        infoIcon=noticeIcon(PanamBulletins.INFO,spec.line,spec.color);
        rows = new Sprite[spec.mixed ? spec.count : 0];
        Map<String,Sprite> rowCache=new HashMap<>();
        for (int i=0;i<rows.length;i++) {
            rows[i]=rowCache.computeIfAbsent(spec.destinations.get(i),this::departureRow);
        }
        leavingRows=new Sprite[spec.previousDestinations.stream().distinct().count()>1?spec.previousDestinations.size():0];
        for(int i=0;i<leavingRows.length;i++)leavingRows[i]=rowCache.computeIfAbsent(spec.previousDestinations.get(i),this::departureRow);
        notices = new Sprite[spec.notices.size()];
        noticeIcons = new Sprite[spec.notices.size()];
        for (int i=0;i<notices.length;i++) {
            int nw=layout==null?471:Math.max(1,(int)layout.newsTextWidth()),nh=layout==null?191:Math.max(1,(int)Math.min(layout.newsTextHeight(),layout.font*1.13f*12));
            BufferedImage notice = blank(nw,nh); Graphics2D g = graphics(notice);
            String message = spec.notices.get(i);
            float fs=layout==null?32.768f:layout.font;
            wrapped(g,PanamBulletins.strip(message),0,0,fs,regular,0,nw,Math.max(1,(int)(nh/(fs*1.13f)))); g.dispose(); notices[i]=pack(notice);
            int kind=PanamBulletins.markedType(message);
            PanamNotice source=spec.noticeSources.get(i);
            noticeIcons[i]=kind==PanamBulletins.INFO&&source.line.equals(spec.line)&&source.color==spec.color
                    ?infoIcon:noticeIcon(kind,source.line,source.color);
        }

        int height=512; while(height<sy+shelf)height*=2;
        atlasWidth=canvas.getWidth();atlasHeight=height;
        // Keep logical atlas coordinates for UVs; raster density is independent.
        double density=layout==null?1:Math.min(spec.viewport.resolutionWidth/(double)layout.width,spec.viewport.resolutionHeight/(double)layout.height);
        density=Math.min(density,Math.min(4096.0/atlasWidth,4096.0/atlasHeight));
        density=Math.min(density,Math.sqrt(4194304.0/(atlasWidth*atlasHeight)));
        rasterWidth=Math.max(1,(int)Math.round(atlasWidth*density));rasterHeight=Math.max(1,(int)Math.round(atlasHeight*density));
        image = new BufferedImage(rasterWidth,rasterHeight,BufferedImage.TYPE_INT_ARGB);
        Graphics2D out=image.createGraphics();out.setComposite(AlphaComposite.Src);out.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        out.scale(rasterWidth/(double)atlasWidth,rasterHeight/(double)atlasHeight);out.drawImage(canvas,0,0,null);out.dispose();canvas.flush();canvas=null;
    }
    /** Dedicated bolder numerals, independently antialiased and tightly measured.
     * The time sprites are not stretched from small Minecraft font glyphs. */
    private Sprite panamDigit(int number) {
        Font font=bold.deriveFont(Font.BOLD,194f);
        BufferedImage image=blank(232,232);
        Graphics2D g=graphics(image);
        java.awt.font.GlyphVector gv=font.createGlyphVector(g.getFontRenderContext(),Integer.toString(number));
        Shape shape=gv.getOutline();
        java.awt.geom.Rectangle2D bounds=shape.getBounds2D();
        g.translate(10-bounds.getX(),10-bounds.getY());
        g.setColor(new Color(YELLOW));g.fill(shape);
        g.setStroke(new BasicStroke(2.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);
        g.dispose();
        int w=Math.min(232,(int)Math.ceil(bounds.getWidth())+23);
        int h=Math.min(232,(int)Math.ceil(bounds.getHeight())+23);
        digitAdvance[number]=w-5;
        Sprite result=pack(image.getSubimage(0,0,w,h));
        image.flush();
        return result;
    }
    private Sprite departureRow(String destination){
        BufferedImage row=blank(496,76);Graphics2D g=graphics(row);g.setColor(Color.WHITE);g.fillRoundRect(0,0,496,76,5,5);
        text(g,destination,8,7,51.2f,bold,NAVY,391);g.setColor(Color.BLACK);g.fillRoundRect(410,4,80,68,3,3);g.dispose();return pack(row);
    }
    private Sprite glyph(String value, Font font, float size, int color, float[] advances, int index) {
        Graphics2D measure=graphics(canvas);measure.setFont(font.deriveFont(size));advances[index]=(float)measure.getFontMetrics().getStringBounds(value,measure).getWidth();measure.dispose();
        BufferedImage img = blank((int)Math.ceil(advances[index])+8, (int)(size*1.4)); Graphics2D g=graphics(img);
        text(g,value,4,0,size,font,color,img.getWidth()-8); g.dispose();
        int left=img.getWidth(),top=img.getHeight(),right=0,bottom=0;
        for(int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++)if((img.getRGB(x,y)>>>24)!=0){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        left=Math.max(0,left-1);top=Math.max(0,top-1);right=Math.min(img.getWidth()-1,right+1);bottom=Math.min(img.getHeight()-1,bottom+1);
        Sprite packed=pack(img.getSubimage(left,top,right-left+1,bottom-top+1));img.flush();
        return new Sprite(packed.x,packed.y,packed.w,packed.h,left,top);
    }
    private Sprite pack(BufferedImage img) {
        int w=img.getWidth(),h=img.getHeight();
        // A 32-logical-pixel gutter still covers two texels at the lowest density.
        int padding=layout==null?2:32;
        if(sx+w+padding>canvas.getWidth()){sx=0;sy+=shelf+padding;shelf=0;}
        if(sy+h+padding>canvas.getHeight())throw new IllegalArgumentException("PANAM atlas overflow");
        Graphics2D g=canvas.createGraphics();g.setComposite(AlphaComposite.Src);g.drawImage(img,sx,sy,null);g.dispose();
        Sprite sprite=new Sprite(sx,sy,w,h);sx+=w+padding;shelf=Math.max(shelf,h);img.flush();return sprite;
    }
    private BufferedImage drawBase() {
        BufferedImage img=blank(WIDTH,HEIGHT);Graphics2D g=graphics(img);g.setColor(Color.BLACK);g.fillRect(0,0,WIDTH,HEIGHT);
        if(spec.suspended){suspended(g);g.dispose();return img;}
        g.setColor(Color.WHITE);g.fillRect(3,3,WIDTH-6,(int)HEADER-3); badges(g,20.48f,14f,95f);
        text(g,spec.destination,245,26,62f,bold,NAVY,626);
        g.setColor(new Color(spec.color));g.fillRect(0,HEIGHT-(int)FOOTER,WIDTH,(int)FOOTER);
        g.setColor(Color.BLACK);g.fillRoundRect(880,0,129,77,8,8);
        if(spec.count==0){g.setColor(new Color(NAVY));g.fillRect(24,197,458,68);text(g,"Temps d’attente indisponibles.",35,211,25,regular,WHITE,430);}
        else if(!spec.mixed){
            float labelY=HEADER+38;
            if(!spec.last){
                ordinal(g,"1","er",20.48f,labelY);
                ordinal(g,"2","e",276.48f,labelY);
                g.setColor(Color.WHITE);g.fillRoundRect(254,(int)(labelY+64),6,142,6,6);
                if(spec.count==1){
                    // MTR returned a single departure: show no invented ETA.
                    g.setColor(new Color(YELLOW));
                    g.fillRoundRect(347,303,76,12,6,6);
                }
            }
            if(spec.last){g.setColor(Color.WHITE);g.fill(new Polygon(new int[]{269,300,300},new int[]{279,261,297},3));wrapped(g,"Dernier\nmétro",307,247,43,bold,WHITE,197,2);}
        }
        // A local MTR information panel, including the honest missing-source state.
        g.setColor(Color.WHITE);g.fillRoundRect(512,(int)HEADER+10,512,(int)BODY-15,5,5);
        g.setColor(new Color(0xF7F7F7));g.fillRect(512,(int)HEADER+10,512,59);g.setColor(Color.WHITE);g.fillRect(512,(int)HEADER+10,156,59);
        g.dispose();return img;
    }
    private BufferedImage drawAdaptiveBase(){
        PanamLayout l=layout;
        BufferedImage img=blank(l.width,l.height);Graphics2D g=graphics(img);
        g.setColor(Color.BLACK);g.fillRect(0,0,l.width,l.height);
        g.setColor(new Color(spec.color));g.fillRect(0,(int)(l.height-l.footer),l.width,(int)Math.ceil(l.footer));
        if(l.header>0){
            g.setColor(Color.WHITE);g.fillRect(0,0,l.width,(int)l.header);
            float size=Math.min(82,l.header-l.pad*2),badgeWidth=size*2+3;
            badges(g,l.pad,l.pad,size);
            float clockReserve=Math.min(140,l.width*.24f);
            text(g,spec.destination,l.pad*2+badgeWidth,l.pad,Math.min(60,size*.76f),bold,NAVY,Math.max(1,l.width-badgeWidth-l.pad*4-clockReserve));
        }
        float clockScale=Math.min(1,Math.min(l.width/400f,l.height/260f));
        if(!l.compact){g.setColor(Color.BLACK);g.fillRoundRect((int)(l.width-l.pad-137*clockScale),0,(int)(137*clockScale+l.pad),(int)(64*clockScale+l.pad),5,5);}
        if(spec.suspended){
            wrapped(g,"Interruption temporaire\nde circulation",l.pad,l.bodyY+l.pad+50*clockScale,
                Math.max(8,Math.min(52,Math.min(l.width/14f,l.bodyH/5))),bold,YELLOW,Math.max(1,(int)(l.width-2*l.pad)),3);
            g.dispose();return img;
        }
        float top=l.bodyY+l.pad+(l.header==0&&!l.compact?48*clockScale:0);
        if(spec.count==0)wrapped(g,"Temps d’attente indisponibles.",l.pad,top,Math.max(8,Math.min(25,l.departW/14)),regular,YELLOW,Math.max(1,(int)(l.departW-2*l.pad)),3);
        else if(!spec.mixed&&!l.compact){
            int count=Math.min(2,spec.count);
            for(int n=0;n<count;n++)text(g,(n==0?"1er":"2e")+" métro",l.pad+n*l.departW/count,top,Math.min(22,l.departW/count/7),regular,WHITE,l.departW/count-l.pad*2);
            if(count==2){g.setColor(Color.WHITE);g.fillRoundRect((int)(l.departW/2-2),(int)(top+40),4,Math.max(1,(int)(l.departH-(top-l.bodyY)-60)),4,4);}
            if(spec.last)text(g,"Dernier métro",l.pad,l.bodyY+l.departH-30,20,bold,WHITE,l.departW-l.pad*2);
        }
        if(!l.compact){g.setColor(Color.WHITE);g.fillRect((int)l.newsX,(int)l.newsY,(int)Math.ceil(l.newsW),(int)Math.ceil(l.newsH));}
        g.dispose();return img;
    }
    private Sprite noticeIcon(int kind,String line,int color){
        BufferedImage indicator=blank(154,59);Graphics2D g=graphics(indicator);
        badges(g,0,0,49.15f,line,color);
        if(kind==PanamBulletins.INFO){
            g.setColor(new Color(0x9EA9CF));g.setStroke(new BasicStroke(1.5f));g.drawRoundRect(111,15,30,30,4,4);
            text(g,"i",122,17,20,regular,0x9EA9CF,20);
        }else{
            int fill=kind==PanamBulletins.WORKS?0xF5A623:kind==PanamBulletins.DANGER?0xE44632:0xC92130;
            g.setColor(new Color(fill));g.fillRoundRect(106,15,31,31,6,6);g.setColor(Color.WHITE);
            if(kind==PanamBulletins.WORKS){
                g.setStroke(new BasicStroke(3.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Float(116,23,128,36));g.draw(new Line2D.Float(128,23,116,36));
            }else if(kind==PanamBulletins.DANGER){
                g.setStroke(new BasicStroke(3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Float(122,21,122,32));g.fillOval(120,36,4,4);
            }else{
                g.setStroke(new BasicStroke(3.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Float(115,24,129,38));g.draw(new Line2D.Float(129,24,115,38));
            }
        }
        g.dispose();return pack(indicator);
    }
    private void badges(Graphics2D g,float x,float y,float size){badges(g,x,y,size,spec.line,spec.color);}
    private void badges(Graphics2D g,float x,float y,float size,String line,int color) {
        g.setColor(new Color(0x25313B));g.fill(new Ellipse2D.Float(x,y,size,size));g.setColor(Color.WHITE);g.fill(new Ellipse2D.Float(x+size*.06f,y+size*.06f,size*.88f,size*.88f));
        g.setColor(new Color(0x25313B));g.setStroke(new BasicStroke(size*.085f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        Path2D.Float m=new Path2D.Float();m.moveTo(x+size*.29,y+size*.71);m.lineTo(x+size*.29,y+size*.27);m.lineTo(x+size*.5,y+size*.56);m.lineTo(x+size*.71,y+size*.27);m.lineTo(x+size*.71,y+size*.71);g.draw(m);
        if(line.isEmpty())return;
        float bx=x+size+3;g.setColor(new Color(color));g.fill(new Ellipse2D.Float(bx,y,size,size));
        int foreground=luminance(color)>.6 ? 0 : WHITE;
        g.setFont(bold.deriveFont(size*.64f));FontMetrics fm=g.getFontMetrics();float tw=fm.stringWidth(line);
        Font glyphFont = bold.deriveFont(size*.67f);
        java.awt.font.GlyphVector gv = glyphFont.createGlyphVector(g.getFontRenderContext(), line);
        java.awt.geom.Rectangle2D visual = gv.getVisualBounds();
        if(visual.getWidth()>size*.86f){
            glyphFont=glyphFont.deriveFont((float)(glyphFont.getSize2D()*size*.86f/visual.getWidth()));
            gv=glyphFont.createGlyphVector(g.getFontRenderContext(),line);visual=gv.getVisualBounds();
        }
        g.setColor(new Color(foreground));
        g.drawGlyphVector(gv, bx + size/2f - (float)visual.getCenterX(),
                y + size/2f - (float)visual.getCenterY());
    }
    private void ordinal(Graphics2D g,String number,String suffix,float x,float y) {
        text(g,number,x,y,20.48f,regular,WHITE,30);text(g,suffix,x+12,y-3,14,regular,WHITE,20);text(g,"métro",x+34,y,20.48f,regular,WHITE,130);
    }
    private void suspended(Graphics2D g) {
        wrapped(g,"Interruption temporaire\nde circulation",41,118,51.2f,bold,YELLOW,650,2);
        String[] langs={"Service\nsuspended","Hier kein\nZugverkehr","Servicio\nsuspendido","Servizio\nsospeso"};
        for(int i=0;i<4;i++)wrapped(g,langs[i],41+i*151,260,20.48f,regular.deriveFont(Font.ITALIC),YELLOW,145,2);
        // Original vector interpretation, drawn only following an explicit operator status.
        g.setColor(new Color(YELLOW));g.fillRoundRect(789,190,153,251,65,65);g.fillRect(797,417,137,49);
        g.setColor(Color.BLACK);g.fillRoundRect(807,228,116,91,15,15);g.fillOval(810,347,21,21);g.fillOval(900,347,21,21);g.fillOval(838,372,12,12);g.fillOval(882,372,12,12);
    }
    private static double luminance(int rgb){return .2126*((rgb>>16)&255)/255+.7152*((rgb>>8)&255)/255+.0722*(rgb&255)/255;}
    public static BufferedImage blank(int w,int h){return new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);}
    public static Graphics2D graphics(BufferedImage img){Graphics2D g=img.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);return g;}
    private static void text(Graphics2D g,String value,float x,float y,float size,Font font,int color,float max) {
        g.setFont(font.deriveFont(size));FontMetrics fm=g.getFontMetrics();float w=(float)fm.getStringBounds(value,g).getWidth();
        AffineTransform old=g.getTransform();g.translate(x,y);g.scale(w>max?max/w:1,1);g.setColor(new Color(color));
        g.drawString(value,0,fm.getAscent());g.setTransform(old);
    }
    private static void wrapped(Graphics2D g,String text,float x,float y,float size,Font font,int color,int width,int maxLines) {
        g.setFont(font.deriveFont(size));FontMetrics fm=g.getFontMetrics();int row=0;
        for(String paragraph:text.split("\n")){
            String line="";
            for(String word:paragraph.split("\\s+")){
                String next=line.isEmpty()?word:line+" "+word;
                if(!line.isEmpty()&&fm.stringWidth(next)>width){text(g,line,x,y+row*size*1.13f,size,font,color,width);if(++row>=maxLines)return;line=word;}else line=next;
            }
            if(!line.isEmpty()){text(g,line,x,y+row*size*1.13f,size,font,color,width);if(++row>=maxLines)return;}
        }
    }
}
