package org.mtr.mod.decor;

/** Original modular station equipment; dimensions in Minecraft metres. */
public enum DecorKind {
    VAULT("vault","Voûte de station",6,3,2,"Béton|Carrelage|Métal|Pierre","Demi-cercle|Surbaissée|Anse large|Nervurée"),
    COLUMN("column","Habillage de pilier",1.2f,3,.8f,"Béton|Métal|Bois|Pierre","Rectangulaire|Rond|Nervuré|Socle"),
    RIBBED_WALL("ribbed_wall","Parement mural nervuré",3,2.5f,.16f,"Béton|Bois|Métal|Pierre","Vertical|Horizontal|Alterné|Cadre"),
    ACOUSTIC("acoustic","Panneau acoustique",2,1.2f,.10f,"Métal|Béton|Bois|Pierre","Perforé|Fentes|Rainures|Lisse"),
    BAFFLE("baffle","Plafond à lames",3,.25f,2,"Bois|Métal|Béton|Pierre","Longitudinal|Transversal|Alterné|Avec cadre"),
    CORNICE("cornice","Corniche et plinthe",3,.25f,.18f,"Pierre|Béton|Métal|Bois","Droit|Angle intérieur|Angle extérieur|Plinthe"),
    LIGHT_STRIP("light_strip","Bandeau lumineux moderne",3,.13f,.16f,"Métal|Pierre|Béton|Bois","Droit|Angle|Embout|Mural"),
    PENDANT("pendant","Luminaire suspendu",2.4f,.10f,.28f,"Métal|Bois|Béton|Pierre","Linéaire|Double|Ailettes|Cadre"),
    SCONCE("sconce","Applique murale",.22f,.75f,.14f,"Métal|Pierre|Bois|Béton","Verticale|Bandeau central|Double|Déflecteur"),
    BENCH("bench","Banc continu modulaire",2.4f,.82f,.57f,"Bois|Métal|Béton|Pierre","Dossier|Sans dossier|Double face|Accoudoirs"),
    LEANING("leaning","Appui debout",2,1,.22f,"Bois|Métal|Béton|Pierre","Lame|Tube|Double face|Repose-pieds"),
    BIN("bin","Poubelle de tri",1.05f,1,.38f,"Métal|Béton|Bois|Pierre","Trois flux|Deux flux|Un flux|Sac transparent"),
    HELP_POINT("help_point","Borne d’appel voyageurs",.36f,1.5f,.23f,"Métal|Pierre|Béton|Bois","Sur pied|Murale|Double bouton|Capot"),
    INTERCOM("intercom","Interphone de quai",.24f,.40f,.055f,"Métal|Pierre|Béton|Bois","Mural|Encastré|Capot|Double bouton"),
    FIRE_CABINET("fire_cabinet","Coffret extincteur",.48f,.88f,.23f,"Métal|Pierre|Béton|Bois","Vitré|Plein|Encastré|Double"),
    CABINET("cabinet","Armoire technique",1,1.9f,.42f,"Métal|Béton|Bois|Pierre","Deux portes|Une porte|Ventilée|Murale"),
    VENT("vent","Grille de ventilation",1.2f,.6f,.08f,"Métal|Béton|Bois|Pierre","Horizontale|Verticale|Maille|Encastrée"),
    HANDRAIL("handrail","Main courante",3,.9f,.055f,"Métal|Bois|Béton|Pierre","Droit|Montant|Descendant|Angle"),
    BALUSTRADE("balustrade","Garde-corps",3,1.1f,.06f,"Métal|Bois|Béton|Pierre","Vitré|Barreaudé|Câbles|Plein"),
    POSTER("poster","Cadre d’affiche",.85f,1.25f,.06f,"Métal|Bois|Pierre|Béton","Mural|Éclairé|Sur pied|Double face"),
    JOURNEY_KIOSK("journey_kiosk","Borne d’itinéraire",.8f,1.8f,.35f,"Métal|Bois|Béton|Pierre","Sur pied|Murale|Double face|Basse"),
    WAYPOINT("waypoint","Point de cheminement au brush",.3f,.2f,.3f,"Métal|Bois|Béton|Pierre","Passage|Palier|Escalier|Ascenseur");
    public final String id,title,materials,variants;
    public final float width,height,depth;
    DecorKind(String id,String title,float width,float height,float depth,String materials,String variants){this.id=id;this.title=title;this.width=width;this.height=height;this.depth=depth;this.materials=materials;this.variants=variants;}
    public static DecorKind of(String id){for(var k:values())if(k.id.equals(id))return k;return BENCH;}
    public boolean light(){return this==LIGHT_STRIP||this==PENDANT||this==SCONCE;}
}
