package org.mtr.mod.data;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Persistent references to the network, never copies of generated names or stops. */
public record SillonSignageBinding(long stationId,long platformId,long routeId,String mode,String scope,
    String layout,String exitFilter,String service,boolean arrows,boolean lineColors,boolean guided) {
    public SillonSignageBinding(long stationId,long platformId,long routeId,String mode,String scope,String layout,String exitFilter,String service,boolean arrows,boolean lineColors){this(stationId,platformId,routeId,mode,scope,layout,exitFilter,service,arrows,lineColors,false);}
    public static final List<String> MODES=List.of("auto","metro","rer","train","tram","bus","boat","cable","air");
    public static final List<String> SCOPES=List.of("full","remaining");
    public static final List<String> LAYOUTS=List.of("auto","horizontal","vertical");
    public static final List<String> SERVICES=List.of("auto","lift","escalator","stairs","information");
    public static final SillonSignageBinding DEFAULT=new SillonSignageBinding(0,0,0,"auto","full","auto","","auto",true,true);
    public SillonSignageBinding {
        mode=MODES.contains(mode)?mode:"auto";scope=SCOPES.contains(scope)?scope:"full";
        layout=LAYOUTS.contains(layout)?layout:"auto";service=SERVICES.contains(service)?service:"auto";
        exitFilter=exitFilter==null?"":exitFilter.strip();if(exitFilter.length()>64)exitFilter=exitFilter.substring(0,64);
    }
    public String encode(){return stationId+";"+platformId+";"+routeId+";"+mode+";"+scope+";"+layout+";"+
        Base64.getUrlEncoder().withoutPadding().encodeToString(exitFilter.getBytes(StandardCharsets.UTF_8))+";"+service+";"+arrows+";"+lineColors+";"+guided;}
    public static SillonSignageBinding decode(String text){
        if(text==null||text.length()>1024)return DEFAULT;
        try{String[] a=text.split(";",-1);if(a.length!=10&&a.length!=11)return DEFAULT;
            return new SillonSignageBinding(Long.parseLong(a[0]),Long.parseLong(a[1]),Long.parseLong(a[2]),a[3],a[4],a[5],
                new String(Base64.getUrlDecoder().decode(a[6]),StandardCharsets.UTF_8),a[7],Boolean.parseBoolean(a[8]),Boolean.parseBoolean(a[9]),a.length==11&&Boolean.parseBoolean(a[10]));
        }catch(RuntimeException e){return DEFAULT;}
    }
}
