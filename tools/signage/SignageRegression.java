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
    private static SillonSignageAutomatic.Context context(){return new SillonSignageAutomatic.Context(new SillonSignageNetworkSnapshot.Point(0,64,0),2,22,0);}
    private static SillonSignageNetworkSnapshot network(boolean changed){
        var p1=new SillonSignageNetworkSnapshot.Point(-100,64,-40);var p2=new SillonSignageNetworkSnapshot.Point(0,64,0);var p3=new SillonSignageNetworkSnapshot.Point(80,64,80);
        var exits=List.of(new SillonSignageNetworkSnapshot.Exit("2","Rue des Ateliers","Accès nord"),new SillonSignageNetworkSnapshot.Exit("10","Place des Tilleuls","Accès sud"));
        var stations=List.of(new SillonSignageNetworkSnapshot.Station(1,"Val-des-Bois","",p1,List.of(),List.of()),new SillonSignageNetworkSnapshot.Station(2,changed?"Nouvelle Manufacture":"Manufacture","Quartier des Arts",p2,exits,List.of()),new SillonSignageNetworkSnapshot.Station(3,changed?"Port étendu":"Port des Brumes","",p3,List.of(),List.of()));
        var platforms=List.of(new SillonSignageNetworkSnapshot.Platform(11,1,"1",p1),new SillonSignageNetworkSnapshot.Platform(22,2,"2",p2),new SillonSignageNetworkSnapshot.Platform(33,3,"3",p3));
        var stops=List.of(new SillonSignageNetworkSnapshot.Stop(1,11,"Val-des-Bois",changed?"Port étendu":"Port des Brumes"),new SillonSignageNetworkSnapshot.Stop(2,22,changed?"Nouvelle Manufacture":"Manufacture",changed?"Port étendu":"Port des Brumes"),new SillonSignageNetworkSnapshot.Stop(3,33,changed?"Port étendu":"Port des Brumes",""));
        var lines=List.of(new SillonSignageNetworkSnapshot.Line(101,changed?"Z7":"X42","metro",changed?0xB75609:0x15865A,stops),new SillonSignageNetworkSnapshot.Line(202,"R9","rer",0xC24C9A,List.of(stops.get(1),stops.get(0))));
        return new SillonSignageNetworkSnapshot(stations,platforms,lines,List.of(new SillonSignageNetworkSnapshot.Service(2,"lift","Ascenseur","Quai 2","idfm_pmr",new SillonSignageNetworkSnapshot.Point(6,70,0))));
    }
    private static SillonSignageSpec preset(String style){return SillonSignagePresets.ALL.stream().map(p->p.document()).filter(s->s.style().equals(style)).findFirst().orElseThrow();}
    private static void automaticChecks(){
        var binding=new SillonSignageBinding(2,22,101,"auto","full","vertical","é","lift",true,true);
        check(SillonSignageBinding.decode(binding.encode()).equals(binding),"Binding roundtrip");
        var saved=preset("route").withBinding(binding);CompoundTag tag=new CompoundTag();saved.write(tag);
        check(SillonSignageSpec.read(tag).equals(saved),"Version 3 sticky network IDs");
        check(new SillonSignageEditorModel(saved).spec().equals(saved),"Network binding editor roundtrip");
        for(String style:SillonSignageSpec.STYLES){
            var template=preset(style).withBinding(new SillonSignageBinding(2,22,101,"auto","full","auto","","auto",true,true)).withBody("STALE PARIS CONTENT");
            var a=SillonSignageAutomatic.resolve(template,network(false),context());
            var b=SillonSignageAutomatic.resolve(template,network(true),context());
            check(a.stationId()==2&&a.platformId()==22,"Live association for "+style);
            check(a.document().body().isEmpty(),"No old manual body in "+style);
            check(!a.document().toString().contains("STALE PARIS"),"No stale sample in "+style);
            if(!List.of("services","digital").contains(style))check(!a.document().visualKey().equals(b.document().visualKey()),"Rename/recolour propagates to "+style);
            var empty=SillonSignageAutomatic.resolve(template,SillonSignageNetworkSnapshot.EMPTY,context());
            check(empty.document().rows().isEmpty()&&empty.document().plan().lines().isEmpty(),"Missing data has no invented content "+style);
        }
        var route=SillonSignageAutomatic.resolve(preset("route").withBinding(binding),network(false),context()).document();
        check(route.plan().lines().get(0).label().equals("X42"),"Actual arbitrary route number");
        check(route.plan().lines().get(0).color()==0x15865A,"Actual arbitrary route colour");
        check(route.plan().lines().get(0).stops().size()==3,"All actual stops");
        check(route.plan().lines().get(0).stops().get(1).icons().contains(new SillonSignageLineBadge("rer",0xC24C9A,"R9").token()),"Real interchange at actual stop");
        check(route.manual().rows().size()==3,"Manual conversion retains generated stops");
        var remaining=new SillonSignageBinding(2,22,101,"auto","remaining","horizontal","","auto",true,true);
        check(SillonSignageAutomatic.resolve(preset("route").withBinding(remaining),network(false),context()).document().plan().lines().get(0).stops().size()==2,"Remaining stops from bound platform");
        var exit=SillonSignageAutomatic.resolve(preset("exit"),network(false),context()).document();
        check(exit.rows().size()==2&&exit.rows().get(1).text().equals("Place des Tilleuls"),"Actual MTR exits");
        check(SillonSignageNetworkOrdering.compare("2","10")<0,"Natural exit numbering");
        check(SillonSignageAutomatic.resolve(preset("services"),network(false),context()).document().rows().get(0).text().equals("Ascenseur"),"Actual service");
        for(String mode:SillonSignageBinding.MODES)check(SillonSignagePainter.icon(SillonSignageLineBadge.modeIcon(mode))!=null,"Actual mode asset "+mode);
        String token=new SillonSignageLineBadge("metro",0x123456,"é😀".repeat(20)).token();
        check(SillonSignageLineBadge.parse(token)!=null&&SillonSignageRow.normalizeIcons(token).equals(token),"Unicode custom badges stay valid");
        System.out.println("PASS automatic data for all 18 families, arbitrary network edits, IDs, platform scope, exits, services and interchange graph");
    }
    private static void renderDenseRoute(Path out)throws Exception {
        List<SillonSignagePlan.Stop> stops=new ArrayList<>();
        String[] names={"Bois des Forges","Parc des Saules","Université des Arts","Les Ateliers","Jardin des Halles","Manufacture","Hôtel de Ville","Place des Tilleuls","Musée des Rives","Cours des Verriers","Les Docks","Quai des Écluses","Port des Brumes","Grand Théâtre","Rochefontaine","Parc du Belvédère","Cité des Jardins","Val des Sources","Les Amandiers","Monts de Lumière","Aérogare du Levant"};
        for(int i=0;i<names.length;i++)stops.add(new SillonSignagePlan.Stop(i+1,names[i],i%5==0?"Quartier des Arts":"",i%3==0?"ratp_rer,"+new SillonSignageLineBadge("rer",0xC24C9A,"R9").token():"",i*100,i*40,true));
        for(String orientation:List.of("horizontal","vertical")){
            var spec=SillonSignagePresets.ALL.stream().map(p->p.document()).filter(d->d.style().equals("route")&&d.binding().layout().equals(orientation)).findFirst().orElseThrow();
            var plan=new SillonSignagePlan(List.of(new SillonSignagePlan.Line(101,"X42","metro",0x15865A,stops)),6,orientation);
            var resolved=spec.materialize("Aérogare du Levant","","ratp_metro,"+new SillonSignageLineBadge("metro",0x15865A,"X42").token(),0x15865A,List.of(),plan);
            ImageIO.write(SillonSignagePainter.render(resolved),"png",out.resolve("dense-21-stops-"+orientation+".png").toFile());
        }
    }
    public static void main(String[] args)throws Exception {
        automaticChecks();
        Path out=Path.of("build/signage-qa/panels");Files.createDirectories(out);
        Set<String> families=new HashSet<>();
        StringBuilder commands=new StringBuilder();
        int index=0,cols=3,cw=640,ch=360,rows=(SillonSignagePresets.ALL.size()+cols-1)/cols;
        BufferedImage atlas=new BufferedImage(cw*cols,ch*rows,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=atlas.createGraphics();SillonSignagePainter.quality(g);
        g.setColor(new Color(0xE8EBF0));g.fillRect(0,0,atlas.getWidth(),atlas.getHeight());
        for(var preset:SillonSignagePresets.ALL) {
            SillonSignageSpec spec=preset.document();families.add(spec.style());
            SillonSignageSpec resolved=SillonSignageAutomatic.resolve(spec,network(false),context()).document();
            BufferedImage im=SillonSignagePainter.render(resolved);
            int[] dims=SillonSignagePainter.dimensions(spec);
            check(im.getWidth()==dims[0]&&im.getHeight()==dims[1],"Texture dimensions");
            double error=Math.abs(im.getWidth()/(double)im.getHeight()-spec.width()/spec.height())/(spec.width()/spec.height());
            check(error<.02,"Aspect ratio "+preset.name());
            CompoundTag tag=new CompoundTag();spec.write(tag);
            commands.append("setblock ").append((index%5-2)*7).append(" ").append(5+(index/5)*4).append(" 0 mtr:sillon_signage[facing=south]").append(tag.toString()).append("\n");
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
        renderDenseRoute(out);
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
        model.values.put("title","x".repeat(128));model.values.put("title2","y");
        check(!model.error().isEmpty(),"Two-line names cannot silently truncate");
        model.values.put("title",SillonSignageSpec.DEFAULT.title());model.values.put("title2","");
        model.values.put("width","NaN");check(!model.error().isEmpty(),"NaN rejected");
        model.values.put("width","13");check(!model.error().isEmpty(),"Out of range rejected");
        model.values.put("width","2,5");check(model.error().isEmpty(),"French decimal accepted");
        model.addRow();model.values.put("row_text","Aéroport d’Orly");model.saveRow();
        model.addRow();model.values.put("row_text","Saint-Denis Pleyel");model.saveRow();model.moveRow(-1);
        check(model.rows.get(0).text().equals("Saint-Denis Pleyel"),"Row reorder");
        model.toggleIcon(true,"idfm_m14");model.toggleIcon(true,"idfm_pmr");model.moveIcon(true,1,-1);
        check(model.icons(true).equals("idfm_pmr,idfm_m14"),"Icon reorder");
        model.removeRow();check(model.rows.size()==1,"Row removal");
        var mixed=SillonSignagePresets.ALL.get(9).document();
        var free=mixed.withOptions(mixed.options().withFreePalette(true));
        check(SillonSignageOptions.decode(free.options().encode()).equals(free.options()),"Free palette serialization");
        check(!free.visualKey().equals(mixed.visualKey()),"Free palette invalidates texture");
        BufferedImage originalPalette=SillonSignagePainter.render(mixed),chosenPalette=SillonSignagePainter.render(free);
        check(originalPalette.getRGB(20,20)!=chosenPalette.getRGB(20,20),"Composed transport zone uses chosen background");
        originalPalette.flush();chosenPalette.flush();
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
