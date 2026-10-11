package org.mtr.mod.render;
import org.junit.Test;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.vertex.Vertex;
import java.util.*;
import static org.junit.Assert.*;
public class BoundedMeshSimplifierTest {
    private RawMesh grid(){RawMesh mesh=new RawMesh(new MaterialProperties(OptimizedModel.ShaderType.CUTOUT,new Identifier("mtr","grid.png"),null));
        for(int y=0;y<=10;y++)for(int x=0;x<=10;x++){Vertex vertex=new Vertex();vertex.position=new Vector3f(x*.01f,y*.01f,0);vertex.normal=new Vector3f(0,0,1);vertex.u=x/10f;vertex.v=y/10f;mesh.vertices.add(vertex);}
        for(int y=0;y<10;y++)for(int x=0;x<10;x++){int a=y*11+x;mesh.faces.add(new Face(new int[]{a,a+1,a+12}));mesh.faces.add(new Face(new int[]{a,a+12,a+11}));}return mesh;}
    @Test public void simplifiesConnectedSurfaceWithoutChangingBorderUvOrSource(){RawMesh mesh=grid();int[] indices=BoundedMeshSimplifier.simplify(mesh,.025);assertNotNull(indices);assertTrue(indices.length<600);Set<Integer> retained=new HashSet<>();double area=0;
        for(int i=0;i<indices.length;i+=3){for(int j=0;j<3;j++)retained.add(indices[i+j]);var a=mesh.vertices.get(indices[i]).position;var b=mesh.vertices.get(indices[i+1]).position;var c=mesh.vertices.get(indices[i+2]).position;double cross=(b.getX()-a.getX())*(c.getY()-a.getY())-(b.getY()-a.getY())*(c.getX()-a.getX());assertTrue("winding",cross>0);area+=cross*.5;}
        assertEquals(.01,area,1e-8);for(int i=0;i<=10;i++){assertTrue(retained.contains(i));assertTrue(retained.contains(110+i));assertTrue(retained.contains(i*11));assertTrue(retained.contains(i*11+10));}
        assertEquals(121,mesh.vertices.size());assertEquals(200,mesh.faces.size());assertEquals(.5,mesh.vertices.get(60).u,1e-6);
    }
    @Test public void errorLimitAndUnsupportedSurfaceFailOpen(){RawMesh mesh=grid();assertNull(BoundedMeshSimplifier.simplify(mesh,.0001));assertNull(BoundedMeshSimplifier.simplify(mesh,Double.NaN));mesh.faces.set(0,new Face(new int[]{0,1,999}));assertNull(BoundedMeshSimplifier.simplify(mesh,.025));}
    @Test public void preservesUvDiscontinuities(){RawMesh mesh=grid();for(int i=0;i<mesh.vertices.size();i++)mesh.vertices.get(i).u=(i%2==0?0:1);assertNull(BoundedMeshSimplifier.simplify(mesh,.025));
    }
}
