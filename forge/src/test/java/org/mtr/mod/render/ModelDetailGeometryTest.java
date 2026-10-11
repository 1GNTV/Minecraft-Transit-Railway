package org.mtr.mod.render;
import org.junit.Test;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.vertex.Vertex;
import static org.junit.Assert.*;

public class ModelDetailGeometryTest {
    private RawMesh mesh(){return new RawMesh(new MaterialProperties(OptimizedModel.ShaderType.CUTOUT,new Identifier("mtr","detail.png"),null));}
    private void cube(RawMesh mesh,float start,float size){
        int base=mesh.vertices.size();for(int i=0;i<8;i++){Vertex v=new Vertex();v.position=new Vector3f(start+((i&1)==0?0:size),((i&2)==0?0:size),((i&4)==0?0:size));v.u=i/8F;mesh.vertices.add(v);}
        int[][] faces={{0,1,3},{0,3,2},{4,6,7},{4,7,5},{0,4,5},{0,5,1},{2,3,7},{2,7,6},{0,2,6},{0,6,4},{1,5,7},{1,7,3}};
        for(int[] face:faces)mesh.faces.add(new Face(new int[]{base+face[0],base+face[1],base+face[2]}));
    }
    @Test public void removesSmallComponentsAndKeepsExactRetainedIndicesAndSource(){RawMesh mesh=mesh();cube(mesh,0,4);cube(mesh,10,.05f);int[] reduced=ModelDetailGeometry.indices(mesh,.125);assertNotNull(reduced);assertEquals(36,reduced.length);for(int i=0;i<12;i++)for(int n=0;n<3;n++)assertEquals(mesh.faces.get(i).vertices[n],reduced[i*3+n]);assertEquals(24,mesh.faces.size());assertEquals(16,mesh.vertices.size());}
    @Test public void retainsModelsThatWouldDisappearAndTransparentGeometry(){RawMesh mesh=mesh();cube(mesh,0,.05f);cube(mesh,1,.05f);assertNull(ModelDetailGeometry.indices(mesh,.125));RawMesh transparent=new RawMesh(new MaterialProperties(OptimizedModel.ShaderType.TRANSLUCENT,new Identifier("mtr","detail.png"),null));cube(transparent,0,4);cube(transparent,10,.05f);assertNull(ModelDetailGeometry.indices(transparent,.125));}
    @Test public void choosesDifferentMainAndShadowDetailWithoutMovingVertices(){RawMesh mesh=mesh();cube(mesh,0,4);cube(mesh,10,.2f);assertNull(ModelDetailGeometry.indices(mesh,.125));assertEquals(36,ModelDetailGeometry.indices(mesh,.25).length);assertEquals(10,mesh.vertices.get(8).position.getX(),0);}
    @Test public void rejectsInvalidIndicesAndNonTriangles(){RawMesh mesh=mesh();cube(mesh,0,4);cube(mesh,10,.05f);mesh.faces.set(0,new Face(new int[]{0,1,999}));assertNull(ModelDetailGeometry.indices(mesh,.125));mesh.faces.set(0,new Face(new int[]{0,1,2,3}));assertNull(ModelDetailGeometry.indices(mesh,.125));}
}
