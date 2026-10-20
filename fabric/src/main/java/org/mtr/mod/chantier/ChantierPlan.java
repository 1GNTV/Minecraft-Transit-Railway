package org.mtr.mod.chantier;

import java.util.*;

/** Immutable world-space blueprint. No world writes or Minecraft dependencies. */
public record ChantierPlan(List<Part> parts,String wall,String floor,String roof,String platform,int spacing,boolean pillars) {
    public static final int MAX_CELLS=350000,MAX_PARTS=32;
    public enum Kind { STATION,TUNNEL,HALL,STAIRS }
    public enum Role { AIR,WALL,FLOOR,ROOF,PLATFORM,LIGHT,PILLAR,STAIR }
    public record Point(double x,double y,double z) {
        public Point {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29999000||Math.abs(z)>29999000||Math.abs(y)>2048)throw new IllegalArgumentException("Coordonnées invalides.");}
        public Point move(double x,double y,double z){return new Point(this.x+x,this.y+y,this.z+z);}
    }
    /** width is the clear inner width, base Y is the track plane; layout 0 lateral, 1 island. */
    public record Part(Kind kind,Point a,Point b,int width,int height,int profile,int layout) {
        public Part {
            Objects.requireNonNull(kind);Objects.requireNonNull(a);Objects.requireNonNull(b);
            double length=Math.hypot(b.x-a.x,b.z-a.z);
            if(length<2||length>192||width<3||width>64||height<3||height>32||profile<0||profile>2||layout<0||layout>1||Math.abs(b.y-a.y)>96)throw new IllegalArgumentException("Volume : longueur 2–192, largeur 3–64, hauteur 3–32.");
            if(kind==Kind.STATION&&(length<8||width<13||Math.abs(b.y-a.y)>.01))throw new IllegalArgumentException("Une station requiert au moins 13 m de largeur et un quai horizontal.");
            if(kind==Kind.STAIRS&&Math.abs(b.y-a.y)>length)throw new IllegalArgumentException("Escalier trop raide : allonge le tracé.");
        }
        public double length(){return Math.hypot(b.x-a.x,b.z-a.z);}
        public Part dimensions(int w,int h){return new Part(kind,a,b,w,h,profile,layout);}
        public Part moved(double x,double y,double z){return new Part(kind,a.move(x,y,z),b.move(x,y,z),width,height,profile,layout);}
        public Part end(Point point){return new Part(kind,a,point,width,height,profile,layout);}
        public double trackOffset(){return layout==0?2.5:width/2.0-3;}
    }
    public ChantierPlan {
        parts=List.copyOf(parts);if(parts.isEmpty()||parts.size()>MAX_PARTS)throw new IllegalArgumentException("Un chantier contient 1 à 32 volumes.");
        for(String material:List.of(wall,floor,roof,platform))if(!material.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||material.length()>128)throw new IllegalArgumentException("Identifiant de matériau invalide.");
        if(spacing<3||spacing>24)throw new IllegalArgumentException("Espacement 3 à 24 m.");
        Point anchor=parts.get(0).a;for(Part p:parts)for(Point v:List.of(p.a,p.b))if(Math.abs(v.x-anchor.x)>256||Math.abs(v.z-anchor.z)>256||Math.abs(v.y-anchor.y)>128)throw new IllegalArgumentException("Chantier limité à 256 m autour de son origine.");
    }
    public static ChantierPlan of(Part p){return new ChantierPlan(List.of(p),"minecraft:smooth_quartz","minecraft:smooth_stone","minecraft:white_concrete","mtr:platform",8,true);}
    public ChantierPlan withParts(List<Part> values){return new ChantierPlan(values,wall,floor,roof,platform,spacing,pillars);}
    public String encode(){StringBuilder s=new StringBuilder("1;").append(wall).append(';').append(floor).append(';').append(roof).append(';').append(platform).append(';').append(spacing).append(';').append(pillars);for(Part p:parts)s.append('\n').append(p.kind).append(';').append(p.a.x).append(';').append(p.a.y).append(';').append(p.a.z).append(';').append(p.b.x).append(';').append(p.b.y).append(';').append(p.b.z).append(';').append(p.width).append(';').append(p.height).append(';').append(p.profile).append(';').append(p.layout);return s.toString();}
    public static ChantierPlan decode(String raw){
        if(raw.length()>24000)throw new IllegalArgumentException("Plan trop volumineux.");String[] rows=raw.split("\\n",-1);if(rows.length<2||rows.length>MAX_PARTS+1)throw new IllegalArgumentException("Nombre de volumes invalide.");
        String[] h=rows[0].split(";",-1);if(h.length!=7||!h[0].equals("1")||!Set.of("true","false").contains(h[6]))throw new IllegalArgumentException("Format de plan invalide.");List<Part> parts=new ArrayList<>();for(int n=1;n<rows.length;n++){String[] p=rows[n].split(";",-1);if(p.length!=11)throw new IllegalArgumentException("Volume invalide.");parts.add(new Part(Kind.valueOf(p[0]),new Point(Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3])),new Point(Double.parseDouble(p[4]),Double.parseDouble(p[5]),Double.parseDouble(p[6])),Integer.parseInt(p[7]),Integer.parseInt(p[8]),Integer.parseInt(p[9]),Integer.parseInt(p[10])));}
        return new ChantierPlan(parts,h[1],h[2],h[3],h[4],Integer.parseInt(h[5]),Boolean.parseBoolean(h[6]));
    }
}
