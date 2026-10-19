package org.mtr.mod.data;

import org.mtr.mapping.holder.CompoundTag;
import java.util.List;


/**
 * Versioned and bounded signage document. Both the server and the compositor
 * consume this single immutable object; no client-side text is trusted.
 */
public record SillonSignageSpec(
    String title, String subtitle, String body, String style, String icons,
    String mount, int arrow, int background, int foreground, int accent,
    float width, float height, float yaw, float pitch, float roll,
    boolean stationAuto, boolean illuminated, boolean doubleSided,
    SillonSignageOptions options, List<SillonSignageRow> rows, SillonSignageBinding binding, SillonSignagePlan plan, SillonSignageDesign design) {
    public static final List<String> STYLES=List.of(
        "metro","metro_subtitle","rer","direction","transfer","interchange","exit","sector","vertical",
        "platform","route","map","services","totem","digital","custom","gpe","gpe_direction");
    public static final List<String> MOUNTS=List.of("wall","ceiling","hanging","flag","standing");
    public static final SillonSignageSpec DEFAULT=new SillonSignageSpec(
        "Station à associer","","","metro_subtitle",
        "","wall",-1,0x101D56,0xFFFFFF,0x80591B,
        2.0f,.60f,0,0,0,true,false,false);

    public SillonSignageSpec(String title,String subtitle,String body,String style,String icons,String mount,
        int arrow,int background,int foreground,int accent,float width,float height,float yaw,float pitch,float roll,
        boolean stationAuto,boolean illuminated,boolean doubleSided,SillonSignageOptions options,List<SillonSignageRow> rows,SillonSignageBinding binding,SillonSignagePlan plan){
        this(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,rows,binding,plan,SillonSignageDesign.DEFAULT);
    }
    public SillonSignageSpec(String title,String subtitle,String body,String style,String icons,String mount,
        int arrow,int background,int foreground,int accent,float width,float height,float yaw,float pitch,float roll,
        boolean stationAuto,boolean illuminated,boolean doubleSided,SillonSignageOptions options,List<SillonSignageRow> rows) {
        this(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,yaw,pitch,roll,
            stationAuto,illuminated,doubleSided,options,rows,SillonSignageBinding.DEFAULT,SillonSignagePlan.EMPTY,SillonSignageDesign.DEFAULT);
    }
    public SillonSignageSpec(String title,String subtitle,String body,String style,String icons,String mount,
        int arrow,int background,int foreground,int accent,float width,float height,float yaw,float pitch,float roll,
        boolean stationAuto,boolean illuminated,boolean doubleSided) {
        this(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,yaw,pitch,roll,
            stationAuto,illuminated,doubleSided,SillonSignageOptions.DEFAULT,List.of());
    }
    public SillonSignageSpec {
        title=bounded(title,128);
        subtitle=bounded(subtitle,128);
        body=bounded(body,2048);
        design=design==null?SillonSignageDesign.DEFAULT:design;
        options=options==null?SillonSignageOptions.DEFAULT:options;
        binding=binding==null?SillonSignageBinding.DEFAULT:binding;plan=plan==null?SillonSignagePlan.EMPTY:plan;
        rows=SillonSignageRow.bounded(rows);
        style=STYLES.contains(style)?style:"metro";
        icons=SillonSignageRow.normalizeIcons(icons);
        mount=MOUNTS.contains(mount)?mount:"wall";
        arrow=Math.max(-1,Math.min(7,arrow));
        background&=0xFFFFFF;foreground&=0xFFFFFF;accent&=0xFFFFFF;
        width=boundedFloat(width,.4f,12f,2f);
        height=boundedFloat(height,.2f,8f,.6f);
        yaw=boundedFloat(yaw,-180f,180f,0f);
        pitch=boundedFloat(pitch,-90f,90f,0f);
        roll=boundedFloat(roll,-180f,180f,0f);
    }
    private static String bounded(String value,int max) {
        if(value==null)return "";
        return value.length()<=max?value:value.substring(0,max);
    }
    private static float boundedFloat(float value,float min,float max,float fallback){
        return Float.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;
    }
    public SillonSignageSpec withDesign(SillonSignageDesign value){
        return new SillonSignageSpec(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,rows,binding,plan,value);
    }
    public SillonSignageSpec withSize(float width,float height){return new SillonSignageSpec(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,rows,binding,plan,design);}
    public SillonSignageSpec withTitle(String text){
        return new SillonSignageSpec(text,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,rows,binding,plan,design);
    }
    public SillonSignageSpec withBody(String text){
        return new SillonSignageSpec(title,subtitle,text,style,icons,mount,arrow,background,foreground,accent,width,height,yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,rows,binding,plan,design);
    }
    public SillonSignageSpec withOptions(SillonSignageOptions value) {
        return new SillonSignageSpec(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,
            yaw,pitch,roll,stationAuto,illuminated,doubleSided,value,rows,binding,plan,design);
    }
    public SillonSignageSpec withRows(List<SillonSignageRow> value) {
        return new SillonSignageSpec(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,
            yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,value,binding,plan,design);
    }
    public SillonSignageSpec withArrow(int direction) {
        return new SillonSignageSpec(title,subtitle,body,style,icons,mount,direction,background,foreground,accent,width,height,
            yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,rows,binding,plan,design);
    }
    public SillonSignageSpec manual() {
        return new SillonSignageSpec(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,
            yaw,pitch,roll,false,illuminated,doubleSided,options,plan.lines().isEmpty()?rows:plan.lines().get(0).stops().stream().map(s->new SillonSignageRow("text","",s.name(),s.detail(),s.icons(),-1)).toList(),binding,SillonSignagePlan.EMPTY,design);
    }
    public SillonSignageSpec withBinding(SillonSignageBinding value) {
        return new SillonSignageSpec(title,subtitle,body,style,icons,mount,arrow,background,foreground,accent,width,height,
            yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,rows,value,plan,design);
    }
    public SillonSignageSpec automatic() {
        return new SillonSignageSpec("","","",style,"",mount,arrow,background,foreground,accent,width,height,
            yaw,pitch,roll,true,illuminated,doubleSided,options,List.of(),binding,SillonSignagePlan.EMPTY,design);
    }
    public SillonSignageSpec materialize(String name,String sub,String symbols,int lineColor,List<SillonSignageRow> content,SillonSignagePlan diagram) {
        return new SillonSignageSpec(name,sub,"",style,symbols,mount,arrow,background,foreground,lineColor,width,height,
            yaw,pitch,roll,stationAuto,illuminated,doubleSided,options,content,binding,diagram,design);
    }
    public List<SillonSignageRow> effectiveRows(String kind) {
        return rows.isEmpty()?SillonSignageRow.legacy(body,kind):rows;
    }
    public String visualKey() {
        return title+"\u0001"+subtitle+"\u0001"+body+"\u0001"+style+"\u0001"+icons+"\u0001"+
            arrow+"/"+background+"/"+foreground+"/"+accent+"/"+width+"/"+height+"/"+
            options.font()+"/"+options.align()+"/"+options.textScale()+"/"+options.iconScale()+"/"+
            options.padding()+"/"+options.bandRatio()+"/"+options.frame()+"/"+options.frameColor()+"/"+
            options.image()+"/"+options.freePalette()+"/"+SillonSignageRow.encode(rows)+"/"+plan.key()+"/"+design.encode();
    }
    public void write(CompoundTag tag){
        tag.putInt("sillon_signage_version",4);
        tag.putString("sillon_design",design.encode());
        tag.putString("sillon_binding",binding.encode());
        tag.putString("sillon_options",options.encode());
        tag.putString("sillon_rows",SillonSignageRow.encode(rows));
        tag.putString("sillon_title",title);
        tag.putString("sillon_subtitle",subtitle);
        tag.putString("sillon_body",body);
        tag.putString("sillon_style",style);
        tag.putString("sillon_icons",icons);
        tag.putString("sillon_mount",mount);
        tag.putInt("sillon_arrow",arrow);
        tag.putInt("sillon_background",background);
        tag.putInt("sillon_foreground",foreground);
        tag.putInt("sillon_accent",accent);
        tag.putFloat("sillon_width",width);
        tag.putFloat("sillon_height",height);
        tag.putFloat("sillon_yaw",yaw);
        tag.putFloat("sillon_pitch",pitch);
        tag.putFloat("sillon_roll",roll);
        tag.putBoolean("sillon_auto",stationAuto);
        tag.putBoolean("sillon_lit",illuminated);
        tag.putBoolean("sillon_double",doubleSided);
    }
    private static SillonSignageOptions legacyOptions(String style) {
        return switch(style) {
            case "rer"->SillonSignageOptions.DEFAULT.withBand(.26f);
            case "sector"->SillonSignageOptions.DEFAULT.withBand(.34f);
            default->SillonSignageOptions.DEFAULT;
        };
    }
    public static SillonSignageSpec read(CompoundTag tag){
        int version=tag.getInt("sillon_signage_version");
        if(version<1||version>4)return DEFAULT;
        return new SillonSignageSpec(
            tag.getString("sillon_title"),tag.getString("sillon_subtitle"),tag.getString("sillon_body"),
            tag.getString("sillon_style"),tag.getString("sillon_icons"),tag.getString("sillon_mount"),
            tag.getInt("sillon_arrow"),tag.getInt("sillon_background"),tag.getInt("sillon_foreground"),
            tag.getInt("sillon_accent"),tag.getFloat("sillon_width"),tag.getFloat("sillon_height"),
            tag.getFloat("sillon_yaw"),tag.getFloat("sillon_pitch"),tag.getFloat("sillon_roll"),
            tag.getBoolean("sillon_auto"),tag.getBoolean("sillon_lit"),tag.getBoolean("sillon_double"),
            version>=2?SillonSignageOptions.decode(tag.getString("sillon_options")):legacyOptions(tag.getString("sillon_style")),
            version>=2?SillonSignageRow.decode(tag.getString("sillon_rows")):List.of(),
            version>=3?SillonSignageBinding.decode(tag.getString("sillon_binding")):SillonSignageBinding.DEFAULT,SillonSignagePlan.EMPTY,version>=4?SillonSignageDesign.decode(tag.getString("sillon_design")):SillonSignageDesign.DEFAULT);
    }
}
