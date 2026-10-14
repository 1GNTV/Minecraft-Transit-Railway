package org.mtr.mod.data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Safe, versioned bulletin codec. Old single-string announcements remain readable. */
public final class PanamBulletins {
    public static final int INFO=0, WORKS=1, DANGER=2, STOP=3, LIMIT=12;
    private static final String PREFIX="PN2:";
    public static final class Bulletin {
        public final int type;
        public final String message;
        public Bulletin(int type,String message){
            this.type=Math.max(INFO,Math.min(STOP,type));
            String value=message==null?"":message.trim();
            this.message=value.length()>512?value.substring(0,512):value;
        }
    }
    private PanamBulletins(){}
    public static List<Bulletin> decode(String packed){
        ArrayList<Bulletin> result=new ArrayList<>();
        if(packed==null||packed.isEmpty())return result;
        if(!packed.startsWith(PREFIX)){result.add(new Bulletin(INFO,packed));return result;}
        for(String part:packed.substring(PREFIX.length()).split(";")){
            if(result.size()>=LIMIT)break;
            int sep=part.indexOf(':');if(sep<=0)continue;
            try{
                int type=Integer.parseInt(part.substring(0,sep));
                if(type<INFO||type>STOP)continue;
                String text=new String(Base64.getUrlDecoder().decode(part.substring(sep+1)),StandardCharsets.UTF_8);
                Bulletin bulletin=new Bulletin(type,text);
                if(!bulletin.message.isEmpty())result.add(bulletin);
            }catch(IllegalArgumentException ignored){}
        }
        return result;
    }
    public static String encode(List<Bulletin> list){
        StringBuilder result=new StringBuilder(PREFIX);
        int count=0;
        for(Bulletin b:list){
            if(b.message.isEmpty())continue;
            if(count++>=LIMIT)break;
            if(count>1)result.append(';');
            result.append(b.type).append(':').append(Base64.getUrlEncoder().withoutPadding().encodeToString(b.message.getBytes(StandardCharsets.UTF_8)));
        }
        return count==0?"":result.toString();
    }
    public static String marked(Bulletin b){return "\u0001PN"+b.type+":"+b.message;}
    public static int markedType(String text){
        return text!=null&&text.length()>5&&text.startsWith("\u0001PN")&&text.charAt(4)>='0'&&text.charAt(4)<='3'&&text.charAt(5)==':'?text.charAt(4)-'0':INFO;
    }
    public static String strip(String text){return text!=null&&text.length()>5&&text.startsWith("\u0001PN")&&text.charAt(5)==':'?text.substring(6):text;}
}
