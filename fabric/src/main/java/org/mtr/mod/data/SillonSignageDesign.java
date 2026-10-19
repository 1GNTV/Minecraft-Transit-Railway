package org.mtr.mod.data;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Explicit visual conventions. Network names and route data remain in their MTR bindings. */
public record SillonSignageDesign(String profile,String sector,String sectorName,boolean translations,boolean operator,boolean passed,float rowScale,String notice,boolean autoSize){
    public static final List<String> PROFILES=List.of("auto","ratp","idfm","gpe");
    public static final List<String> SECTORS=List.of("none","custom","forum","rivoli","seine");
    public static final SillonSignageDesign DEFAULT=new SillonSignageDesign("auto","none","",true,true,false,1,"",false);
    public SillonSignageDesign(String profile,String sector,String sectorName,boolean translations,boolean operator,boolean passed,float rowScale){this(profile,sector,sectorName,translations,operator,passed,rowScale,"",false);}
    public SillonSignageDesign{
        profile=PROFILES.contains(profile)?profile:"auto";sector=SECTORS.contains(sector)?sector:"none";
        sectorName=sectorName==null?"":sectorName.strip();if(sectorName.length()>64)sectorName=sectorName.substring(0,64);
        notice=notice==null?"":notice;if(notice.length()>512)notice=notice.substring(0,512);
        rowScale=SillonSignageOptions.clamp(rowScale,.65f,1.6f,1);
    }
    public String label(String fallback){return !sectorName.isBlank()?sectorName:switch(sector){case "forum"->"Forum";case "rivoli"->"Rivoli";case "seine"->"Seine";default->fallback;};}
    public String symbol(){return Set.of("forum","rivoli","seine").contains(sector)?"ratp_"+sector:"";}
    public String encode(){return profile+";"+sector+";"+Base64.getUrlEncoder().withoutPadding().encodeToString(sectorName.getBytes(StandardCharsets.UTF_8))+";"+translations+";"+operator+";"+passed+";"+rowScale+";"+Base64.getUrlEncoder().withoutPadding().encodeToString(notice.getBytes(StandardCharsets.UTF_8))+";"+autoSize;}
    public static SillonSignageDesign decode(String text){try{if(text==null||text.length()>4096)return DEFAULT;String[] a=text.split(";",-1);if(a.length!=7&&a.length!=9)return DEFAULT;return new SillonSignageDesign(a[0],a[1],new String(Base64.getUrlDecoder().decode(a[2]),StandardCharsets.UTF_8),Boolean.parseBoolean(a[3]),Boolean.parseBoolean(a[4]),Boolean.parseBoolean(a[5]),Float.parseFloat(a[6]),a.length==9?new String(Base64.getUrlDecoder().decode(a[7]),StandardCharsets.UTF_8):"",a.length==9&&Boolean.parseBoolean(a[8]));}catch(RuntimeException e){return DEFAULT;}}
}
