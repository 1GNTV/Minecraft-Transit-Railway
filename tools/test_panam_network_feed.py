"""Run isolated production announcement/compositor checks; no Gradle, mod JAR or game.

Java's source launcher keeps helper compilation in memory. All tested models,
rotation and raster code are copied verbatim from the production source files.
"""
from pathlib import Path
import re,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
HARNESS=r'''
public class PanamNetworkFeedCheck {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static void rotation(List<PanamNotice> input){
  var feed=new PanamNoticeFeed();var timeline=new PanamFrame.Timeline();
  List<PanamNotice> previous=List.of();Set<PanamNotice> seen=new HashSet<>();
  for(long now=0;now<3600000;now+=500){
   var page=feed.window(input,now);check(!page.isEmpty()&&page.size()<=8,"bounded visible atlas window");
   if(!page.equals(previous)){timeline.restartNotices(now);previous=page;}
   timeline.notices(page.stream().map(n->n.priority).toList(),now);
   seen.add(page.get(timeline.noticeIndex));
  }
  for(PanamNotice n:input)check(seen.contains(n),"starved network notice "+n.line+":"+n.message);
 }
 public static void main(String[] args){
  var many=new ArrayList<PanamNotice>();
  for(int i=0;i<19;i++)many.add(new PanamNotice("L"+i,i*977,"Network message "+i,0));
  for(int i=0;i<6;i++)many.add(new PanamNotice("12",0x00805A,"Current-line danger "+i,3));
  rotation(many);rotation(many.subList(0,19));rotation(many.subList(19,25));
  var mixed=new ArrayList<PanamNotice>();
  for(int i=0;i<11;i++)mixed.add(new PanamNotice("12",0x00805A,"Works "+i,1));
  mixed.add(new PanamNotice("12",0x00805A,"Danger",3));rotation(mixed);
  var feed=new PanamNoticeFeed();
  var same=new PanamNotice("10",0xFFBE00,"Same wording",0);
  var other=new PanamNotice("12",0x00805A,"Same wording",0);
  check(feed.window(List.of(same,other),0).size()==2,"identical text on different lines keeps both badges");
  check(feed.window(List.of(same,same.withPriority(3)),0).equals(List.of(same.withPriority(3))),"dedup retains strongest priority");
  check(feed.window(List.of(),0).isEmpty(),"clear feed");
  PanamNewsClient.update("Global",new long[]{10,12},new String[]{"Other-line bulletin","Local-line bulletin"});
  check(PanamNewsClient.routes().size()==2,"full network snapshot");
  boolean immutable=false;try{PanamNewsClient.routes().clear();}catch(UnsupportedOperationException e){immutable=true;}
  check(immutable,"network snapshot must be immutable");
  String text=PanamBulletins.marked(new PanamBulletins.Bulletin(PanamBulletins.INFO,"Traffic disrupted"));
  var base=new PanamSurface.Spec("12","Mairie d’Issy",0x00805A,List.of("Mairie d’Issy"),List.of(text),false,false);
  var line10=base.withNoticeSources(List.of(new PanamNotice("10",0xFFBE00,text,0)));
  check(!base.equals(line10),"badge metadata must invalidate atlas cache");
  check(line10.line.equals("12")&&line10.color==0x00805A,"network news must not replace departure line/header");
  check(line10.withViewport(new PanamViewport(PanamScreenSettings.DEFAULT)).noticeSources.equals(line10.noticeSources),"adaptive screen keeps source badges");
  java.awt.Font normal=new java.awt.Font(java.awt.Font.SANS_SERIF,java.awt.Font.PLAIN,16);
  var surface=new PanamSurface(line10,normal,normal.deriveFont(java.awt.Font.BOLD));
  var icon=surface.noticeIcons[0];
  check((surface.image.getRGB(icon.x+76,icon.y+44)&0xFFFFFF)==0xFFBE00,"notice badge uses line 10 yellow, not platform line 12 green");
  surface.image.flush();
  var global=new PanamSurface(base.withNoticeSources(List.of(new PanamNotice("",0x808080,text,0))),normal,normal.deriveFont(java.awt.Font.BOLD));
  icon=global.noticeIcons[0];check((global.image.getRGB(icon.x+76,icon.y+44)>>>24)==0,"global bulletin must not claim a particular line");global.image.flush();
  System.out.println("PASS: full network rotation >8 notices, priorities, per-line badges, immutable snapshots and actual badge pixels");
 }
}
'''
sources=[ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel'/name for name in (
 'PanamNotice.java','PanamNoticeFeed.java','PanamSurface.java','PanamViewport.java',
 'PanamLayout.java','PanamFrame.java','PanamAnimation.java','PanamAdaptiveFrame.java')]
sources += [ROOT/'fabric/src/main/java/org/mtr/mod/data'/name for name in ('PanamScreenSettings.java','PanamBulletins.java','PanamNewsClient.java')]
imports={'import java.util.List;'};bodies=[]
for path in sources:
 text=path.read_text()
 imports.update(i for i in re.findall(r'^import [^;]+;',text,re.M) if not i.startswith('import org.mtr.'))
 text=re.sub(r'^package [^;]+;\s*','',text,flags=re.M)
 text=re.sub(r'^import [^;]+;\s*','',text,flags=re.M)
 text=re.sub(r'^public (final class|class) ',r'\1 ',text,flags=re.M)
 bodies.append(text)
with tempfile.TemporaryDirectory(prefix='panam-network-check-') as tmp:
 path=Path(tmp)/'PanamNetworkFeedCheck.java'
 path.write_text('\n'.join(sorted(imports))+'\n'+HARNESS+'\n'+'\n'.join(bodies))
 subprocess.run(['java','-Djava.awt.headless=true',str(path)],check=True)
