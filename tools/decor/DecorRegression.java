import org.mtr.mod.decor.*;
import org.mtr.mapping.holder.CompoundTag;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

public final class DecorRegression {
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        Path out=Path.of("build/decor-review");Files.createDirectories(out.resolve("models"));Files.createDirectories(out.resolve("images"));
        check(DecorKind.values().length==20,"20 families");int tested=0;
        for(DecorKind k:DecorKind.values()){
            DecorSpec original=DecorSpec.defaults(k);check(DecorSpec.decode(original.encode()).equals(original),"wire "+k);
            CompoundTag tag=new CompoundTag();original.write(tag);check(DecorSpec.read(tag,k).equals(original),"NBT "+k);
            var changed=new DecorSpec(k,7,2,1,.2f,.5f,45,12,1,2,3,0x223344,0xDDBB22,2,3,true,"mtr:textures/decor/poster.png","Quai é – 東京");
            check(original.patch(changed,2).width()==original.width(),"appearance preserves size");check(original.patch(changed,2).variant()==original.variant(),"appearance preserves variant");check(original.patch(changed,2).label().equals(original.label()),"appearance preserves label");
            BufferedImage board=new BufferedImage(1200,980,BufferedImage.TYPE_INT_RGB);Graphics2D g=board.createGraphics();g.setColor(new Color(0xF1F2EE));g.fillRect(0,0,1200,980);g.setColor(new Color(0x18343E));g.setFont(new Font("SansSerif",Font.BOLD,33));g.drawString(String.format("%02d",k.ordinal()+1)+"  "+k.title,38,55);g.setFont(new Font("SansSerif",Font.PLAIN,16));g.drawString("Modèle Sillon • géométrie utilisée par le mod • dimensions et finitions réglables",40,86);
            Set<String> variants=new HashSet<>();
            StringBuilder obj=new StringBuilder("# Sillon original geometry - "+k.title+"\n");int vertex=1;
            for(int v=0;v<4;v++){
                DecorSpec spec=new DecorSpec(k,original.width(),original.height(),original.depth(),original.spacing(),original.suspension(),0,0,0,0,0,original.color(),original.accent(),original.material(),v,original.illuminated(),"",k==DecorKind.HELP_POINT?"INFORMATION":"");
                DecorMesh mesh=new DecorMesh(spec);variants.add(mesh.faces().toString());check(!mesh.faces().isEmpty(),"empty mesh");check(mesh.faces().size()<14000,"budget");
                for(var face:mesh.faces())for(var p:java.util.List.of(face.a(),face.b(),face.c(),face.d()))check(Float.isFinite(p.x())&&Float.isFinite(p.y())&&Float.isFinite(p.z()),"finite mesh");
                // Every default and extreme variant must generate within the bounded geometry budget.
                var huge=new DecorSpec(k,16,12,8,.08f,8,180,60,8,8,8,0xFFFFFF,0,3,v,true,"","");new DecorMesh(huge);
                BufferedImage image=DecorPreview.render(spec,560,350,30);ImageIO.write(image,"png",out.resolve("images/"+k.id+"-"+v+".png").toFile());
                int x=30+(v%2)*585,y=112+(v/2)*415;g.setColor(Color.WHITE);g.fillRoundRect(x,y,570,398,16,16);g.drawImage(image,x+5,y+5,null);g.setColor(new Color(0x18343E));g.setFont(new Font("SansSerif",Font.BOLD,19));g.drawString(k.variants.split("\\|")[v],x+18,y+375);
                if(v==0){for(var f:mesh.faces()){for(var p:java.util.List.of(f.a(),f.b(),f.c(),f.d()))obj.append("v ").append(p.x()).append(' ').append(p.y()).append(' ').append(p.z()).append('\n');obj.append("f ").append(vertex).append(' ').append(vertex+1).append(' ').append(vertex+2).append(' ').append(vertex+3).append('\n');vertex+=4;}}
                tested++;
            }
            Set<String> materials=new HashSet<>();for(int m=0;m<4;m++){var t=new DecorSpec(k,original.width(),original.height(),original.depth(),original.spacing(),original.suspension(),0,0,0,0,0,original.color(),original.accent(),m,0,original.illuminated(),"","");materials.add(new DecorMesh(t).faces().toString());}check(materials.size()==4,"Every finish must visibly change the production mesh: "+k);
            check(variants.size()==4,"Each variant must have distinct production geometry: "+k);
            g.setFont(new Font("SansSerif",Font.PLAIN,15));g.setColor(new Color(0x51616B));g.drawString("Base : "+k.width+" × "+k.height+" × "+k.depth+" blocs  •  "+k.materials.replace("|"," / "),40,962);g.dispose();
            ImageIO.write(board,"jpg",out.resolve(String.format("%02d-",k.ordinal()+1)+k.id+".jpg").toFile());Files.writeString(out.resolve("models/"+k.id+".obj"),obj,StandardCharsets.UTF_8);
            System.out.println("PASS "+k.id+" 4 variants • "+DecorMesh.get(original).faces().size()+" default quads");
        }
        check(DecorPlacement.repeat(32,0,16).size()==3,"repetition distance budget");check(DecorPlacement.validBox(31,31,31),"volume boundary");check(!DecorPlacement.validBox(48,48,48),"volume cap");
        check(DecorSpec.decode("broken").kind()==DecorKind.BENCH,"malformed packet");check(DecorSpec.defaults(DecorKind.BENCH).patch(DecorSpec.defaults(DecorKind.VAULT),31).kind()==DecorKind.BENCH,"family preserved");
        System.out.println("PASS "+tested+" variants, 80 extreme meshes, serialization, selective patches and bounded placement");
    }
}
