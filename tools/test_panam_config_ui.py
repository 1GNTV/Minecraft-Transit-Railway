"""Exercise the actual brush screen and numeric rules against lightweight widgets.

No Gradle, Minecraft, native GUI or mod build. Java source launcher keeps the
isolated helper in memory. Stub widgets model text scroll and record geometry;
this is a state/layout regression check, not an in-game rendering test.
"""
from pathlib import Path
import re, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
STUBS=r'''
interface IGui {int SQUARE_SIZE=20,ARGB_WHITE=0xFFFFFFFF;}
class ScreenExtension {
 int width=854,height=270,closed;List<ClickableWidget> children=new ArrayList<>();
 protected void init2(){children.clear();}public void tick2(){}public void render(GraphicsHolder h,int x,int y,float d){}
 public void renderBackground(GraphicsHolder h){}public void onClose2(){closed++;}public boolean isPauseScreen2(){return false;}
 void addChild(ClickableWidget w){children.add(w);}
}
class Widget {int x,y,w,h;boolean visible=true,active=true;String label="";
 void setX2(int v){x=v;}void setY2(int v){y=v;}void setWidth2(int v){w=v;}
 void setActiveMapped(boolean v){active=v;}void setMessage2(Text t){label=t.data;}
 boolean isHovered(){return false;}
}
class ButtonWidgetExtension extends Widget {
 java.util.function.Consumer<ButtonWidgetExtension> action;
 ButtonWidgetExtension(int x,int y,int w,int h,java.util.function.Consumer<ButtonWidgetExtension> a){this.x=x;this.y=y;this.w=w;this.h=h;action=a;}
 void click(){if(active&&visible)action.accept(this);}
}
class CheckboxWidgetExtension extends Widget {boolean checked;
 CheckboxWidgetExtension(int x,int y,int w,int h,boolean ignored,java.util.function.Consumer<Boolean> a){this.x=x;this.y=y;this.w=w;this.h=h;}
 boolean isChecked2(){return checked;}void setChecked(boolean b){checked=b;}
}
class TextFieldWidgetExtension extends Widget {
 String value="",filter;boolean scrolled,focused;
 TextFieldWidgetExtension(int x,int y,int w,int h,int max,TextCase c,String f,String suggestion){this.x=x;this.y=y;this.w=w;this.h=h;filter=f;}
 void setText2(String s){value=filter==null?s:s.replaceAll(filter,"");scrolled=w<=8;}
 String getText2(){return value;}String paintedText(){return scrolled?"":value;}
 void setSelectionEnd2(int n){if(n==0)scrolled=false;}void setSelectionStart2(int n){if(n==0)scrolled=false;}
 void setTextFieldFocused2(boolean b){focused=b;}boolean isFocused2(){return focused;}
 void setEditableColor2(int c){}void tick2(){}
}
class Text {String data;Text(String s){data=s;}}
class TextHelper {static Text literal(String s){return new Text(s);}}
enum TextCase {DEFAULT}
class ClickableWidget {Widget widget;ClickableWidget(Widget w){widget=w;}}
class BlockPos {}
class LongAVLTreeSet extends TreeSet<Long>{LongAVLTreeSet(Collection<Long> c){super(c);}}
class BlockPanamDisplayEntity {
 Set<Long> getPlatformIds(){return Set.of(11L);}int getDisplayPage(){return 2;}
 String getMessage(int i){return i==0?"Travaux|[panam:last]":"";}boolean getHideArrival(int i){return i==2;}
}
class BlockPANAMScreen {static class BlockEntity extends BlockPanamDisplayEntity {
 PanamScreenSettings settings(){return new PanamScreenSettings(.5f,1,-.25f,30,-5,10,3,1.25f,1536,768,1.2f);}
}}
class PIDSConfigScreen {
 static ButtonWidgetExtension getPlatformFilterButton(BlockPos p,CheckboxWidgetExtension c,LongAVLTreeSet s,ScreenExtension owner){return new ButtonWidgetExtension(0,0,0,20,b->{});}
}
class IDrawing {static void setPositionAndWidth(Widget w,int x,int y,int width){w.x=x;w.y=y;w.w=width;}}
class GraphicsHolder {
 int width,height;GraphicsHolder(int w,int h){width=w;height=h;}
 static int getTextWidth(String s){return s.length()*6;}static int getDefaultLight(){return 0;}
 void drawText(String s,int x,int y,int c,boolean b,int light){
  if(x<0||y<0||x+getTextWidth(s)>width||y+9>height)throw new AssertionError("label outside screen: "+s);
 }
}
class GuiDrawing {GuiDrawing(GraphicsHolder g){}void beginDrawingRectangle(){}void drawRectangle(double a,double b,double c,double d,int colour){}void finishDrawingRectangle(){}}
class InitClient {static final Registry REGISTRY_CLIENT=new Registry();static class Registry {List<Object> packets=new ArrayList<>();void sendPacketToServer(Object p){packets.add(p);}}}
class PacketUpdatePIDSConfig {
 String[] messages;boolean[] hidden;int page;
 PacketUpdatePIDSConfig(BlockPos p,String[] m,boolean[] h,LongAVLTreeSet platforms,int n){messages=m.clone();hidden=h.clone();page=n;}
}
class PacketUpdatePanamScreen {PanamScreenSettings settings;PacketUpdatePanamScreen(BlockPos p,PanamScreenSettings s){settings=s;}}
'''
HARNESS=r'''
public class PanamConfigUiCheck {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static Object field(Object o,String name)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static ButtonWidgetExtension button(PanamConfigScreen s,String n)throws Exception{return (ButtonWidgetExtension)field(s,n);}
 static TextFieldWidgetExtension[] inputs(PanamConfigScreen s)throws Exception{return (TextFieldWidgetExtension[])field(s,"displayFields");}
 static PanamConfigScreen open(boolean screen,int w,int h){
  var s=new PanamConfigScreen(new BlockPos(),screen?new BlockPANAMScreen.BlockEntity():new BlockPanamDisplayEntity());
  s.width=w;s.height=h;s.init2();return s;
 }
 static void layout(PanamConfigScreen s){
  s.render(new GraphicsHolder(s.width,s.height),0,0,0);
  var visible=s.children.stream().map(c->c.widget).filter(w->w.visible).toList();
  for(var a:visible){
   check(a.w>0&&a.x>=0&&a.y>=0&&a.x+a.w<=s.width&&a.y+a.h<=s.height,"widget bounds: "+a.label);
   for(var b:visible)if(a!=b)check(!(a.x<b.x+b.w&&b.x<a.x+a.w&&a.y<b.y+b.h&&b.y<a.y+a.h),"overlapping controls: "+a.label+" / "+b.label);
  }
 }
 public static void main(String[] ignored)throws Exception {
  for(int w:new int[]{320,426,854,1600})for(int h:new int[]{240,270,480})for(boolean only:new boolean[]{true,false}){
   var s=open(only,w,h);layout(s);
   if(only){
    var f=inputs(s);
    String[] expected=PanamDisplayForm.values(new BlockPANAMScreen.BlockEntity().settings());
    for(int i=0;i<11;i++)check(f[i].paintedText().equals(expected[i]),"initial field invisible/wrong: "+i);
    f[6].setText2("4,5");button(s,"positionTab").click();layout(s);button(s,"sizeTab").click();
    check(f[6].getText2().equals("4,5"),"tab switch resets value");
    s.width=w+10;s.init2();layout(s);check(f[6].paintedText().equals("4,5"),"resize loses/hides edits");
   }
   button(s,"tabArrivals").click();layout(s);button(s,"tabNews").click();layout(s);
  }
  InitClient.REGISTRY_CLIENT.packets.clear();
  var s=open(true,854,270);var f=inputs(s);
  f[7].setText2("2,5");button(s,"saveButton").click();
  check(s.closed==1&&InitClient.REGISTRY_CLIENT.packets.size()==2,"save must send both settings packets once");
  var p=(PacketUpdatePanamScreen)InitClient.REGISTRY_CLIENT.packets.get(1);
  check(p.settings.height==2.5f&&p.settings.resolutionWidth==1536&&p.settings.yaw==30,"save lost settings");
  var local=(PacketUpdatePIDSConfig)InitClient.REGISTRY_CLIENT.packets.get(0);
  check(local.page==2&&local.hidden[2]&&local.messages[0].contains("[panam:last]"),"local state lost");
  s.onClose2();check(InitClient.REGISTRY_CLIENT.packets.size()==2,"double save");
  InitClient.REGISTRY_CLIENT.packets.clear();s=open(true,854,270);f=inputs(s);f[6].setText2("-");
  s.render(new GraphicsHolder(s.width,s.height),0,0,0);check(!button(s,"saveButton").active,"invalid save enabled");
  s.onClose2();check(s.closed==0&&InitClient.REGISTRY_CLIENT.packets.isEmpty(),"Escape saved invalid values");
  button(s,"cancelButton").click();check(s.closed==1&&InitClient.REGISTRY_CLIENT.packets.isEmpty(),"cancel sent settings");
  s=open(true,854,270);button(s,"resetDisplay").click();
  check(Arrays.equals(Arrays.stream(inputs(s)).map(TextFieldWidgetExtension::getText2).toArray(String[]::new),PanamDisplayForm.values(PanamScreenSettings.DEFAULT)),"reset defaults");
  for(String bad:new String[]{"","-",".","NaN","Infinity","1..2","17"})check(!PanamDisplayForm.valid(0,bad),"invalid position accepted "+bad);
  check(!PanamDisplayForm.valid(9,"512.5")&&!PanamDisplayForm.valid(9,"2049"),"invalid resolution accepted");
  check(PanamDisplayForm.valid(7,"0,25")&&PanamDisplayForm.valid(8,"3"),"valid boundary rejected");
  check(PanamDisplayForm.adjust(6,"16",1).equals("16"),"upper step overflow");
  check(PanamDisplayForm.adjust(8,"0.5",-1).equals("0.5"),"lower step overflow");
  String zoom="1";for(int i=0;i<10;i++)zoom=PanamDisplayForm.adjust(8,zoom,1);check(zoom.equals("2"),"zoom float noise");
  System.out.println("PASS: actual GUI initialization, tabs, 24 layouts, resize, save/cancel/reset, validation and numeric stepping (stub widgets; no game)");
 }
}
'''
paths=[ROOT/'fabric/src/main/java/org/mtr/mod/screen'/name for name in ('PanamConfigScreen.java','PanamDisplayForm.java')]
paths.append(ROOT/'fabric/src/main/java/org/mtr/mod/data/PanamScreenSettings.java')
imports=set();bodies=[]
for path in paths:
 text=path.read_text()
 imports.update(i for i in re.findall(r'^import [^;]+;',text,re.M) if not i.startswith('import org.mtr.'))
 text=re.sub(r'^package [^;]+;\s*','',text,flags=re.M)
 text=re.sub(r'^import [^;]+;\s*','',text,flags=re.M)
 text=re.sub(r'^public (final class|class) ',r'\1 ',text,flags=re.M)
 bodies.append(text)
with tempfile.TemporaryDirectory(prefix='panam-ui-check-') as tmp:
 path=Path(tmp)/'PanamConfigUiCheck.java'
 path.write_text('import java.util.*;\n'+'\n'.join(sorted(imports))+'\n'+HARNESS+'\n'+STUBS+'\n'+'\n'.join(bodies))
 subprocess.run(['java','-Djava.awt.headless=true',str(path)],check=True)
