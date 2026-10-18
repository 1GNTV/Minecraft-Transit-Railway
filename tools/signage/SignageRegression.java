import org.mtr.mod.data.*;
import org.mtr.mod.client.*;
import org.mtr.mod.screen.*;
import org.mtr.mapping.holder.CompoundTag;
import java.util.*;
import java.util.List;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class SignageRegression {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path out=Path.of("build/signage-qa/panels");Files.createDirectories(out);
        Set<String> families=new HashSet<>();
        StringBuilder commands=new StringBuilder();
        int index=0,cols=3,cw=640,ch=360,rows=(SillonSignagePresets.ALL.size()+cols-1)/cols;
        BufferedImage atlas=new BufferedImage(cw*cols,ch*rows,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=atlas.createGraphics();SillonSignagePainter.quality(g);
        g.setColor(new Color(0xE8EBF0));g.fillRect(0,0,atlas.getWidth(),atlas.getHeight());
        for(var preset:SillonSignagePresets.ALL) {
            SillonSignageSpec spec=preset.document();families.add(spec.style());
            BufferedImage im=SillonSignagePainter.render(spec);
            int[] dims=SillonSignagePainter.dimensions(spec);
            check(im.getWidth()==dims[0]&&im.getHeight()==dims[1],"Texture dimensions");
            double error=Math.abs(im.getWidth()/(double)im.getHeight()-spec.width()/spec.height())/(spec.width()/spec.height());
            check(error<.02,"Aspect ratio "+preset.name());
            CompoundTag tag=new CompoundTag();spec.write(tag);
            commands.append("setblock ").append((index%5-2)*7).append(" ").append(5+(index/5)*4).append(" 0 mtr:sillon_signage[facing=north]").append(tag.toString()).append("\n");
            check(spec.equals(SillonSignageSpec.read(tag)),"NBT roundtrip "+preset.name());
            check(SillonSignageOptions.decode(spec.options().encode()).equals(spec.options()),"Options roundtrip");
            var editor=new SillonSignageEditorModel(spec);check(editor.error().isEmpty(),"Editor accepts preset");
            check(editor.spec().equals(spec),"Editor roundtrip "+preset.name());
            if(spec.title().contains("|"))check(!editor.values.get("title2").isBlank(),"Two separate title inputs");
            int x=(index%cols)*cw,y=(index/cols)*ch;
            g.setColor(Color.WHITE);g.fillRoundRect(x+8,y+8,cw-16,ch-16,12,12);
            SillonSignagePainter.image(g,im,x+24,y+42,cw-48,ch-82);
            SillonSignagePainter.text(g,preset.name(),new Font("SansSerif",Font.BOLD,17),0x1B2940,x+20,y+12,cw-40,26,"left");
            SillonSignagePainter.text(g,SillonSignagePresets.label(spec.style()),new Font("SansSerif",Font.PLAIN,12),0x4E6078,x+20,y+ch-30,cw-40,16,"left");
            ImageIO.write(im,"png",out.resolve(String.format("%02d-%s.png",index,spec.style())).toFile());
            im.flush();index++;
        }
        Files.writeString(Path.of("build/signage-qa/fixtures-commands.txt"),commands.toString());
        g.dispose();ImageIO.write(atlas,"png",Path.of("build/signage-qa/panel-atlas.png").toFile());
        check(families.containsAll(SillonSignageSpec.STYLES),"Every family has a preset");
        for(var entry:SillonSignageIconCatalog.ENTRIES)check(SillonSignagePainter.icon(entry.id())!=null,"Missing icon "+entry.id());
        check(SillonSignageIconCatalog.find("metro 14","Métro").stream().anyMatch(e->e.id().equals("idfm_m14")),"Human-readable icon search");
        for(String family:SillonSignageOptions.FONTS) {
            Font font=SillonSignagePainter.font(family,true,32);
            System.out.println("FONT "+family+" "+font.getFontName());
            check(!font.getFamily().equals("SansSerif"),"Real font loaded for "+family);
        }
        check(SillonSignagePainter.resource("mtr:signage/maps/metro.png")!=null,"Real map bundled");
        var row=new SillonSignageRow("exit","12","rue de l’Église","Exit / Salida","idfm_m4,idfm_pmr",5);
        check(SillonSignageRow.decode(SillonSignageRow.encode(List.of(row))).equals(List.of(row)),"Unicode wire roundtrip");
        check(SillonSignageRow.decode("not a document").isEmpty(),"Malformed row payload");
        String wide="é😀".repeat(80);List<SillonSignageRow> huge=new ArrayList<>();
        for(int i=0;i<40;i++)huge.add(new SillonSignageRow("exit","123",wide,wide,"idfm_m14,".repeat(30),7));
        String encoded=SillonSignageRow.encode(huge);
        check(encoded.length()<30000,"Network budget");
        check(SillonSignageRow.decode(encoded).equals(SillonSignageRow.bounded(huge)),"Bounded Unicode document");
        var model=new SillonSignageEditorModel(SillonSignageSpec.DEFAULT);
        model.values.put("width","NaN");check(!model.error().isEmpty(),"NaN rejected");
        model.values.put("width","13");check(!model.error().isEmpty(),"Out of range rejected");
        model.values.put("width","2,5");check(model.error().isEmpty(),"French decimal accepted");
        model.addRow();model.values.put("row_text","Aéroport d’Orly");model.saveRow();
        model.addRow();model.values.put("row_text","Saint-Denis Pleyel");model.saveRow();model.moveRow(-1);
        check(model.rows.get(0).text().equals("Saint-Denis Pleyel"),"Row reorder");
        model.toggleIcon(true,"idfm_m14");model.toggleIcon(true,"idfm_pmr");model.moveIcon(true,1,-1);
        check(model.icons(true).equals("idfm_pmr,idfm_m14"),"Icon reorder");
        model.removeRow();check(model.rows.size()==1,"Row removal");
        CompoundTag old=new CompoundTag();SillonSignageSpec.DEFAULT.write(old);old.putInt("sillon_signage_version",1);
        check(SillonSignageSpec.read(old).options().equals(SillonSignageOptions.DEFAULT),"Version 1 migration");
        for(int[] resolution:new int[][]{{320,240},{480,270},{640,360},{720,400},{960,540},{1280,720}}) {
            var l=SillonSignageEditorLayout.of(resolution[0],resolution[1]);
            check(!l.editor().intersects(l.preview()),"Preview overlap");
            check(l.editor().bottom()<=l.pagerY(),"Footer separation");
            check(l.preview().h()>=30,"Preview always visible");
            for(int toolbar:new int[]{0,24})check(l.editor().y()+toolbar+(l.formRows(toolbar)-1)*38+32<=l.pagerY(),"Form fits");
        }
        System.out.println("PASS "+families.size()+" families, "+index+" rendered presets, "+SillonSignageIconCatalog.ENTRIES.size()+" icons, 3 fonts, serialization, row editing and responsive geometry");
    }
}
