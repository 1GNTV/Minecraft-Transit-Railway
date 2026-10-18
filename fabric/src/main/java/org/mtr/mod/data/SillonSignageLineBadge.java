package org.mtr.mod.data;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Safe inline symbol: its colour and label come from the user's line, including custom networks. */
public record SillonSignageLineBadge(String mode,int color,String label) {
    public String token(){String text=label==null?"":label;while(text.codePointCount(0,text.length())>24||text.getBytes(StandardCharsets.UTF_8).length>72)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));
        return "line_"+mode+"_"+String.format(Locale.ROOT,"%06x",color&0xFFFFFF)+"_"+HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));}
    public static SillonSignageLineBadge parse(String token){
        try{if(!token.startsWith("line_"))return null;String[] p=token.split("_",4);
            if(p.length!=4||!SillonSignageBinding.MODES.contains(p[1])||p[2].length()!=6||p[3].length()>144)return null;
            return new SillonSignageLineBadge(p[1],Integer.parseInt(p[2],16),new String(HexFormat.of().parseHex(p[3]),StandardCharsets.UTF_8));
        }catch(RuntimeException e){return null;}
    }
    public static String modeIcon(String mode){return switch(mode){case "rer"->"ratp_rer";case "tram"->"idfm_tram_2";case "bus"->"idfm_bus_2";case "train"->"idfm_train_2";case "cable"->"idfm_cable_2";case "boat"->"idfm_ferry_2";case "air"->"idfm_aeroport";default->"ratp_metro";};}
}
