package org.mtr.mod.decor;

import org.mtr.mapping.holder.CompoundTag;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Versioned immutable document shared by preview, world geometry and editing packets. */
public record DecorSpec(DecorKind kind,float width,float height,float depth,float spacing,float suspension,
                        float yaw,float pitch,float x,float y,float z,int color,int accent,int material,int variant,
                        boolean illuminated,String image,String label) {
    public DecorSpec {
        kind=kind==null?DecorKind.BENCH:kind;
        width=clamp(width,.25f,16);height=clamp(height,.1f,12);depth=clamp(depth,.035f,8);
        spacing=clamp(spacing,.08f,2);suspension=clamp(suspension,0,8);
        yaw=clamp(yaw,-180,180);pitch=clamp(pitch,-60,60);
        x=clamp(x,-8,8);y=clamp(y,-8,8);z=clamp(z,-8,8);
        color&=0xFFFFFF;accent&=0xFFFFFF;material=Math.max(0,Math.min(3,material));variant=Math.max(0,Math.min(3,variant));
        image=image==null?"":image.substring(0,Math.min(160,image.length()));
        if(!image.isEmpty()&&!image.matches("[a-z0-9_.-]+:[a-z0-9/_.-]+\\.png"))image="";
        label=label==null?"":label.substring(0,Math.min(48,label.length()));
    }
    public static float clamp(float f,float a,float b){return Float.isFinite(f)?Math.max(a,Math.min(b,f)):a;}
    public static DecorSpec defaults(DecorKind k){return new DecorSpec(k,k.width,k.height,k.depth,.16f,.65f,0,0,0,0,0,
        k==DecorKind.BENCH||k==DecorKind.LEANING?0xB69161:k==DecorKind.FIRE_CABINET?0xBE3037:0xD3D1C9,0x177AA5,0,0,k.light(),"","");}
    public String encode(){return String.join(";",kind.id,""+width,""+height,""+depth,""+spacing,""+suspension,""+yaw,""+pitch,""+x,""+y,""+z,""+color,""+accent,""+material,""+variant,""+illuminated,
        Base64.getUrlEncoder().withoutPadding().encodeToString(image.getBytes(StandardCharsets.UTF_8)),Base64.getUrlEncoder().withoutPadding().encodeToString(label.getBytes(StandardCharsets.UTF_8)));}
    public static DecorSpec decode(String value){try{
        if(value.length()>2048)return defaults(DecorKind.BENCH);var a=value.split(";",-1);if(a.length!=18)return defaults(DecorKind.BENCH);
        return new DecorSpec(DecorKind.of(a[0]),Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3]),Float.parseFloat(a[4]),Float.parseFloat(a[5]),Float.parseFloat(a[6]),Float.parseFloat(a[7]),Float.parseFloat(a[8]),Float.parseFloat(a[9]),Float.parseFloat(a[10]),Integer.parseInt(a[11]),Integer.parseInt(a[12]),Integer.parseInt(a[13]),Integer.parseInt(a[14]),Boolean.parseBoolean(a[15]),new String(Base64.getUrlDecoder().decode(a[16]),StandardCharsets.UTF_8),new String(Base64.getUrlDecoder().decode(a[17]),StandardCharsets.UTF_8));
    }catch(RuntimeException ex){return defaults(DecorKind.BENCH);}}
    public void write(CompoundTag tag){tag.putInt("sillon_decor_version",1);tag.putString("sillon_decor",encode());}
    public static DecorSpec read(CompoundTag tag,DecorKind fallback){return tag.contains("sillon_decor")?decode(tag.getString("sillon_decor")):defaults(fallback);}
    /** Explicit patch mask: unselected properties and object families remain intact. */
    public DecorSpec patch(DecorSpec s,int mask){return new DecorSpec(kind,
        (mask&1)!=0?s.width:width,(mask&1)!=0?s.height:height,(mask&1)!=0?s.depth:depth,
        (mask&1)!=0?s.spacing:spacing,(mask&1)!=0?s.suspension:suspension,
        (mask&4)!=0?s.yaw:yaw,(mask&4)!=0?s.pitch:pitch,(mask&4)!=0?s.x:x,(mask&4)!=0?s.y:y,(mask&4)!=0?s.z:z,
        (mask&2)!=0?s.color:color,(mask&2)!=0?s.accent:accent,(mask&2)!=0?s.material:material,
        (mask&8)!=0?s.variant:variant,(mask&2)!=0?s.illuminated:illuminated,(mask&16)!=0?s.image:image,(mask&16)!=0?s.label:label);}
}
