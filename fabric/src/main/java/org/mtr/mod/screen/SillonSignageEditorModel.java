package org.mtr.mod.screen;

import org.mtr.mod.data.*;
import java.util.*;

/** Pure editor state, validation and row operations. No Minecraft dependency. */
public final class SillonSignageEditorModel {
    public record Field(String key,String label,String type,List<String> choices,float min,float max,float step) {}
    public static final Map<String,Field> FIELDS=new LinkedHashMap<>();
    static {
        string("title","Nom / destination");string("subtitle","Sous-titre / traduction");
        choice("auto","Nom depuis la station MTR",List.of("Non","Oui"));
        choice("style","Famille de panneau",SillonSignageSpec.STYLES);
        choice("font","Typographie",SillonSignageOptions.FONTS);choice("align","Alignement",SillonSignageOptions.ALIGNS);
        color("bg","Fond");color("fg","Texte");color("accent","Bandeau / ligne");color("frameColor","Couleur du cadre");
        number("textScale","Taille des caractères",.5f,1.8f,.05f);number("iconScale","Taille des pictogrammes",.5f,1.6f,.05f);
        number("padding","Marges",.25f,2,.05f);number("bandRatio","Part du bandeau",.1f,.4f,.01f);
        choice("frame","Cadre",SillonSignageOptions.FRAMES);string("image","Image du pack de ressources");
        number("width","Largeur (blocs)",.4f,12,.125f);number("height","Hauteur (blocs)",.2f,8,.125f);
        number("offsetX","Décalage horizontal X",-12,12,.0625f);number("offsetY","Décalage vertical Y",-12,12,.0625f);
        number("offsetZ","Décalage profondeur Z",-4,4,.0625f);
        number("yaw","Orientation Y (degrés)",-180,180,5);number("pitch","Inclinaison X (degrés)",-90,90,5);
        number("roll","Roulis Z (degrés)",-180,180,5);number("thickness","Épaisseur (blocs)",.005f,.35f,.005f);
        number("suspension","Longueur des tiges",.05f,6,.0625f);
        choice("mount","Fixation",SillonSignageSpec.MOUNTS);choice("lit","Rétroéclairage",List.of("Non","Oui"));
        choice("double","Double face",List.of("Non","Oui"));
        choice("arrow","Flèche générale",List.of("-1","0","1","2","3","4","5","6","7"));
        string("row_text","Destination / libellé");string("row_detail","Traduction / précision");string("row_number","Numéro de sortie / voie");
        choice("row_kind","Type de ligne",SillonSignageRow.KINDS);
        choice("row_arrow","Flèche de cette ligne",List.of("-1","0","1","2","3","4","5","6","7"));
    }
    public static final List<String> TEXT=List.of("title","subtitle","auto","arrow");
    public static final List<String> APPEARANCE=List.of("style","font","align","bg","fg","accent","textScale","iconScale","padding","bandRatio","frame","frameColor","image");
    public static final List<String> POSE=List.of("width","height","mount","offsetX","offsetY","offsetZ","yaw","pitch","roll","thickness","suspension","lit","double");
    public static final List<String> ROW=List.of("row_text","row_detail","row_number","row_kind","row_arrow");
    public final Map<String,String> values=new LinkedHashMap<>();
    public final List<SillonSignageRow> rows=new ArrayList<>();
    public int selectedRow=-1;
    private String headerIcons="",rowIcons="",legacyBody="";
    private static void string(String k,String label){FIELDS.put(k,new Field(k,label,"text",List.of(),0,0,0));}
    private static void color(String k,String label){FIELDS.put(k,new Field(k,label,"color",List.of(),0,0,0));}
    private static void number(String k,String label,float lo,float hi,float step){FIELDS.put(k,new Field(k,label,"number",List.of(),lo,hi,step));}
    private static void choice(String k,String label,List<String> list){FIELDS.put(k,new Field(k,label,"choice",list,0,0,0));}
    public SillonSignageEditorModel(SillonSignageSpec spec){read(spec);}
    public void read(SillonSignageSpec s) {
        values.clear();var o=s.options();
        values.put("title",s.title());values.put("subtitle",s.subtitle());values.put("style",s.style());
        values.put("auto",s.stationAuto()?"Oui":"Non");values.put("lit",s.illuminated()?"Oui":"Non");
        values.put("double",s.doubleSided()?"Oui":"Non");values.put("mount",s.mount());values.put("arrow",""+s.arrow());
        values.put("bg",hex(s.background()));values.put("fg",hex(s.foreground()));values.put("accent",hex(s.accent()));
        values.put("font",o.font());values.put("align",o.align());values.put("frame",o.frame());values.put("frameColor",hex(o.frameColor()));
        values.put("image",o.image());
        put("width",s.width());put("height",s.height());put("yaw",s.yaw());put("pitch",s.pitch());put("roll",s.roll());
        put("offsetX",o.offsetX());put("offsetY",o.offsetY());put("offsetZ",o.offsetZ());
        put("textScale",o.textScale());put("iconScale",o.iconScale());put("padding",o.padding());put("bandRatio",o.bandRatio());
        put("thickness",o.thickness());put("suspension",o.suspension());
        headerIcons=s.icons();legacyBody=s.body();rows.clear();rows.addAll(s.rows());selectedRow=-1;
        loadRow();
    }
    private void put(String key,float value){values.put(key,format(value));}
    public static String format(float value){return new java.math.BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();}
    public static String hex(int value){return String.format(Locale.ROOT,"%06X",value&0xFFFFFF);}
    public void cycle(String key,int direction) {
        Field f=FIELDS.get(key);if(f==null||f.choices().isEmpty())return;
        int at=f.choices().indexOf(values.get(key));
        values.put(key,f.choices().get(Math.floorMod(at+direction,f.choices().size())));
        if("style".equals(key)) {
            SillonSignageSpec preset=SillonSignagePresets.forStyle(values.get(key));
            values.put("bg",hex(preset.background()));values.put("fg",hex(preset.foreground()));
            values.put("accent",hex(preset.accent()));values.put("font",preset.options().font());
        }
    }
    public void adjust(String key,int direction) {
        Field f=FIELDS.get(key);
        try {float n=Float.parseFloat(values.get(key).replace(',','.'));
            put(key,Math.max(f.min(),Math.min(f.max(),Math.round((n+direction*f.step())*10000)/10000f)));
        }catch(RuntimeException ignored){}
    }
    public String error() {
        for(Field f:FIELDS.values()) {
            String v=values.getOrDefault(f.key(),"");
            try {
                if("number".equals(f.type())) {
                    float n=Float.parseFloat(v.trim().replace(',','.'));
                    if(!Float.isFinite(n)||n<f.min()||n>f.max())return f.label()+" : "+format(f.min())+" à "+format(f.max());
                }else if("color".equals(f.type())&&!v.matches("#?[0-9a-fA-F]{6}"))return f.label()+" : 6 chiffres hexadécimaux";
            }catch(RuntimeException e){return f.label()+" : nombre attendu";}
        }
        String image=values.getOrDefault("image","");
        if(image.length()>192||image.contains("..")||!image.matches("[a-z0-9_.:/-]*"))return "Image : utiliser un identifiant du pack, par ex. mtr:signage/maps/metro.png";
        return "";
    }
    private float n(String key){return Float.parseFloat(values.get(key).trim().replace(',','.'));}
    private int c(String key){return Integer.parseInt(values.get(key).replace("#",""),16);}
    public SillonSignageSpec spec() {
        String error=error();if(!error.isEmpty())throw new IllegalArgumentException(error);
        saveRow();
        SillonSignageOptions o=new SillonSignageOptions(values.get("font"),values.get("align"),n("textScale"),n("iconScale"),n("padding"),
            n("offsetX"),n("offsetY"),n("offsetZ"),n("thickness"),n("suspension"),values.get("frame"),c("frameColor"),values.get("image"),n("bandRatio"));
        return new SillonSignageSpec(values.get("title"),values.get("subtitle"),legacyBody,values.get("style"),headerIcons,
            values.get("mount"),Integer.parseInt(values.get("arrow")),c("bg"),c("fg"),c("accent"),n("width"),n("height"),
            n("yaw"),n("pitch"),n("roll"),"Oui".equals(values.get("auto")),"Oui".equals(values.get("lit")),"Oui".equals(values.get("double")),o,rows);
    }
    public void saveRow() {
        if(selectedRow>=0&&selectedRow<rows.size())rows.set(selectedRow,new SillonSignageRow(
            values.get("row_kind"),values.get("row_number"),values.get("row_text"),values.get("row_detail"),rowIcons,Integer.parseInt(values.getOrDefault("row_arrow","-1"))));
    }
    private void loadRow() {
        SillonSignageRow r=selectedRow>=0&&selectedRow<rows.size()?rows.get(selectedRow):new SillonSignageRow("transport","","","","",-1);
        values.put("row_text",r.text());values.put("row_detail",r.detail());values.put("row_number",r.number());
        values.put("row_kind",r.kind());values.put("row_arrow",""+r.arrow());rowIcons=r.icons();
    }
    public void selectRow(int index){saveRow();selectedRow=rows.isEmpty()?-1:Math.floorMod(index,rows.size());loadRow();}
    public void addRow() {
        saveRow();if(rows.size()>=SillonSignageRow.MAX_ROWS)return;
        String kind=Set.of("exit","sector").contains(values.get("style"))?"exit":"transport";
        rows.add(new SillonSignageRow(kind,"","","","",-1));selectedRow=rows.size()-1;loadRow();
    }
    public void importLegacyRows() {
        saveRow();if(!rows.isEmpty())return;
        String kind=Set.of("exit","sector").contains(values.get("style"))?"exit":"transport";
        rows.addAll(SillonSignageRow.legacy(legacyBody,kind));legacyBody="";
        selectedRow=rows.isEmpty()?-1:0;loadRow();
    }
    public void removeRow(){if(selectedRow<0)return;rows.remove(selectedRow);selectedRow=Math.min(selectedRow,rows.size()-1);loadRow();}
    public void moveRow(int direction) {
        saveRow();int next=selectedRow+direction;
        if(selectedRow<0||next<0||next>=rows.size())return;
        Collections.swap(rows,selectedRow,next);selectedRow=next;loadRow();
    }
    public String icons(boolean row){return row&&selectedRow>=0?rowIcons:headerIcons;}
    public void setIcons(boolean row,String value){if(row&&selectedRow>=0)rowIcons=value;else headerIcons=value;}
    public void toggleIcon(boolean row,String id) {
        List<String> list=new ArrayList<>(Arrays.asList(icons(row).split(",")));list.removeIf(String::isBlank);
        if(!list.remove(id)&&list.size()<16)list.add(id);setIcons(row,String.join(",",list));
    }
    public void moveIcon(boolean row,int index,int delta) {
        List<String> list=new ArrayList<>(Arrays.asList(icons(row).split(",")));int next=index+delta;
        if(index<0||index>=list.size()||next<0||next>=list.size())return;
        Collections.swap(list,index,next);setIcons(row,String.join(",",list));
    }
}
