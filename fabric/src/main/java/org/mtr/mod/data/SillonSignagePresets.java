package org.mtr.mod.data;

import java.util.List;

/**
 * Editable reference cases transcribed from real photographed Paris signs.
 * The signs' captions and composition have a real source; dimensions and
 * colors are approximate photographic measurements, not blueprints.
 *
 * User-provided photographic references are enumerated in
 * docs/SILLON-SIGNAGE-REFERENCES.md.
 */
public final class SillonSignagePresets {
    public record Preset(String name, String reference, SillonSignageSpec document){}
    private SillonSignagePresets(){}

    private static SillonSignageSpec sign(String name,String note,String body,String style,
        String icons,String mount,int arrow,int background,int accent,
        float width,float height){
        return new SillonSignageSpec(name,note,body,style,icons,mount,arrow,
            background,0xFFFFFF,accent,width,height,0,0,0,
            false,false,false);
    }
    public static final List<Preset> ALL=List.of(
        new Preset("Monceau","Photo réelle — plaque murale bleue",
            sign("Monceau","","","metro","","wall",-1,0x202457,0x80591B,2.05f,.57f)),
        new Preset("Pigalle","Photo réelle — plaque simple",
            sign("Pigalle","","","metro","","wall",-1,0x202457,0x80591B,2.10f,.58f)),
        new Preset("Saint-Sulpice","Photo réelle — plaque émaillée",
            sign("Saint-Sulpice","","","metro","","wall",-1,0x202457,0x80591B,2.30f,.52f)),
        new Preset("Rambuteau — Centre Georges Pompidou",
            "Photo réelle — bandeau secondaire brun",
            sign("Rambuteau","Centre Georges Pompidou","","metro_subtitle",
                "","wall",-1,0x21275B,0x51330C,2.2f,.60f)),
        new Preset("Saint-Denis Porte de Paris",
            "Photo réelle — titre sur deux lignes, sous-titre Stade de France",
            sign("Saint-Denis|Porte de Paris","Stade de France","","metro_subtitle",
                "","wall",-1,0x2A4078,0x875E29,2.25f,.78f)),
        new Preset("Châtelet — Les Halles, secteur Forum",
            "Photo réelle — secteur Forum",
            sign("Châtelet|Les Halles","secteur Forum","","rer",
                "ratp_forum","wall",-1,0x101B54,0x707347,3.75f,.83f)),
        new Preset("Châtelet, secteur Rivoli — sorties 10 à 14",
            "Photo réelle — bandes secteurs + sorties numérotées",
            sign("Sortie","secteur Rivoli",
                "10 pl. Sainte-Opportune|11 r. de Rivoli|12 r. Bertin Poirée|13 r. des Lavandières|14 r. Saint-Denis",
                "sector","ratp_rivoli,idfm_m1,idfm_m14","wall",-1,0x101E5E,0x836130,2.4f,1.85f)),
        new Preset("Sortie — Châtelet / Rivoli",
            "Photo réelle — panneau bleu et correspondances",
            sign("Sortie","","10 pl. Sainte-Opportune|11 r. de Rivoli|12 r. Bertin Poirée|13 r. des Lavandières|14 r. Saint-Denis",
                "exit","idfm_m1,idfm_m4,idfm_m14","wall",1,0x101E5E,0x836130,2.7f,1.35f)),
        new Preset("Strasbourg–Saint-Denis",
            "Photo réelle — nom de station sur deux lignes",
            sign("Strasbourg|Saint-Denis","","","metro","","wall",-1,0x080A18,0x80591B,2.35f,.88f)),
        new Preset("Correspondances / Services / Sortie",
            "RATP — Fiches équipements, page Signalétique",
            sign("Correspondances","Services","1 r. Voltaire | 2 r. Anatole France",
                "interchange","idfm_m4,idfm_bus_1","wall",0,0x101E5E,0x009C94,2.60f,.95f)),
        new Preset("Colonne d'orientation métro",
            "RATP — Fiches équipements, page Signalétique",
            sign("","", "", "vertical", "idfm_m1", "standing", 0,
                0xFDFDFB,0x80591B,.65f,2.20f))
    );

    public static Preset at(int index){
        return ALL.get(Math.floorMod(index,ALL.size()));
    }
}
