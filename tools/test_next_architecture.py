"""Exercise shader token transformation and persistent-content region lifetimes without GL."""
from pathlib import Path
import shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
TEST=r'''import org.mtr.mod.render.*;import java.util.*;
public class ArchitectureTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class R implements AutoCloseable {boolean closed;public void close(){check(!closed);closed=true;}}
 record Item(int id,int revision){}
 public static void main(String[] args){
  String v="#version 150\nuniform mat4 iris_ModelViewMat;\nuniform mat4 iris_ModelViewMatInverse;\nuniform mat3 iris_NormalMat;\n// iris_ModelViewMat comment\nvoid main() { gl_Position=iris_ModelViewMat*vec4(1); vec3 n=iris_NormalMat*vec3(1); }";
  String f="#version 150\nuniform mat4 iris_ModelViewMatInverse;\nout vec4 color;\nvoid main(){color=iris_ModelViewMatInverse*vec4(1);}";
  var p=InstanceShaderSource.patch(v,f);check(p!=null);check(p.vertex().contains("uniform mat4 iris_ModelViewMat;"));check(p.vertex().contains("// iris_ModelViewMat comment"));check(p.vertex().contains("mtr_ModelViews[gl_InstanceID]"));check(p.fragment().contains("mtr_ModelViewInverses[mtr_InstanceId]"));check(p.fragment().contains("flat in int mtr_InstanceId"));check(p.vertex().contains("mtr_InstanceId = mtr_Instanced ? gl_InstanceID : 0"));
  check(InstanceShaderSource.patch(v.replace("150","120"),f)==null);check(InstanceShaderSource.patch(v+"\n#define MV iris_ModelViewMat\n",f)==null);check(InstanceShaderSource.patch(p.vertex(),p.fragment())==null);check(InstanceShaderSource.patch("void main(){}",f)==null);
  var cache=new PersistentRegionCache<String,Item,Integer,R>(2,20,12);int[] builds={0};java.util.function.Function<List<Item>,R> build=items->{builds[0]++;return new R();};
  var a=new Item(1,0);var b=new Item(2,0);var changed=new Item(1,1);
  cache.beginFrame();R first=cache.get("s",List.of(a,b),Item::id,x->5,build);check(first!=null&&builds[0]==1);
  cache.beginFrame();check(cache.get("s",List.of(a),Item::id,x->5,build)==first);check(builds[0]==1); // camera hides b: immutable geometry persists
  check(cache.get("s",List.of(changed),Item::id,x->5,build)==null);check(!first.closed); // pinned earlier selection
  cache.beginFrame();R replacement=cache.get("s",List.of(changed),Item::id,x->5,build);check(replacement!=null&&first.closed&&builds[0]==2);check(cache.residentVertices()==10); // unchanged b retained
  cache.beginFrame();check(cache.get("s",List.of(b),Item::id,x->5,build)==replacement);check(builds[0]==2);
  cache.close();check(replacement.closed&&cache.residentVertices()==0);cache.close();
  System.out.println("PASS: GLSL uniforms/comments/macros/stages; region subset reuse, changed-content rebuild, queued-resource pinning, exact disposal");
 }
}'''
with tempfile.TemporaryDirectory(prefix='mtr-architecture-') as tmp:
 p=Path(tmp);(p/'ArchitectureTest.java').write_text(TEST)
 sources=[ROOT/'fabric/src/main/java/org/mtr/mod/render'/f'{name}.java' for name in ['InstanceShaderSource','PersistentRegionCache']]
 subprocess.run([shutil.which('javac'),'-d',tmp,*map(str,sources),str(p/'ArchitectureTest.java')],check=True)
 subprocess.run([shutil.which('java'),'-cp',tmp,'ArchitectureTest'],check=True)
