package org.mtr.mod.render;
import org.junit.Test;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.vertex.Vertex;
import static org.junit.Assert.*;
public class RailPageGeometryTest {
    @Test public void splitsLargeModelWithoutLosingTrianglesOrTextureLightAndMaterial(){
        RawMesh source=new RawMesh(new MaterialProperties(OptimizedModel.ShaderType.CUTOUT,new Identifier("mtr","large-rail.png"),null));
        for(int triangle=0;triangle<6000;triangle++){int base=source.vertices.size();for(int j=0;j<3;j++){Vertex vertex=new Vertex();vertex.position=new Vector3f(triangle,j,0);vertex.normal=new Vector3f(0,0,1);vertex.u=triangle/6000f;vertex.v=j*.25f;vertex.light=triangle;vertex.color=0x12345678;source.vertices.add(vertex);}source.faces.add(new Face(new int[]{base,base+1,base+2}));}
        var chunks=RailGeometrySnapshots.split(source);assertNotNull(chunks);assertEquals(2,chunks.size());int triangle=0;
        for(var chunk:chunks){assertTrue(chunk.vertices()<=16000);for(RawMesh page:chunk.meshes()){assertSame(source.materialProperties,page.materialProperties);for(Face face:page.faces){for(int j=0;j<3;j++){var vertex=page.vertices.get(face.vertices[j]);assertEquals(triangle,vertex.position.getX(),0);assertEquals(j,vertex.position.getY(),0);assertEquals(triangle/6000f,vertex.u,0);assertEquals(j*.25f,vertex.v,0);assertEquals(triangle,vertex.light);assertEquals(0x12345678,vertex.color);}triangle++;}}}
        assertEquals(6000,triangle);assertEquals(18000,source.vertices.size());assertEquals(6000,source.faces.size());assertEquals(17997,source.faces.get(5999).vertices[0]);
    }
    @Test public void rejectsInvalidGeometryInsteadOfPartiallySubmitting(){RawMesh source=new RawMesh(new MaterialProperties(OptimizedModel.ShaderType.CUTOUT,new Identifier("mtr","invalid.png"),null));source.faces.add(new Face(new int[]{0,1,2}));assertNull(RailGeometrySnapshots.split(source));source.faces.set(0,new Face(new int[]{0,1,2,3}));assertNull(RailGeometrySnapshots.split(source));}
}
