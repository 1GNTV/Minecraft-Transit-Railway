"""Compare PANAM row selection and ETA offsets with MTR RenderPIDS."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
p=root/"fabric/src/main/java/org/mtr/mod/render"
renderer=(p/"RenderPANAMPIDS.java").read_text()
vanilla=(p/"RenderPIDS.java").read_text()
surface=(p/"pixel/PanamSurface.java").read_text()
assert 'PanamArrivalSelection.select(' in renderer
assert 'MAX_PANAM_WAIT_MS' not in renderer
assert 'receivedAt()<15000' not in renderer
assert 'ids.add(id)' not in renderer
assert 'getDisplayPage() * arrivalsPerPage' in vanilla
assert 'arrivalIndex++' in vanilla
assert 'if(spec.count==1)' in surface
java=r'''
import org.mtr.mod.render.PanamArrivalSelection;
import java.util.*;
public class VerifyPANAM {
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 public static void main(String[] args){
   var rows=List.of("A","B","B","C");
   check(PanamArrivalSelection.select(rows,3,0,i->false).equals(List.of("A","B","B")),"duplicates kept");
   check(PanamArrivalSelection.select(rows,3,1,i->false).equals(List.of("C")),"page cursor");
   check(PanamArrivalSelection.select(rows,3,0,i->i==0).equals(List.of("A","B")),"hidden first does not consume arrival");
   check(PanamArrivalSelection.select(rows,3,0,i->i==1).equals(List.of("A","B")),"hidden middle does not consume arrival");
   check(PanamArrivalSelection.select(List.of("A"),3,0,i->false).equals(List.of("A")),"no invented second train");
   check(PanamArrivalSelection.select(List.<String>of(),3,0,i->false).isEmpty(),"empty");
   long epoch=1000000000L,offset=8200L,now=epoch-128200L;
   check(PanamArrivalSelection.etaSeconds(epoch,offset,now)==120,"MTR offset and seconds");
   check(PanamArrivalSelection.clientArrivalMillis(epoch,offset)==epoch-offset,"epoch correction");
   System.out.println("PASS: row order, hidden rows, page selection, ETA server offset");
 }
}
'''
with tempfile.TemporaryDirectory() as d:
    dest=Path(d)
    (dest/"VerifyPANAM.java").write_text(java)
    subprocess.run(["javac","-d",d,str(p/"PanamArrivalSelection.java"),str(dest/"VerifyPANAM.java")],check=True)
    subprocess.run(["java","-cp",d,"VerifyPANAM"],check=True)
