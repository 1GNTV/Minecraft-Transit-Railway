package org.mtr.mod.data;

import java.util.*;

/** One editable library for every shipped signage family; sources live in docs/signage. */
public final class SillonSignagePresets {
    public record Preset(String name,String reference,SillonSignageSpec document){}
    private SillonSignagePresets(){}
    public static String label(String style) {
        return switch(style) {
            case "metro"->"Plaque métro";case "metro_subtitle"->"Plaque avec sous-titre";case "rer"->"Plaque RER";
            case "direction"->"Direction";case "transfer"->"Correspondance";case "interchange"->"Panneau composé";
            case "exit"->"Sorties numérotées";case "sector"->"Secteur Châtelet";case "vertical"->"Orientation verticale";
            case "platform"->"Direction de quai";case "route"->"Plan de ligne";case "map"->"Plan / image";
            case "services"->"Services";case "totem"->"Totem métro";case "digital"->"Départs en temps réel";
            case "gpe"->"Nom Grand Paris";case "gpe_direction"->"Direction Grand Paris";default->"Composition libre";
        };
    }
    private static SillonSignageOptions options(String font,String frame,String image) {
        var d=SillonSignageOptions.DEFAULT;
        return new SillonSignageOptions(font,"auto",1,1,1,0,0,0,d.thickness(),d.suspension(),frame,d.frameColor(),image,.18f);
    }
    private static SillonSignageSpec sign(String title,String subtitle,String body,String style,String icons,String mount,int arrow,
        int bg,int fg,int accent,float w,float h) {
        return new SillonSignageSpec(title,subtitle,body,style,icons,mount,arrow,bg,fg,accent,w,h,0,0,0,false,false,Set.of("ceiling","hanging","flag","standing").contains(mount));
    }
    private static SillonSignageRow row(String kind,String number,String text,String detail,String icons,int arrow) {
        return new SillonSignageRow(kind,number,text,detail,icons,arrow);
    }
    private static Preset preset(String title,String ref,SillonSignageSpec spec){return new Preset(title,ref,spec);}
    private static final String RATP="RATP - typographie du métro et Fiches équipements, signalétique";
    private static final String HALL="Photos Châtelet-Les Halles / Girod Group, fabrication de la signalétique";
    private static final String GPE="AREP - signalétique du Grand Paris Express, Villejuif-Gustave Roussy";
    private static final String IDFM="IDFM - prescriptions signalétiques, polices et compositions";
    public static final List<Preset> ALL=List.of(
        preset("Monceau",RATP,sign("Monceau","","","metro","","wall",-1,0x202457,0xFFFFFF,0x80591B,2.05f,.57f)),
        preset("Pigalle",RATP,sign("Pigalle","","","metro","","wall",-1,0x202457,0xFFFFFF,0x80591B,2.10f,.58f)),
        preset("Saint-Sulpice",RATP,sign("Saint-Sulpice","","","metro","","wall",-1,0x202457,0xFFFFFF,0x80591B,2.30f,.52f)),
        preset("Rambuteau / Centre Georges Pompidou",RATP,sign("Rambuteau","Centre Georges Pompidou","","metro_subtitle","","wall",-1,0x21275B,0xFFFFFF,0x51330C,2.2f,.60f)),
        preset("Saint-Denis Porte de Paris",RATP,sign("Saint-Denis|Porte de Paris","Stade de France","","metro_subtitle","","wall",-1,0x2A4078,0xFFFFFF,0x875E29,2.25f,.78f)),
        preset("Strasbourg-Saint-Denis",RATP,sign("Strasbourg|Saint-Denis","","","metro","","wall",-1,0x080A18,0xFFFFFF,0x80591B,2.35f,.88f)),
        preset("Châtelet-Les Halles / Forum",HALL,sign("Châtelet|Les Halles","","","rer","ratp_forum,ratp_logo_white","wall",-1,0x101B54,0xFFFFFF,0x707347,3.75f,.83f).withOptions(SillonSignageOptions.DEFAULT.withBand(.28f))),
        preset("Direction métro 14",RATP,sign("Aéroport d’Orly","","","direction","ratp_metro,idfm_m14","ceiling",0,0xFFFFFF,0x132047,0x640082,3,.65f)),
        preset("Correspondances 5 et 9",RATP,sign("Correspondances","","","transfer","","wall",0,0xFFFFFF,0x132047,0x996017,3,1.1f).withRows(List.of(
            row("transport","","Bobigny - Pablo Picasso","Direction","idfm_m5",0),
            row("transport","","Mairie de Montreuil","Direction","idfm_m9",0)))),
        preset("Transport / Services / Sortie",RATP,sign("","","","interchange","","hanging",0,0x101E5E,0xFFFFFF,0x009C94,3,1.1f).withRows(List.of(
            row("transport","","Bobigny - Pablo Picasso","","ratp_metro,idfm_m5",0),
            row("service","","Information","Information · Información","ratp_assistance_audio",0),
            row("exit","1","boulevard Voltaire","","",0)))),
        preset("Sorties 10 à 14 / Rivoli",HALL,sign("Sortie","","10 pl. Sainte-Opportune|11 r. de Rivoli|12 r. Bertin Poirée|13 r. des Lavandières|14 r. Saint-Denis","exit","idfm_m1,idfm_m4,idfm_m14","wall",1,0x101E5E,0xFFFFFF,0x836130,2.7f,1.35f)),
        preset("Secteur Rivoli",HALL,sign("Sortie","secteur Rivoli","10 pl. Sainte-Opportune|11 r. de Rivoli|12 r. Bertin Poirée|13 r. des Lavandières|14 r. Saint-Denis","sector","ratp_rivoli,idfm_m1,idfm_m4,idfm_m14","wall",-1,0x101E5E,0xFFFFFF,0x836130,2.4f,1.85f).withOptions(SillonSignageOptions.DEFAULT.withBand(.34f))),
        preset("Colonne métro 1",RATP,sign("","","","vertical","idfm_m1","standing",0,0xFFFFFF,0x101D56,0xFFBE00,.65f,2.2f)),
        preset("Totem métro",RATP,sign("","","","totem","idfm_m14","standing",0,0xFFFFFF,0x101D56,0x640082,.75f,2.5f)),
        preset("Quai 14 / Orly",RATP,sign("Aéroport d’Orly","","Maison Blanche|Hôpital Bicêtre|Villejuif - Gustave Roussy|L’Haÿ-les-Roses|Chevilly-Larue|Thiais - Orly|Aéroport d’Orly",
            "platform","ratp_metro,idfm_m14","wall",1,0xFFFFFF,0x132047,0x640082,5,1)),
        preset("Ligne 14 / parcours complet","RATP - plan de la ligne 14, 2026",
            sign("Saint-Denis Pleyel / Aéroport d’Orly","","Saint-Denis Pleyel|Mairie de Saint-Ouen|Saint-Ouen|Porte de Clichy|Pont Cardinet|Saint-Lazare|Madeleine|Pyramides|Châtelet|Gare de Lyon|Bercy|Cour Saint-Émilion|Bibliothèque François Mitterrand|Olympiades|Maison Blanche|Hôpital Bicêtre|Villejuif - Gustave Roussy|L’Haÿ-les-Roses|Chevilly-Larue|Thiais - Orly|Aéroport d’Orly",
                "route","idfm_metro_2,idfm_m14","wall",-1,0xFFFFFF,0x132047,0x640082,3,2.2f)),
        preset("Plan régional / Île-de-France","IDFM - plan régional, édition 2026",
            sign("Transports en Île-de-France","","","map","","wall",-1,0xFFFFFF,0x132047,0x640082,3,2.3f)
                .withOptions(options("parisine","box","mtr:signage/maps/metro.png"))),
        preset("Ascenseur / accès adapté",RATP,sign("Ascenseur","Accès aux quais","","services","sncf_ascenseur,idfm_pmr","flag",0,0x666971,0xFFFFFF,0x666971,1.8f,.5f)),
        preset("Départs MTR",RATP+" / données de desserte MTR",sign("Prochains départs","","","digital","idfm_m14","hanging",-1,0x152543,0xFFFFFF,0x640082,2.4f,1.35f)),
        preset("Villejuif - Gustave Roussy",GPE,sign("Villejuif - Gustave Roussy","","","gpe","","wall",-1,0x000000,0xFFFFFF,0x640082,4.4f,.65f)
            .withOptions(options("grand_paris","none",""))),
        preset("Grand Paris / direction 14",GPE+" / "+IDFM,sign("Saint-Denis Pleyel","","","gpe_direction","idfm_metro_2,idfm_m14","ceiling",0,0xFFFFFF,0x000000,0x640082,3,.65f)
            .withOptions(options("grand_paris","none",""))),
        preset("Panneau personnalisé",IDFM,sign("Destination","Précision","","custom","","wall",-1,0x101D56,0xFFFFFF,0x996017,2,.6f))
    );
    public static Preset at(int index){return ALL.get(Math.floorMod(index,ALL.size()));}
    public static SillonSignageSpec forStyle(String style) {
        return ALL.stream().filter(p->p.document.style().equals(style)).findFirst().orElse(ALL.get(0)).document();
    }
}
