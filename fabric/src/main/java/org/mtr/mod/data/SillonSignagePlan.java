package org.mtr.mod.data;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Immutable render data. Generated from MTR and never written into a sign's saved document. */
public final class SillonSignagePlan {
    public record Stop(long stationId,String name,String detail,String icons,double x,double z,boolean positioned){}
    public record Line(long id,String label,String mode,int color,List<Stop> stops){public Line{stops=List.copyOf(stops);}}
    public static final SillonSignagePlan EMPTY=new SillonSignagePlan(List.of(),0,"auto");
    private final List<Line> lines;private final long currentStation;private final String layout,key;
    public SillonSignagePlan(List<Line> lines,long currentStation,String layout){
        this.lines=List.copyOf(lines);this.currentStation=currentStation;this.layout=layout;
        try{key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((this.lines+"/"+currentStation+"/"+layout).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public List<Line> lines(){return lines;}public long currentStation(){return currentStation;}
    public String layout(){return layout;}public String key(){return key;}
    @Override public boolean equals(Object o){return o instanceof SillonSignagePlan p&&key.equals(p.key);}
    @Override public int hashCode(){return key.hashCode();}
}
