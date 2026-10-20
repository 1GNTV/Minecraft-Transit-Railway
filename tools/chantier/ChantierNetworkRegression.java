import org.mtr.mod.chantier.*;
import org.mtr.core.simulation.Simulator;
import java.nio.file.*;
import java.util.*;
import static org.mtr.mod.chantier.ChantierPlan.*;
public final class ChantierNetworkRegression {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("sillon-chantier-network-");Simulator data=new Simulator("overworld",new String[]{"overworld"},dir,false);String id=UUID.randomUUID().toString();
  var plan=ChantierPlan.of(new Part(Kind.STATION,new Point(.5,20,.5),new Point(.5,20,40.5),19,8,1,1));
  var prep=new ChantierNetwork(0,id,plan.encode(),"{}","{}").run(data);check(prep.error.isEmpty(),prep.error);check(ChantierNetwork.nodes(prep.target).size()==4,"Four actual rail nodes missing");
  var built=new ChantierNetwork(1,id,plan.encode(),prep.snapshot,prep.target).run(data);check(built.error.isEmpty(),built.error);check(data.stations.size()==1&&data.platforms.size()==2&&data.rails.size()==2,"Native station/platform rails absent");check(data.platforms.stream().allMatch(p->p.area!=null),"Platforms not associated with station");
  var next=plan.withParts(List.of(plan.parts().get(0).dimensions(21,10)));var edit=new ChantierNetwork(0,id,next.encode(),built.snapshot,"{}").run(data);check(edit.error.isEmpty(),edit.error);var changed=new ChantierNetwork(1,id,next.encode(),edit.snapshot,edit.target).run(data);check(changed.error.isEmpty(),changed.error);check(data.stations.size()==1&&data.platforms.size()==2&&data.rails.size()==2,"Adjustment duplicated native data");
  var removed=new ChantierNetwork(2,id,"",changed.snapshot,"{}").run(data);check(removed.error.isEmpty(),removed.error);check(data.stations.isEmpty()&&data.platforms.isEmpty()&&data.rails.isEmpty(),"Undo left native railway records");data.stop();System.out.println("PASS chantier native MTR: prepare, nodes, station, platform rails, real associations, adjustment without duplicates and complete removal");
 }
}
