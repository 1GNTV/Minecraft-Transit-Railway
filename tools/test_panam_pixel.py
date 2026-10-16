"""Run the production compositor/timeline, save visual evidence, and measure CPU work (never FPS)."""
from pathlib import Path
import subprocess, tempfile, json, math, os
ROOT=Path(__file__).resolve().parents[1]
OUT=Path(os.environ.get('PANAM_TEST_OUTPUT',ROOT/'build/panam-verification'))
OUT.mkdir(parents=True,exist_ok=True)
HARNESS=r'''
import org.mtr.mod.render.pixel.*;
import java.awt.*;import java.awt.image.*;import java.io.*;import java.util.*;import javax.imageio.ImageIO;
public class PixelOracle {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static BufferedImage render(PanamSurface s,PanamFrame.Timeline t,long at) {
  BufferedImage img=PanamSurface.blank(1024,466);Graphics2D g=PanamSurface.graphics(img);
  PanamFrame.draw(s,t,at,20,19,(p,x,y,w,h,alpha,cx,cy,cw,ch)->{
   check(p.x>=0&&p.y>=0&&p.x+p.w<=s.atlasWidth&&p.y+p.h<=s.atlasHeight,"atlas bounds");
   Shape old=g.getClip();g.clip(new java.awt.geom.Rectangle2D.Float(cx,cy,cw,ch));
   g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));
   java.awt.geom.AffineTransform tr=new java.awt.geom.AffineTransform();tr.translate(x,y);tr.scale(w/p.w,h/p.h);
   g.drawImage(s.image.getSubimage(p.x,p.y,p.w,p.h),tr,null);g.setClip(old);
  });g.dispose();return img;
 }
 public static void main(String[] args)throws Exception {
  Font regular=Font.createFont(Font.TRUETYPE_FONT,new File(args[0])),bold=Font.createFont(Font.TRUETYPE_FONT,new File(args[1]));File out=new File(args[2]);
  check(PanamAnimation.zoom(0)==1.4f&&PanamAnimation.zoom(2000)==1,"zoom endpoints");
  check(PanamAnimation.departure(0)==0&&PanamAnimation.departure(1500)==1,"transition endpoints");
  check(Math.abs(PanamAnimation.zoom(775)-1.0665)<.0001,"reference bezier midpoint at x=.3875, y=.83375");
  check(PanamAnimation.colon(700)==.5f&&PanamAnimation.number(450)<1,"blink values");
  long now=100000;
  PanamFrame.Departure a=new PanamFrame.Departure("a","Aéroport d’Orly",now+30000,true),b=new PanamFrame.Departure("b","Aéroport d’Orly",now+150000,true);
  PanamFrame.Timeline t=new PanamFrame.Timeline();t.update(new PanamFrame.Departure[]{a,b},now);t.noticeAt=now-2000;
  PanamSurface.Spec spec=new PanamSurface.Spec("14","Aéroport d’Orly",0x650080,List.of(a.destination,b.destination),List.of("Informations en temps réel indisponibles"),false,false);
  PanamSurface s=new PanamSurface(spec,regular,bold);
  BufferedImage steady=render(s,t,now+3000);ImageIO.write(steady,"png",new File(out,"panam-normal.png"));
  check((steady.getRGB(500,10)&0xFFFFFF)==0xFFFFFF,"white header");check((steady.getRGB(515,460)&0xFFFFFF)==0x650080,"line colour footer under notice panel");
  check((steady.getRGB(500,450)&0xFFFFFF)==0x650080,"line colour footer under departure zone");
  long z=t.zoomAt[0];t.update(new PanamFrame.Departure[]{new PanamFrame.Departure("a",a.destination,now+32000,true),b},now+4000);check(t.changedAt==now,"ETA corrections keep train identity");check(t.zoomAt[0]==z,"unchanged minute never restarts zoom");
  t.update(new PanamFrame.Departure[]{b,new PanamFrame.Departure("c",a.destination,now+300000,true)},now+5000);
  check(t.previous[0].id.equals("a")&&t.current[0].id.equals("b"),"departure state retained");
  for(int i=0;i<4;i++)ImageIO.write(render(s,t,now+5000+i*500),"png",new File(out,"panam-transition-"+i+".png"));
  t.notices(3,now+28000);check(t.noticeIndex==0&&t.noticeAt==now+28000,"10s info rotation catches up");
  String[] modes={"unavailable","mixed","last","suspended","eight-notices","eight-disruptions"};
  for(String mode:modes){
   java.util.List<String> ds=mode.equals("unavailable")?List.of():mode.equals("mixed")?List.of("Saint-Denis Pleyel","Aéroport d’Orly","Saint-Denis Pleyel"):mode.equals("last")?List.of("Aéroport d’Orly"):List.of(a.destination,b.destination);
   java.util.List<String> ns=mode.equals("eight-disruptions")?List.of(
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(1,"Travaux un")),
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(2,"Danger deux")),
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(1,"Travaux trois")),
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(2,"Danger quatre")),
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(1,"Travaux cinq")),
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(2,"Danger six")),
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(1,"Travaux sept")),
     org.mtr.mod.data.PanamBulletins.marked(new org.mtr.mod.data.PanamBulletins.Bulletin(2,"Danger huit")))
   :mode.equals("eight-notices")?List.of("Un","Deux","Trois","Quatre","Cinq","Six","Sept","Huit"):List.of("Informations en temps réel indisponibles");
   PanamSurface surface=new PanamSurface(new PanamSurface.Spec("14","Aéroport d’Orly",0x650080,ds,ns,mode.equals("suspended"),mode.equals("last")),regular,bold);
   PanamFrame.Timeline mt=new PanamFrame.Timeline();PanamFrame.Departure[] dep=new PanamFrame.Departure[ds.size()];for(int i=0;i<dep.length;i++)dep[i]=new PanamFrame.Departure("x"+i,ds.get(i),now+i*120000+50000,true);mt.update(dep,now);mt.noticeAt=now-2000;
   ImageIO.write(render(surface,mt,now+3000),"png",new File(out,"panam-"+mode+".png"));surface.image.flush();
  }
  // Warm up the actual quad planner; no AWT rasterization inside the measured loop.
  final long[] quads={0};PanamFrame.Emitter count=(p,x,y,w,h,alpha,cx,cy,cw,ch)->{if(w>0&&h>0)quads[0]++;};
  for(int i=0;i<20000;i++)PanamFrame.draw(s,t,now+10000+i,20,19,count);
  int frames=100000;quads[0]=0;long start=System.nanoTime();for(int i=0;i<frames;i++)PanamFrame.draw(s,t,now+10000+i,20,19,count);long duration=System.nanoTime()-start;
  start=System.nanoTime();PanamSurface fresh=new PanamSurface(spec,regular,bold);long build=System.nanoTime()-start;
  String result="{\"frames\":"+frames+",\"ns_per_screen_face\":"+(duration/(double)frames)+",\"quads_per_face\":"+(quads[0]/(double)frames)+",\"compositor_ns\":"+build+",\"atlas_bytes\":"+((long)s.atlasWidth*(long)s.atlasHeight*4)+",\"renderer\":\"CPU quad planner; no Minecraft FPS measurement\"}";
  java.nio.file.Files.writeString(new File(out,"pixel-profile.json").toPath(),result);System.out.println(result);System.out.println("PASS: production pixels, packing, states, ETA identity, timings, and bounded quad work");
 }
}
'''.replace('List.of','java.util.List.of')
with tempfile.TemporaryDirectory(prefix='pixel-test-') as tmp:
    target=Path(tmp);(target/'PixelOracle.java').write_text(HARNESS,encoding='utf-8')
    sources=[ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel'/f for f in ('PanamAnimation.java','PanamSurface.java','PanamFrame.java')]
    sources.append(ROOT/'fabric/src/main/java/org/mtr/mod/data/PanamBulletins.java')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(target),*map(str,sources),str(target/'PixelOracle.java')],check=True)
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(target),'PixelOracle',str(ROOT/'fabric/src/main/resources/assets/mtr/font/panam/noto-sans-regular.ttf'),str(ROOT/'fabric/src/main/resources/assets/mtr/font/noto-sans-semibold.ttf'),str(OUT)],check=True)
model=json.loads((ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json').read_text())
states=json.loads((ROOT/'fabric/src/main/resources/assets/mtr/blockstates/panam_pids.json').read_text())['variants']
assert len(states)==4
assert len(model['elements'])==6, 'two compact support rods plus casing'
assert all(0<=e['from'][1]<e['to'][1]<=16 for e in model['elements'])
assert all(e['to'][1]<=16 and e['from'][1]>=0 for e in model['elements'])
# Evaluate the renderer's actual rotation/translation convention against the baked casing.
for facing,angle,step in [('north',180,(0,-1)),('east',270,(1,0))]:
    for side in (0,1):
        yaw=math.radians(90-angle+side*180);center=(.5+step[0]/2,.5+step[1]/2)
        points=[]
        for x,y in [(0,0),(1024,0),(1024,466),(0,466)]:
            lx=-(-1.95/2+x*1.95/1024);ly=-( -12.45/16+y*(11.75/16)/466 );lz=-.1345
            points.append((center[0]+math.cos(yaw)*lx+math.sin(yaw)*lz,ly,center[1]-math.sin(yaw)*lx+math.cos(yaw)*lz))
        assert abs(max(p[1] for p in points)-12.45/16)<1e-6
        assert abs(min(p[1] for p in points)-.70/16)<1e-6
        flat=[p[0] if facing=='north' else p[2] for p in points]
        assert max(flat)-min(flat)<1e-6
        assert all(abs(v-.5)>.13125 for v in flat),'screen must be in front of the casing'
# Both the body and bottom metal lip must extend with the artwork.
assert abs(model['elements'][0]['from'][1]-.30)<1e-6
assert abs(model['elements'][1]['from'][1]-.30)<1e-6
assert abs(model['elements'][1]['to'][1]-.70)<1e-6
assert abs(model['elements'][2]['from'][1]-12.45)<1e-6
assert 0 < .30 < .70 < 12.45 < 12.85 < 16
assert abs((12.45-.70)-11.75)<1e-6
assert abs((11.75/11.30)-1)>.03 and abs((11.75/11.30)-1)<.05
print('PASS: both faces fitted to the 4% taller casing; bottom lip and screen flush; all within one block')
