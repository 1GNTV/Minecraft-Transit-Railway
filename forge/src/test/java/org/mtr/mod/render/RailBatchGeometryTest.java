package org.mtr.mod.render;

import org.junit.Test;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.vertex.Vertex;
import java.util.List;
import static org.junit.Assert.*;

public class RailBatchGeometryTest {
    private static RawMesh mesh(OptimizedModel.ShaderType type) {
        final RawMesh mesh = new RawMesh(new MaterialProperties(type, new Identifier("mtr", "rail.png"), null));
        final Vertex v = new Vertex();
        v.position = new Vector3f(1, 0, 0); v.normal = new Vector3f(1, 0, 0);
        v.u = 0.25F; v.v = 0.75F; v.color = 0xFF123456;
        mesh.vertices.add(v); mesh.faces.add(new Face(new int[]{0, 0, 0}));
        return mesh;
    }

    @Test public void copyOwnsVerticesVectorsAndFaceIndices() {
        final RawMesh source = mesh(OptimizedModel.ShaderType.CUTOUT), copy = RailBatchGeometry.copy(source);
        assertNotSame(source.vertices.get(0), copy.vertices.get(0));
        assertNotSame(source.vertices.get(0).position, copy.vertices.get(0).position);
        assertNotSame(source.vertices.get(0).normal, copy.vertices.get(0).normal);
        assertNotSame(source.faces.get(0).vertices, copy.faces.get(0).vertices);
        copy.vertices.get(0).u = 1; copy.faces.get(0).vertices[0] = 7;
        assertEquals(0.25F, source.vertices.get(0).u, 0);
        assertEquals(0, source.faces.get(0).vertices[0]);
    }

    @Test public void bakingPreservesSourceAndUsesLightmapBufferPacking() {
        final RawMesh source = mesh(OptimizedModel.ShaderType.CUTOUT);
        final Vertex v = RailBatchGeometry.bake(source, 5, 2, 7, (float) (Math.PI / 2), 0, 0, 0x00E00020).vertices.get(0);
        assertEquals(5, v.position.getX(), 0.00001F);
        assertEquals(2, v.position.getY(), 0.00001F);
        assertEquals(6, v.position.getZ(), 0.00001F);
        assertEquals(0, v.normal.getX(), 0.00001F);
        assertEquals(-1, v.normal.getZ(), 0.00001F);
        assertEquals(0x002000E0, v.light);
        assertEquals(0xFF123456, v.color);
        assertEquals(0.25F, v.u, 0); assertEquals(0.75F, v.v, 0);
        assertEquals(1, source.vertices.get(0).position.getX(), 0);
        assertEquals(0, source.vertices.get(0).light);
    }

    @Test public void incompatibleWholeResourcesFallBack() {
        final RawMesh solid = mesh(OptimizedModel.ShaderType.CUTOUT);
        assertTrue(RailBatchGeometry.eligible(List.of(solid)));
        assertFalse(RailBatchGeometry.eligible(List.of()));
        assertFalse(RailBatchGeometry.eligible(List.of(solid, mesh(OptimizedModel.ShaderType.TRANSLUCENT))));
        assertFalse(RailBatchGeometry.eligible(List.of(solid, mesh(OptimizedModel.ShaderType.CUTOUT_GLOWING))));
        solid.vertices.get(0).normal = null;
        assertFalse(RailBatchGeometry.eligible(List.of(solid)));
    }
}
