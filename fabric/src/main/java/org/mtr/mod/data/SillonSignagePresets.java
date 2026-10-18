package org.mtr.mod.data;
import java.util.*;

/** Templates describe appearance only. Every template starts linked to the MTR world. */
public final class SillonSignagePresets {
    public record Preset(String name,String reference,SillonSignageSpec document){}
    private SillonSignagePresets(){}
    public static String label(String style){return switch(style){
        case "metro"->"Plaque métro";case "metro_subtitle"->"Plaque avec sous-titre";case "rer"->"Plaque RER";
        case "direction"->"Direction";case "transfer"->"Correspondances";case "interchange"->"Panneau composé";
        case "exit"->"Sorties numérotées";case "sector"->"Secteur / accès";case "vertical"->"Orientation verticale";
        case "platform"->"Desserte de quai";case "route"->"Plan de ligne";case "map"->"Plan du réseau MTR";
        case "services"->"Services";case "totem"->"Totem métro";case "digital"->"Départs en temps réel";
        case "gpe"->"Nom Grand Paris";case "gpe_direction"->"Direction Grand Paris";default->"Composition libre";};}
    private static Preset template(String name,String style,String mount,int arrow,int bg,int fg,int accent,float w,float h){
        var spec=new SillonSignageSpec("","","",style,"",mount,arrow,bg,fg,accent,w,h,0,0,0,true,"digital".equals(style),
            Set.of("ceiling","hanging","flag","standing").contains(mount));
        if(style.startsWith("gpe")){var o=spec.options();spec=spec.withOptions(new SillonSignageOptions("grand_paris",o.align(),o.textScale(),o.iconScale(),o.padding(),0,0,0,o.thickness(),o.suspension(),"none",o.frameColor(),"",o.bandRatio(),false));}
        return new Preset(name,"Références RATP / IDFM · contenu lié au réseau MTR",spec);
    }
    private static Preset diagram(String name,String style,String layout,String scope,float w,float h){
        var p=template(name,style,"wall",-1,0xFFFFFF,0x132047,0x666666,w,h);
        return new Preset(name,p.reference(),p.document().withBinding(new SillonSignageBinding(0,0,0,"auto",scope,layout,"","auto",true,true)));
    }
    public static final List<Preset> ALL=List.of(
        template("Plaque métro · classique","metro","wall",-1,0x202457,0xFFFFFF,0x80591B,2.05f,.57f),
        template("Plaque métro · large","metro","wall",-1,0x202457,0xFFFFFF,0x80591B,2.7f,.58f),
        template("Plaque métro · compacte","metro","wall",-1,0x202457,0xFFFFFF,0x80591B,2.3f,.52f),
        template("Plaque · bandeau fin","metro_subtitle","wall",-1,0x21275B,0xFFFFFF,0x51330C,2.2f,.60f),
        template("Plaque · deux niveaux","metro_subtitle","wall",-1,0x2A4078,0xFFFFFF,0x875E29,2.25f,.78f),
        template("Plaque métro · sombre","metro","wall",-1,0x080A18,0xFFFFFF,0x80591B,2.35f,.88f),
        template("Plaque RER","rer","wall",-1,0x101B54,0xFFFFFF,0x707347,3.75f,.83f),
        template("Direction · lignes et terminus","direction","ceiling",0,0xFFFFFF,0x132047,0x666666,3,.65f),
        template("Correspondances de la station","transfer","wall",0,0xFFFFFF,0x132047,0x996017,3,1.1f),
        template("Transport / services / sorties","interchange","hanging",0,0x101E5E,0xFFFFFF,0x009C94,3,1.4f),
        template("Liste des sorties MTR","exit","wall",1,0x101E5E,0xFFFFFF,0x836130,2.4f,2.5f),
        template("Accès et sorties du secteur","sector","wall",-1,0x101E5E,0xFFFFFF,0x6F6760,2.4f,3.6f),
        template("Colonne · mode et indice","vertical","standing",0,0xFFFFFF,0x101D56,0x666666,.65f,2.2f),
        template("Totem · mode et indice","totem","standing",0,0xFFFFFF,0x101D56,0x666666,.75f,2.5f),
        diagram("Quai · stations restantes","platform","horizontal","remaining",5.5f,1.05f),
        diagram("Plan de ligne · horizontal","route","horizontal","full",6.5f,1.1f),
        diagram("Plan du réseau en jeu","map","auto","full",4,3),
        template("Services et accès adaptés","services","flag",0,0x666971,0xFFFFFF,0x666971,1.8f,.6f),
        template("Départs du quai MTR","digital","hanging",-1,0x152543,0xFFFFFF,0x666666,2.4f,1.35f),
        template("Nom Grand Paris","gpe","wall",-1,0x000000,0xFFFFFF,0x666666,4.4f,.65f),
        template("Direction Grand Paris","gpe_direction","ceiling",0,0xFFFFFF,0x000000,0x666666,3,.65f),
        template("Composition liée au réseau","custom","wall",-1,0x101D56,0xFFFFFF,0x996017,3,1.5f),
        diagram("Plan de ligne · vertical","route","vertical","full",2.6f,4.2f)
    );
    public static Preset at(int index){return ALL.get(Math.floorMod(index,ALL.size()));}
    public static SillonSignageSpec forStyle(String style){return ALL.stream().filter(p->p.document().style().equals(style)).findFirst().orElse(ALL.get(0)).document();}
}
