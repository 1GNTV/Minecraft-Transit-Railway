package org.mtr.mod.client;

import org.mtr.mod.data.SillonSignageSpec;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reference-oriented RATP/IDFM layouts. Not an official reproduction or an
 * approved operator signage pack; physical measurements differ by generation.
 *
 * Source families:
 *  - RATP fiche equipements "Signalétique": white transport / blue exit /
 *    grey special services, see docs/SILLON-SIGNAGE-REFERENCES.md.
 *  - RATP "La typographie du métro": Parisine since 1996.
 *  - User-supplied real photographs of Metro plaques, exit totems and sectors.
 *
 * Layout coordinates below are fractions of the actual panel image, NOT
 * world-block coordinates. Unmeasured geometry is marked as an approximation.
 */
public final class SillonRATPSignageLayouts {
    static final int NAVY=0x101B54, BLUE=0x101E5E, WHITE=0xFDFDFB;
    static final int TEXT_DARK=0x132047, GREY=0x666971, OCHRE=0x836130;
    static final int SILVER=0xA7A7A4, FORUM_GREEN=0x707347;
    private SillonRATPSignageLayouts(){}

    public static boolean draw(Graphics2D g,SillonSignageSpec spec,int w,int h){
        switch(spec.style()){
            case "metro","metro_subtitle" -> station(g,spec,w,h);
            case "rer" -> rerStation(g,spec,w,h);
            case "direction","transfer" -> direction(g,spec,w,h);
            case "exit" -> exit(g,spec,w,h);
            case "sector" -> sector(g,spec,w,h);
            case "vertical" -> vertical(g,spec,w,h);
            case "platform","route" -> line(g,spec,w,h);
            case "map" -> plan(g,spec,w,h);
            case "services" -> services(g,spec,w,h);
            case "totem" -> totem(g,spec,w,h);
            default -> {return false;}
        }
        return true;
    }

