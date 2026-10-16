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
  BufferedImage img=PanamSurface.blank(s.layout==null?1024:s.layout.width,s.layout==null?466:s.layout.height);Graphics2D g=PanamSurface.graphics(img);
  PanamFrame.draw(s,t,at,20,19,(p,x,y,w,h,alpha,cx,cy,cw,ch)->{
   check(p.x>=0&&p.y>=0&&p.x+p.w<=s.atlasWidth&&p.y+p.h<=s.atlasHeight,"atlas bounds");
   Shape old=g.getClip();g.clip(new java.awt.geom.Rectangle2D.Float(cx,cy,cw,ch));
   g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));
   java.awt.geom.AffineTransform tr=new java.awt.geom.AffineTransform();tr.translate(x,y);tr.scale(w/p.w,h/p.h);
   g.clip(new java.awt.geom.Rectangle2D.Float(x,y,w,h));tr.translate(-p.x,-p.y);tr.scale(s.atlasWidth/s.rasterWidth,s.atlasHeight/s.rasterHeight);
   g.drawImage(s.image,tr,null);g.setClip(old);
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
  t.notices(3,now+28000);check(t.noticeIndex==0&&t.noticeAt==now+28000,"ticker initialises without skipping");
  // Important line danger repeats between network bulletins, holds for 12s,
  // and ordinary bulletins keep their own short dwell and fair cycling.
  PanamFrame.Timeline prioritized=new PanamFrame.Timeline();
  prioritized.notices(List.of(0,3,0),now);
  check(prioritized.noticeIndex==1,"critical line bulletin displayed first");
  prioritized.notices(List.of(0,3,0),now+11999);
  check(prioritized.noticeIndex==1,"12-second critical dwell");
  prioritized.notices(List.of(0,3,0),now+12000);
  check(prioritized.noticeIndex==0,"network bulletin is never starved");
  prioritized.notices(List.of(0,3,0),now+16500);
  check(prioritized.noticeIndex==1,"return to critical incident between network bulletins");
  prioritized.notices(List.of(0,3,0),now+28500);
  check(prioritized.noticeIndex==2,"network bulletin round-robin progresses");
  PanamFrame.Timeline works=new PanamFrame.Timeline();
  works.notices(List.of(0,1,0),now);
  check(works.noticeIndex==0,"scheduled works must not override network as major danger");
  works.notices(List.of(0,1,0),now+4500);
  check(works.noticeIndex==1,"works still cycles normally");
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
  // Exercise actual adaptive packing, raster density and clipped quad output.
  float[][] viewports={{1.95f,.821875f,1,1024,512},{1,2,1,512,1024},{1,1,.5f,2048,2048},{8,.25f,3,2048,128},{.25f,8,3,128,2048},{3,1,2,128,128}};
  for(int v=0;v<viewports.length;v++){
   float[] q=viewports[v];
   var settings=new org.mtr.mod.data.PanamScreenSettings(0,0,0,0,0,0,q[0],q[1],(int)q[3],(int)q[4],q[2]);
   var viewport=new PanamViewport(settings);
   var adaptive=new PanamSurface(new PanamSurface.Spec("14",a.destination,0x650080,List.of(a.destination,b.destination),java.util.Collections.nCopies(8,"Information voyageurs. ".repeat(20)),false,false).withViewport(viewport),regular,bold);
   check(adaptive.rasterWidth<=4096&&adaptive.rasterHeight<=4096,"bounded raster axes");
   check((long)adaptive.rasterWidth*adaptive.rasterHeight<=4200000,"bounded raster area");
   check(adaptive.layout.columns==(viewport.height<viewport.width*.6f),"responsive columns breakpoint");
   check(adaptive.layout.header==0||viewport.height>=viewport.width*.4f,"responsive header breakpoint");
   PanamFrame.draw(adaptive,t,now+20000,20,19,(p,x,y,w,h,alpha,cx,cy,cw,ch)->{
    check(Float.isFinite(x+y+w+h+alpha+cx+cy+cw+ch)&&w>0&&h>0,"finite positive adaptive quads");
    check(p.x>=0&&p.y>=0&&p.x+p.w<=adaptive.atlasWidth&&p.y+p.h<=adaptive.atlasHeight,"adaptive atlas bounds");
   });
   ImageIO.write(render(adaptive,t,now+20000),"png",new File(out,"panam-adaptive-"+v+".png"));adaptive.image.flush();
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
    sources=[ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel'/f for f in ('PanamAnimation.java','PanamSurface.java','PanamFrame.java','PanamAdaptiveFrame.java','PanamLayout.java','PanamViewport.java','PanamNotice.java','PanamNoticeFeed.java')]
    sources.append(ROOT/'fabric/src/main/java/org/mtr/mod/data/PanamBulletins.java')
    sources.append(ROOT/'fabric/src/main/java/org/mtr/mod/data/PanamScreenSettings.java')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(target),*map(str,sources),str(target/'PixelOracle.java')],check=True)
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(target),'PixelOracle',str(ROOT/'fabric/src/main/resources/assets/mtr/font/panam/noto-sans-regular.ttf'),str(ROOT/'fabric/src/main/resources/assets/mtr/font/noto-sans-semibold.ttf'),str(OUT)],check=True)
# Validate the SOLIS casing against the actual photographed silhouette.
model=json.loads((ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json').read_text())
states=json.loads((ROOT/'fabric/src/main/resources/assets/mtr/blockstates/panam_pids.json').read_text())['variants']
renderer=(ROOT/'fabric/src/main/java/org/mtr/mod/render/RenderPANAMPIDS.java').read_text()
surface=(ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel/PanamSurface.java').read_text()
assert len(states)==4, 'two facing directions on a two-block horizontal panel'
assert len(model['elements'])>=14, 'distinct spine, double display, bottom lip and rods'
assert model['ambientocclusion'] is False
assert all(0<=e['from'][0]<e['to'][0]<=16 and 0<=e['from'][2]<e['to'][2]<=16 for e in model['elements'])
assert all(-1<=e['from'][1]<e['to'][1]<=16 for e in model['elements']), 'projecting chin stays within model bake limits'
# The actual LCD raster remains exactly two blocks wide and ~12% taller,
# lowered 0.4 model pixels beneath the original panel origin.
assert 'WORLD_WIDTH = 1.95f' in surface
assert 'WORLD_HEIGHT = 13.15f / 16' in surface
assert 'TOP = 12.75f / 16' in surface
assert abs(12.75-13.15+.4)<1e-6
assert 'g.rotateXDegrees(2.6f)' in renderer
assert 'g.translate(0,-PanamSurface.TOP,0)' in renderer
assert 'g.translate(-PanamSurface.WORLD_WIDTH/2,0,-.164f)' in renderer
assert sum('suspension' in x.get('comment','').lower() or 'ceiling anchor' in x.get('comment','').lower() for x in model['elements'])>=2
assert sum('lower lip' in x.get('comment','').lower() or 'lip carrier' in x.get('comment','').lower() for x in model['elements'])>=2
assert any(x['from'][1]<0 for x in model['elements']), 'lower lip hangs below baseline'
assert 'noticePriorities' in renderer and 'panel.noticeFeed.window(candidates,now)' in renderer
print('PASS: SOLIS 2-block PANAM, stepped tilt shell, 2.6-degree actual screen tilt, suspension and critical-cycle timing')
