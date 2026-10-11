"""Pass boundaries, dense-region stability and projection error safety using production helpers."""
from pathlib import Path
import shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
TEST=r'''import java.util.*;import org.mtr.mod.render.*;
public class FpsFixesTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  var pages=new RegionPageLayout<String>(16000);
  check(pages.page("station-a",9000)==0,"first page");check(pages.page("station-b",9000)==1,"dense region must split");
  check(pages.page("station-c",6000)==0,"fill first page");for(int i=0;i<100;i++){check(pages.page("station-b",9000)==1,"visibility must not reshuffle pages");check(pages.page("station-a",9000)==0,"subset retains membership");}
  check(pages.page("station-a",16000)==2,"changed geometry moves without overflow");check(pages.page("station-d",9000)==0,"released contribution reused");
  boolean rejected=false;try{pages.page("oversize",16001);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"oversize fragments must never overrun a page");
  check(!ScreenErrorBudget.permits(.125,100,16,1,1080,true,.75),"near large train retains full mesh");
  check(ScreenErrorBudget.permits(.125,40000,16,1,1080,true,.75),"subpixel distant train can simplify");
  check(!ScreenErrorBudget.permits(.125,40000,16,1,4320,true,.75),"higher resolution restores detail");
  check(!ScreenErrorBudget.permits(.125,40000,16,4,1080,true,.75),"narrow FOV restores detail");
  check(!ScreenErrorBudget.permits(.125,Double.NaN,16,1,1080,true,.75),"invalid input fails open");
  check(ScreenErrorBudget.permits(.125,100,16,.005,1080,false,.75),"orthographic shadow uses its projection");
  record Draw(String mesh,BoundedDrawGrouping.Rect rect){}
  var left=new BoundedDrawGrouping.Rect(-1,-1,-.5,.5);var right=new BoundedDrawGrouping.Rect(.5,-1,1,.5);
  var a=new Draw("train",left);var b=new Draw("panel",right);var c=new Draw("train",left);var d=new Draw("train",left);
  var original=List.of(a,b,c,d);check(BoundedDrawGrouping.reorder(original,(x,y)->x.mesh.equals(y.mesh),Draw::rect,24).equals(List.of(a,c,d,b)),"disjoint draw regrouping");
  var overlapping=new Draw("panel",left);var constrained=List.of(a,overlapping,c,d);check(BoundedDrawGrouping.reorder(constrained,(x,y)->x.mesh.equals(y.mesh),Draw::rect,24).equals(constrained),"overlapping depth/texture order must survive");
  var unknown=new Draw("addon",null);var uncertain=List.of(a,unknown,c,d);check(BoundedDrawGrouping.reorder(uncertain,(x,y)->x.mesh.equals(y.mesh),Draw::rect,24).equals(uncertain),"unknown/addon bounds keep order");
  List<String> order=new ArrayList<>();WorldTextBatch.register(()->order.add("flush"));
  check(!WorldTextBatch.active(),"outside callbacks stays immediate");WorldTextBatch.begin();check(WorldTextBatch.active(),"world scope");order.add("glyph transformed A");order.add("glyph transformed B");WorldTextBatch.boundary();order.add("texture");
  try{WorldTextBatch.begin();order.add("nested text");throw new RuntimeException();}catch(RuntimeException e){}finally{WorldTextBatch.end();}
  check(WorldTextBatch.active(),"nested exception retains outer scope");WorldTextBatch.end();check(!WorldTextBatch.active(),"exception cleanup cannot batch later GUI");
  check(order.equals(List.of("flush","glyph transformed A","glyph transformed B","flush","texture","flush","nested text","flush","flush")),"text/nontext/nested boundary ordering");
  System.out.println("PASS: dense stable page splitting/resize/capacity, resolution/FOV/near-plane/orthographic LOD, nested text and exception boundaries");
 }
}'''
with tempfile.TemporaryDirectory(prefix="mtr-fps-fixes-") as tmp:
 base=Path(tmp);target=base/'org/mtr/mod/render';target.mkdir(parents=True)
 for name in ['WorldTextBatch','ScreenErrorBudget','RegionPageLayout','BoundedDrawGrouping']:shutil.copy(ROOT/'fabric/src/main/java/org/mtr/mod/render'/f'{name}.java',target)
 (base/'FpsFixesTest.java').write_text(TEST)
 subprocess.run(['javac','-d',tmp,*map(str,base.rglob('*.java'))],check=True)
 subprocess.run(['java','-cp',tmp,'FpsFixesTest'],check=True)
