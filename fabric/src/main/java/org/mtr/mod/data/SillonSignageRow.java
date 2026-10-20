package org.mtr.mod.data;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** A real independent destination row: no pipe syntax is required by the editor. */
public record SillonSignageRow(String kind,String number,String text,String detail,String icons,int arrow) {
    public static final List<String> KINDS=List.of("transport","exit","service","sector","text");
    public static final int MAX_ROWS=32;
    public SillonSignageRow {
        kind=KINDS.contains(kind)?kind:"transport";
        number=limit(number,12);text=limit(text,128);detail=limit(detail,128);
        icons=normalizeIcons(icons);
        arrow=Math.max(-1,Math.min(7,arrow));
    }
    public static String normalizeIcons(String value) {
        if(value==null)return "";
        List<String> out=new ArrayList<>();int length=0;
        for(String id:value.toLowerCase(Locale.ROOT).split(",")) {
            id=id.strip();if(!id.matches("[a-z0-9_/-]{1,192}"))continue;
            if(out.size()==16||length+id.length()+1>1024)break;
            out.add(id);length+=id.length()+1;
        }
        return String.join(",",out);
    }
    private static String limit(String value,int max) {
        return value==null?"":value.substring(0,Math.min(max,value.length())).replace('\n',' ');
    }
    public static List<SillonSignageRow> bounded(List<SillonSignageRow> source) {
        if(source==null)return List.of();
        List<SillonSignageRow> out=new ArrayList<>();int budget=0;
        for(SillonSignageRow row:source) {
            if(row==null)continue;
            int bytes=20+3*(row.kind.length()+row.number.length()+row.text.length()+row.detail.length()+row.icons.length());
            if(out.size()==MAX_ROWS||budget+bytes>21000)break;
            budget+=bytes;out.add(row);
        }
        return List.copyOf(out);
    }
    public static String encode(List<SillonSignageRow> source) {
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DataOutputStream out=new DataOutputStream(bytes);
            List<SillonSignageRow> rows=bounded(source);out.writeByte(rows.size());
            for(SillonSignageRow row:rows) {
                out.writeUTF(row.kind);out.writeUTF(row.number);out.writeUTF(row.text);
                out.writeUTF(row.detail);out.writeUTF(row.icons);out.writeByte(row.arrow);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException ex){throw new IllegalStateException(ex);}
    }
    public static List<SillonSignageRow> decode(String text) {
        if(text==null||text.isBlank()||text.length()>30000)return List.of();
        try{
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text)));
            int size=in.readUnsignedByte();if(size>MAX_ROWS)return List.of();
            List<SillonSignageRow> rows=new ArrayList<>();
            for(int i=0;i<size;i++)rows.add(new SillonSignageRow(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readByte()));
            return in.available()==0?List.copyOf(rows):List.of();
        }catch(IOException|IllegalArgumentException ex){return List.of();}
    }
    public static List<SillonSignageRow> legacy(String body,String kind) {
        if(body==null||body.isBlank())return List.of();
        List<SillonSignageRow> rows=new ArrayList<>();
        for(String line:body.split("[|\\n]")) {
            if(line.isBlank())continue;
            String n="",t=line.strip();
            if("exit".equals(kind)) {
                int j=0;while(j<t.length()&&Character.isDigit(t.charAt(j)))j++;
                n=t.substring(0,j);t=t.substring(j).strip();
            }
            rows.add(new SillonSignageRow(kind,n,t,"","",-1));
            if(rows.size()==MAX_ROWS)break;
        }
        return List.copyOf(rows);
    }
}
