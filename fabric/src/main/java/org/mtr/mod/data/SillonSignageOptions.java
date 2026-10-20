package org.mtr.mod.data;

import java.util.List;

/** Independent typography, geometry and material settings shared by editor and renderer. */
public record SillonSignageOptions(String font, String align, float textScale, float iconScale,
    float padding, float offsetX, float offsetY, float offsetZ, float thickness,
    float suspension, String frame, int frameColor, String image, float bandRatio, boolean freePalette) {
    public static final List<String> FONTS=List.of("parisine","grand_paris","idf_voyageur");
    public static final List<String> FRAMES=List.of("none","thin","box");
    public static final List<String> ALIGNS=List.of("auto","left","center");
    public static final SillonSignageOptions DEFAULT=new SillonSignageOptions(
        "parisine","auto",1,1,1,0,0,0,.04f,.65f,"none",0x161616,"",.18f);
    public SillonSignageOptions(String font,String align,float textScale,float iconScale,float padding,
        float offsetX,float offsetY,float offsetZ,float thickness,float suspension,String frame,
        int frameColor,String image,float bandRatio) {
        this(font,align,textScale,iconScale,padding,offsetX,offsetY,offsetZ,thickness,suspension,frame,frameColor,image,bandRatio,false);
    }
    public SillonSignageOptions {
        font=FONTS.contains(font)?font:"parisine";
        align=ALIGNS.contains(align)?align:"auto";
        textScale=clamp(textScale,.5f,1.8f,1);iconScale=clamp(iconScale,.5f,1.6f,1);
        padding=clamp(padding,.25f,2,1);
        offsetX=clamp(offsetX,-12,12,0);offsetY=clamp(offsetY,-12,12,0);offsetZ=clamp(offsetZ,-4,4,0);
        thickness=clamp(thickness,.005f,.35f,.04f);suspension=clamp(suspension,.05f,6,.65f);
        frame=FRAMES.contains(frame)?frame:"thin";frameColor&=0xFFFFFF;
        image=image==null?"":image.strip().toLowerCase(java.util.Locale.ROOT);
        if(image.length()>192||!image.matches("[a-z0-9_.:/-]*")||image.contains(".."))image="";
        bandRatio=clamp(bandRatio,.1f,.4f,.18f);
    }
    public static float clamp(float v,float min,float max,float fallback) {
        return Float.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;
    }
    public SillonSignageOptions withBand(float ratio) {
        return new SillonSignageOptions(font,align,textScale,iconScale,padding,offsetX,offsetY,offsetZ,thickness,suspension,frame,frameColor,image,ratio,freePalette);
    }
    public SillonSignageOptions withFreePalette(boolean enabled) {
        return new SillonSignageOptions(font,align,textScale,iconScale,padding,offsetX,offsetY,offsetZ,
            thickness,suspension,frame,frameColor,image,bandRatio,enabled);
    }
    public String encode() {
        return String.join(";",font,align,""+textScale,""+iconScale,""+padding,""+offsetX,""+offsetY,
            ""+offsetZ,""+thickness,""+suspension,frame,""+frameColor,image,""+bandRatio,""+freePalette);
    }
    public static SillonSignageOptions decode(String encoded) {
        if(encoded==null||encoded.length()>2048)return DEFAULT;
        try{
            String[] p=encoded.split(";",-1);
            if(p.length!=14&&p.length!=15)return DEFAULT;
            return new SillonSignageOptions(p[0],p[1],Float.parseFloat(p[2]),Float.parseFloat(p[3]),
                Float.parseFloat(p[4]),Float.parseFloat(p[5]),Float.parseFloat(p[6]),Float.parseFloat(p[7]),
                Float.parseFloat(p[8]),Float.parseFloat(p[9]),p[10],Integer.parseInt(p[11]),p[12],Float.parseFloat(p[13]),p.length==15&&Boolean.parseBoolean(p[14]));
        }catch(RuntimeException ex){return DEFAULT;}
    }
}
