import org.mtr.mod.data.*;
import org.mtr.mod.client.*;
import java.util.*;
import java.util.List;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Photographic comparison fixtures ONLY. None of these names, routes or notices is in production templates. */
public final class SignageReferenceReview {
    private static SillonSignageSpec base(String style){return SillonSignagePresets.forStyle(style).manual();}
    private static SillonSignageSpec content(SillonSignageSpec s,String title,String subtitle,String symbols,List<SillonSignageRow> rows){return s.materialize(title,subtitle,symbols,s.accent(),rows,SillonSignagePlan.EMPTY);}
    private static SillonSignageSpec palette(SillonSignageSpec s,int bg,int fg,int accent){return new SillonSignageSpec(s.title(),s.subtitle(),s.body(),s.style(),s.icons(),s.mount(),s.arrow(),bg,fg,accent,s.width(),s.height(),s.yaw(),s.pitch(),s.roll(),s.stationAuto(),s.illuminated(),s.doubleSided(),s.options(),s.rows(),s.binding(),s.plan(),s.design());}
    private static String badge(String mode,int c,String name){return new SillonSignageLineBadge(mode,c,name).token();}
    private static SillonSignageRow row(String kind,String n,String text,String detail,String icons,int arrow){return new SillonSignageRow(kind,n,text,detail,icons,arrow);}
    public static void main(String[] args)throws Exception{
        Path out=Path.of("build/signage-qa/reference-review");Files.createDirectories(out);Map<String,SillonSignageSpec> samples=new LinkedHashMap<>();
        samples.put("01-plaque-monceau",content(base("metro"),"Monceau","","",List.of()));
        samples.put("02-plaque-rambuteau",content(base("metro_subtitle"),"Rambuteau","Centre Georges Pompidou","",List.of()));
        samples.put("03-plaque-deux-lignes",content(base("metro_subtitle").withSize(2.4f,.95f),"Saint-Denis|Porte de Paris","Stade de France","",List.of()));
        var forum=new SillonSignageDesign("ratp","forum","",true,true,false,1);
        samples.put("04-rer-forum",content(base("rer").withSize(4.5f,1).withOptions(base("rer").options().withBand(.35f)).withDesign(forum),"Châtelet|Les Halles","","",List.of()));
        String m14="ratp_metro,"+badge("metro",0x662D91,"14");String m1="ratp_metro,"+badge("metro",0xFFCD00,"1");
        samples.put("05-direction",content(base("direction").withArrow(0),"Aéroport d’Orly","",m14,List.of()));
        samples.put("06-correspondances",content(base("transfer").withSize(3.4f,.9f),"Correspondances","","",List.of(row("transport","","Bobigny – Pablo Picasso","","ratp_metro,"+badge("metro",0xF5A23F,"5"),0),row("transport","","Mairie de Montreuil","","ratp_metro,"+badge("metro",0xB6BD00,"9"),0))));
        List<SillonSignageRow> exits=List.of(row("exit","10","pl. Sainte-Opportune","","",-1),row("exit","11","r. de Rivoli","","",-1),row("exit","12","r. Bertin Poirée","","",-1),row("exit","13","r. des Lavandières","","",-1),row("exit","14","r. Saint-Denis","","",-1));
        String serving="ratp_metro,"+badge("metro",0xFFCD00,"1")+","+badge("metro",0x662D91,"14");
        samples.put("07-sorties",content(base("exit").withArrow(-1),"Sortie","",serving,exits));
        samples.put("08-secteur-rivoli",content(base("sector").withDesign(new SillonSignageDesign("ratp","rivoli","",true,true,false,1)),"Sortie","",serving,exits));
        samples.put("09-colonne-metro",content(base("vertical"),"","",m14,List.of()).withArrow(0));
        samples.put("10-totem-metro",content(base("totem"),"","",m1,List.of()).withArrow(0));
        samples.put("11-ascenseur",content(base("services"),"Ascenseur","","official_lift",List.of(row("service","","Ascenseur","","official_lift",0))));
        samples.put("12-panneau-compose",content(base("interchange"),"","","",List.of(row("transport","","Château de Vincennes","",m1,2),row("service","","Ascenseur","Lift · Ascensore","official_lift",1),row("exit","2","rue du Louvre","Exit · Salida","",1))));
        var notice=new SillonSignageDesign("ratp","none","",true,true,false,1,"Du 17 juin au 21 septembre, station Tuileries fermée complètement (jeux de Paris 2024)",false);
        samples.put("13-panam",content(base("digital").withDesign(notice),"Château de Vincennes","15:29",m1,List.of(row("transport","0","Château de Vincennes","Quai 1",badge("metro",0xFFCD00,"1"),-1),row("transport","3","Château de Vincennes","Quai 1",badge("metro",0xFFCD00,"1"),-1))));
        samples.put("14-nom-grand-paris",content(base("gpe"),"Villejuif – Gustave Roussy","","",List.of()));
        samples.put("15-direction-grand-paris",content(base("gpe_direction").withSize(3.5f,.7f).withArrow(7),"Saint-Denis|– Pleyel","",m14,List.of()));
        String[] names={"Saint-Denis – Pleyel","Mairie de Saint-Ouen","Saint-Ouen","Porte de Clichy","Pont Cardinet","Saint-Lazare","Madeleine","Pyramides","Châtelet","Gare de Lyon","Bercy","Cour Saint-Émilion","Bibliothèque François Mitterrand","Olympiades","Maison Blanche","Hôpital Bicêtre","Villejuif – Gustave Roussy","L’Haÿ-les-Roses","Chevilly-Larue","Thiais – Orly","Aéroport d’Orly"};
        Map<Integer,String> connections=Map.ofEntries(Map.entry(0,"ratp_rer,"+badge("rer",0x008B5B,"D")),Map.entry(1,"ratp_metro,"+badge("metro",0x98D5DA,"13")),Map.entry(2,"ratp_rer,"+badge("rer",0xFFCC30,"C")),Map.entry(3,"ratp_rer,"+badge("rer",0xFFCC30,"C")+",ratp_metro,"+badge("metro",0x98D5DA,"13")),Map.entry(4,"idfm_train_2,"+badge("train",0xA989AD,"L")),Map.entry(6,"ratp_metro,"+badge("metro",0xD282BE,"8")+","+badge("metro",0x00814F,"12")),Map.entry(7,"ratp_metro,"+badge("metro",0xFA9ABA,"7")),Map.entry(8,"ratp_rer,"+badge("rer",0xEE1C25,"A")+","+badge("rer",0x5291CE,"B")+","+badge("rer",0x008B5B,"D")+",ratp_metro,"+badge("metro",0xFFCD00,"1")+","+badge("metro",0xCF009E,"4")+","+badge("metro",0xFA9ABA,"7")+","+badge("metro",0x805A2A,"11")),Map.entry(9,"ratp_rer,"+badge("rer",0xEE1C25,"A")+","+badge("rer",0x008B5B,"D")+",ratp_metro,"+badge("metro",0xFFCD00,"1")),Map.entry(10,"ratp_metro,"+badge("metro",0x83C28D,"6")),Map.entry(12,"ratp_rer,"+badge("rer",0xFFCC30,"C")+",idfm_tram_2,"+badge("tram",0xF59242,"T3a")),Map.entry(14,"ratp_metro,"+badge("metro",0xFA9ABA,"7")),Map.entry(19,"ratp_rer,"+badge("rer",0xFFCC30,"C")));
        List<SillonSignagePlan.Stop> stops=new ArrayList<>();for(int i=0;i<names.length;i++)stops.add(new SillonSignagePlan.Stop(i+1,names[i],i==3?"Tribunal de Paris":i==18?"Marché International":"",connections.getOrDefault(i,""),i*100,i%4*50,true));
        for(String layout:List.of("horizontal","vertical")){
            var s=SillonSignagePresets.ALL.stream().map(p->p.document()).filter(p->p.style().equals("route")&&p.binding().layout().equals(layout)).findFirst().orElseThrow().manual();
            var ordered=new ArrayList<>(stops);if(layout.equals("vertical"))Collections.reverse(ordered);
            var plan=new SillonSignagePlan(List.of(new SillonSignagePlan.Line(14,"14","metro",0x662D91,ordered)),21,layout);
            samples.put(layout.equals("horizontal")?"16-plan-horizontal":"17-plan-vertical",s.materialize(layout.equals("horizontal")?"Aéroport d’Orly":"Saint-Denis|– Pleyel","",m14,0x662D91,List.of(),plan));
        }
        var platform=base("platform");var subset=stops.subList(14,21);samples.put("18-desserte-quai",platform.materialize("Aéroport d’Orly","",m14,0x662D91,List.of(),new SillonSignagePlan(List.of(new SillonSignagePlan.Line(14,"14","metro",0x662D91,subset)),15,"horizontal")));
        var custom=base("custom");samples.put("19-composition-libre",content(custom,"","","",List.of(row("transport","","Saint-Denis – Pleyel","",m14,2),row("service","","Escalator","","official_escalator",2),row("exit","4","rue Édouard-Vaillant","","",1))));
        var branch=List.of(new SillonSignagePlan.Stop(1,"Belvédère","","",0,0,true),new SillonSignagePlan.Stop(2,"Manufacture","","",100,80,true),new SillonSignagePlan.Stop(3,"Jardin des Arts","","",220,80,true),new SillonSignagePlan.Stop(4,"Port des Brumes","","",320,180,true));
        var cross=List.of(new SillonSignagePlan.Stop(5,"Université","","",80,240,true),branch.get(1),new SillonSignagePlan.Stop(6,"Les Ateliers","","",200,-80,true));
        samples.put("20-reseau-automatique",base("map").automatic().materialize("Plan du réseau","","",0,List.of(),new SillonSignagePlan(List.of(new SillonSignagePlan.Line(1,"X42","metro",0x15865A,branch),new SillonSignagePlan.Line(2,"R9","rer",0xC24C9A,cross)),2,"auto")));
        int cols=3,cw=700,ch=400,count=samples.size(),rows=(count+cols-1)/cols;var atlas=new BufferedImage(cols*cw,rows*ch,BufferedImage.TYPE_INT_RGB);var g=atlas.createGraphics();SillonSignagePainter.quality(g);g.setColor(new Color(0xE7EBF0));g.fillRect(0,0,atlas.getWidth(),atlas.getHeight());int index=0;
        StringBuilder commands=new StringBuilder();
        for(var e:samples.entrySet()){var image=SillonSignagePainter.render(e.getValue());ImageIO.write(image,"png",out.resolve(e.getKey()+".png").toFile());int x=index%cols*cw,y=index/cols*ch;g.setColor(Color.WHITE);g.fillRoundRect(x+8,y+8,cw-16,ch-16,12,12);SillonSignagePainter.text(g,e.getKey().substring(3).replace('-',' '),new Font("SansSerif",Font.BOLD,20),0x172138,x+20,y+16,cw-40,26,"left");SillonSignagePainter.image(g,image,x+24,y+58,cw-48,ch-80);var tag=new org.mtr.mapping.holder.CompoundTag();e.getValue().write(tag);commands.append("setblock ").append((index%5-2)*7).append(" ").append(5+index/5*4).append(" 0 mtr:sillon_signage[facing=south]").append(tag).append("\n");index++;image.flush();}
        Files.writeString(Path.of("build/signage-qa/reference-commands.txt"),commands.toString());
        g.dispose();ImageIO.write(atlas,"png",out.resolve("reference-atlas.png").toFile());System.out.println("PASS "+samples.size()+" source-matched photographic review renders");
    }
}