    private static void panel(Graphics2D g,int w,int h,int bg,boolean rounded){
        g.setColor(new Color(bg));
        if(rounded)g.fill(new RoundRectangle2D.Float(1,1,w-2,h-2,Math.min(16,h*.045f),Math.min(16,h*.045f)));
        else g.fillRect(0,0,w,h);
        g.setColor(new Color(0x44000000,true));
        g.setStroke(new BasicStroke(Math.max(1.3f,h*.006f)));
        g.drawRect(2,2,w-5,h-5);
    }
    private static int px(int dim,float v){return Math.round(dim*v);}
    private static void textCenter(Graphics2D g,String t,boolean bold,float size,int color,float x,float y,float left,float right){
        if(t==null||t.isBlank())return;
        SillonSignageCanvas.center(g,t,SillonSignageCanvas.font(bold,size),
            color,x,y,left,right);
    }
    private static void textLeft(Graphics2D g,String t,boolean bold,float size,int color,float x,float y,float available){
        if(t==null||t.isBlank())return;
        SillonSignageCanvas.left(g,t,SillonSignageCanvas.font(bold,size),color,x,y,available);
    }
    /**
     * Station plaques are not corridor signs. Original metro blue/white:
     * distinct navy panel, white centered Parisine, subordinate ochre strip.
     */
    private static void station(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,NAVY,true);
        boolean subtitle="metro_subtitle".equals(s.style())&&!s.subtitle().isBlank();
        int band=subtitle?px(h,.178f):0;
        if(band>0){
            g.setColor(new Color(OCHRE));
            g.fillRect(3,h-band,w-6,band-3);
        }
        float usable=h-band;
        String title=s.title().replace('\n','|');
        String[] forced=title.split("\\|",3);
        List<String> lines=new ArrayList<>();
        if(forced.length>1){
            for(String part:forced)if(!part.isBlank()&&lines.size()<2)lines.add(part.strip());
        }else if(title.length()>18){
            int middle=title.length()/2,where=-1,best=999;
            for(int i=2;i<title.length()-2;i++)if(title.charAt(i)==' '&&Math.abs(i-middle)<best){
                best=Math.abs(i-middle);where=i;
            }
            if(where>0){lines.add(title.substring(0,where));lines.add(title.substring(where+1));}
        }
        if(lines.isEmpty())lines.add(title);
        if(lines.size()==1){
            textCenter(g,lines.get(0),true,Math.min(usable*.53f,w*.090f),WHITE,w*.5f,usable*.67f,w*.055f,w*.945f);
        }else{
            float size=Math.min(usable*.38f,w*.074f);
            textCenter(g,lines.get(0),true,size,WHITE,w*.5f,usable*.455f,w*.06f,w*.94f);
            textCenter(g,lines.get(1),true,size,WHITE,w*.5f,usable*.865f,w*.06f,w*.94f);
        }
        if(subtitle)textCenter(g,s.subtitle(),true,band*.66f,WHITE,w*.5f,h-band*.21f,w*.045f,w*.955f);
    }
    private static void rerStation(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,NAVY,false);
        float top=h*.19f;
        g.setColor(new Color(FORUM_GREEN));g.fillRect(0,0,px(w,.26f),h);
        SillonSignageCanvas.drawIcons(g,s.icons(),px(w,.025f),px(h,.06f),Math.max(24,px(h,.18f)),5,px(w,.25f));
        if(!s.subtitle().isBlank())
            textLeft(g,s.subtitle(),true,h*.095f,WHITE,w*.035f,h*.74f,w*.19f);
        textCenter(g,s.title().replace('|',' '),true,Math.min(h*.31f,w*.06f),
            WHITE,w*.63f,h*.57f,w*.29f,w*.97f);
    }
    /**
     * Corridor transfers are white with navy text. The 3-panel photograph
     * of Chatelet separates this from the navy "Sortie" family.
     */
    private static void direction(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,WHITE,false);
        int dark=TEXT_DARK,pad=px(w,.035f);
        int iconSize=Math.max(27,Math.min(px(h,.46f),px(w,.11f)));
        SillonSignageCanvas.drawIcons(g,s.icons(),pad,px(h,.10f),iconSize,
            Math.max(6,px(h,.025f)),px(w,.60f));
        textLeft(g,s.title(),true,Math.min(h*.31f,w*.065f),dark,
            pad,h*.78f,w*.74f-pad);
        if(!s.subtitle().isBlank())textLeft(g,s.subtitle(),false,h*.125f,dark,pad,h*.94f,w*.77f);
        SillonSignageCanvas.drawArrow(g,s.arrow(),w*.90f,h*.51f,Math.min(h*.34f,w*.055f),dark);
        g.setColor(new Color(0xFFE5E5E2,true));g.fillRect(0,h-3,w,3);
    }
    /**
     * Exit header and numbered destinations: not generic pipe-separated
     * body copy. Every row is parsed as a RATP exit number plus road name.
     */
    private static void exit(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,BLUE,false);
        int white=WHITE,pad=px(w,.045f);
        int stripe=px(h,.19f);
        g.setColor(new Color(WHITE));g.fillRect(0,0,w,stripe);
        SillonSignageCanvas.drawIcons(g,s.icons(),pad,Math.max(3,px(h,.022f)),
            Math.max(22,stripe-px(h,.04f)),8,px(w,.79f));
        textLeft(g,"Sortie",true,Math.min(h*.20f,w*.105f),white,pad,h*.38f,w*.76f);
        SillonSignageCanvas.drawArrow(g,s.arrow(),w*.895f,h*.32f,
            Math.min(h*.18f,w*.068f),white);
        String rows=s.body().isBlank()?s.subtitle():s.body();
        if(rows.isBlank()){
            textLeft(g,"Sorties à renseigner",false,h*.079f,white,pad,h*.64f,w*.85f);
            return;
        }
        String[] entries=rows.split("\\|");
        int n=Math.min(8,entries.length);
        float rowHeight=Math.min(h*.115f,h*.59f/Math.max(1,n)),top=h*.45f;
        int box=Math.max(21,Math.round(rowHeight*.78f));
        for(int i=0;i<n;i++){
            String item=entries[i].strip();if(item.isBlank())continue;
            int j=0;
            while(j<item.length()&&Character.isDigit(item.charAt(j)))j++;
            String index=item.substring(0,j);
            String name=item.substring(j).strip();
            if(index.isBlank()){name=item;index="";}
            float cy=top+rowHeight*i;
            if(!index.isBlank()){
                g.setColor(new Color(WHITE));
                g.setStroke(new BasicStroke(Math.max(2,h*.006f)));
                g.drawRect(pad,Math.round(cy)-box+5,box,box);
                textCenter(g,index,true,box*.58f,white,pad+box*.5f,cy, pad+2,pad+box-2);
            }
            textLeft(g,name,true,Math.min(rowHeight*.74f,w*.043f),white,
                pad+box+Math.max(10,px(w,.015f)),cy,w-pad*2-box);
        }
    }
    private static void sector(Graphics2D g,SillonSignageSpec s,int w,int h){
        int head=px(h,.34f);
        g.setColor(new Color(GREY));g.fillRect(0,0,w,head);
        g.setColor(new Color(WHITE));g.fillRect(0,head,w,px(h,.20f));
        g.setColor(new Color(BLUE));g.fillRect(0,head+px(h,.20f),w,h-head-px(h,.20f));
        String sector=s.subtitle().isBlank()?"secteur":s.subtitle();
        textCenter(g,sector,true,Math.min(head*.44f,w*.07f),WHITE,w*.5f,head*.66f,w*.05f,w*.95f);
        SillonSignageCanvas.drawIcons(g,s.icons(),px(w,.045f),head+px(h,.025f),
            Math.max(23,px(h,.145f)),5,px(w,.95f));
        textLeft(g,s.title().isBlank()?"Sortie":s.title(),true,
            Math.min(h*.20f,w*.105f),WHITE,px(w,.04f),h*.78f,w*.73f);
        SillonSignageCanvas.drawArrow(g,s.arrow(),w*.88f,h*.76f,
            Math.min(h*.13f,w*.06f),WHITE);
        if(!s.body().isBlank())textLeft(g,s.body().replace('|',' '),true,h*.08f,WHITE,
            px(w,.04f),h*.94f,w*.90f);
    }
    private static void vertical(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,WHITE,false);
        g.setColor(new Color(NAVY));g.fillRect(0,0,w,Math.max(4,px(h,.024f)));
        int size=Math.min(px(w,.69f),px(h,.21f));
        int x=(w-size)/2;
        SillonSignageCanvas.drawIcons(g,s.icons(),x,px(h,.10f),size,6,w);
        textCenter(g,s.title(),true,Math.min(h*.16f,w*.25f),TEXT_DARK,w*.5f,h*.58f,w*.075f,w*.925f);
        SillonSignageCanvas.drawArrow(g,s.arrow(),w*.5f,h*.82f,Math.min(w*.29f,h*.15f),NAVY);
    }
    private static void line(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,WHITE,false);
        SillonSignageCanvas.drawIcons(g,s.icons(),px(w,.04f),px(h,.09f),px(h,.19f),8,px(w,.45f));
        textLeft(g,s.title(),true,Math.min(h*.20f,w*.08f),TEXT_DARK,
            px(w,.05f),h*.47f,w*.87f);
        if(!s.body().isBlank()){
            // Manual station order is a named sequence, not invented stations.
            String[] stops=s.body().split("\\|");
            int n=Math.min(12,stops.length);
            int xl=px(w,.065f),xr=px(w,.93f),y=px(h,.72f);
            g.setColor(new Color(0xFF6F3D85,true));
            g.setStroke(new BasicStroke(Math.max(3,h*.018f)));
            g.drawLine(xl,y,xr,y);
            for(int i=0;i<n;i++){
                int sx=xl+(n==1?0:Math.round((xr-xl)*i/(float)(n-1)));
                g.setColor(new Color(WHITE));
                g.fillOval(sx-7,y-7,14,14);
                g.setColor(new Color(0xFF6F3D85,true));
                g.drawOval(sx-7,y-7,14,14);
                textCenter(g,stops[i].strip(),false,Math.max(11,h*.071f),TEXT_DARK,
                    sx,y+Math.max(20,h*.16f),Math.max(0,sx-w*.09f),Math.min(w,sx+w*.09f));
            }
        }else textLeft(g,"Sélectionner un quai MTR ou saisir les stations",
                false,h*.09f,TEXT_DARK,px(w,.05f),h*.77f,w*.9f);
    }
    private static void plan(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,WHITE,false);
        textLeft(g,s.title(),true,h*.17f,TEXT_DARK,px(w,.05f),h*.21f,w*.9f);
        g.setColor(new Color(NAVY));g.fillRect(px(w,.04f),px(h,.27f),px(w,.92f),Math.max(3,px(h,.012f)));
        // Deliberately do not invent corridor topology or place fictitious
        // "Entrée, billets, quais, sortie" dots on a made-up map.
        if(s.body().isBlank())
            textLeft(g,"Plan non renseigné",false,h*.12f,TEXT_DARK,px(w,.06f),h*.57f,w*.84f);
        else {
            String[] place=s.body().split("\\|");
            for(int i=0;i<Math.min(7,place.length);i++)
                textLeft(g,place[i].strip(),false,h*.10f,TEXT_DARK,px(w,.07f),
                    h*(.40f+i*.081f),w*.85f);
        }
    }
    private static void services(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,GREY,false);
        SillonSignageCanvas.drawIcons(g,s.icons(),px(w,.045f),px(h,.12f),
            Math.min(px(h,.42f),px(w,.23f)),8,px(w,.72f));
        textLeft(g,s.title(),true,Math.min(h*.22f,w*.085f),WHITE,
            px(w,.04f),h*.76f,w*.88f);
        if(!s.subtitle().isBlank())
            textLeft(g,s.subtitle(),false,h*.105f,WHITE,px(w,.04f),h*.94f,w*.87f);
    }
    private static void totem(Graphics2D g,SillonSignageSpec s,int w,int h){
        panel(g,w,h,WHITE,false);
        g.setColor(new Color(NAVY));
        g.setStroke(new BasicStroke(Math.max(7,w*.025f)));
        int circle=Math.min(px(w,.74f),px(h,.37f));
        int x=(w-circle)/2,y=px(h,.045f);
        g.drawOval(x,y,circle,circle);
        textCenter(g,"M",true,circle*.7f,NAVY,w*.5f,y+circle*.75f,x,x+circle);
        SillonSignageCanvas.drawIcons(g,s.icons(),px(w,.10f),px(h,.54f),
            Math.min(px(w,.34f),px(h,.15f)),8,px(w,.95f));
        SillonSignageCanvas.drawArrow(g,s.arrow(),w*.5f,h*.86f,
            Math.min(w*.28f,h*.13f),NAVY);
    }
}
