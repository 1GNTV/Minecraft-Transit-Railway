package org.mtr.mod.render;

import org.junit.Test;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.vertex.Vertex;
import java.util.*;
import static org.junit.Assert.*;

public class OpaqueMeshOptimizerTest {
    private RawMesh mesh(OptimizedModel.ShaderType type) {
        RawMesh mesh = new RawMesh(new MaterialProperties(type, new Identifier("mtr", "test.png"), null));
        for (int i = 0; i < 48; i++) { Vertex v = new Vertex(); v.color = i; v.u = i / 50F; v.light = i * 16; mesh.vertices.add(v); }
        for (int i = 0; i < 16; i++) mesh.faces.add(new Face(new int[]{i * 3, i * 3 + 1, i * 3 + 2}));
        return mesh;
    }
    private List<String> triangles(RawMesh mesh) {
        List<String> result = new ArrayList<>();
        for (Face f : mesh.faces) { StringBuilder s = new StringBuilder(); for (int i : f.vertices) s.append(mesh.vertices.get(i).color).append(','); result.add(s.toString()); }
        Collections.sort(result); return result;
    }
    @Test public void preservesEveryTriangleWindingProvokingVertexAndAttribute() {
        RawMesh source = mesh(OptimizedModel.ShaderType.CUTOUT), result = OpaqueMeshOptimizer.optimize(source);
        assertNotSame(source, result); assertEquals(triangles(source), triangles(result));
        assertSame(source.materialProperties, result.materialProperties); assertEquals(source.vertices.size(), result.vertices.size());
        for (Vertex v : result.vertices) { assertSame(source.vertices.get(v.color), v); assertEquals(v.color / 50F, v.u, 0); assertEquals(v.color * 16, v.light); }
        for (int i = 0; i < 16; i++) assertArrayEquals(new int[]{i * 3, i * 3 + 1, i * 3 + 2}, source.faces.get(i).vertices);
    }
    @Test public void transparentGlowAndNonTrianglesKeepOriginalOrder() {
        RawMesh translucent = mesh(OptimizedModel.ShaderType.TRANSLUCENT), glow = mesh(OptimizedModel.ShaderType.CUTOUT_GLOWING);
        assertSame(translucent, OpaqueMeshOptimizer.optimize(translucent)); assertSame(glow, OpaqueMeshOptimizer.optimize(glow));
        RawMesh quads = mesh(OptimizedModel.ShaderType.CUTOUT); quads.faces.set(0, new Face(new int[]{0,1,2,3})); assertSame(quads, OpaqueMeshOptimizer.optimize(quads));
    }
    @Test public void reducesCacheMissesOnInterleavedDisconnectedStrips() {
        int[] indices = new int[200 * 3];
        for (int f = 0; f < 200; f++) { int base = (f % 20) * 100 + f / 20; indices[f*3]=base; indices[f*3+1]=base+1; indices[f*3+2]=base+2; }
        int[] original = indices.clone(), result = TriangleOrder.optimize(indices, 2048);
        assertArrayEquals(original, indices); assertTrue(TriangleOrder.misses(result) < TriangleOrder.misses(indices));
        List<String> before = new ArrayList<>(), after = new ArrayList<>();
        for (int i=0;i<indices.length;i+=3) { before.add(Arrays.toString(Arrays.copyOfRange(indices,i,i+3))); after.add(Arrays.toString(Arrays.copyOfRange(result,i,i+3))); }
        Collections.sort(before); Collections.sort(after); assertEquals(before,after);
    }
    @Test public void rejectsInvalidIndicesAndBoundsLargeInputs() {
        try { TriangleOrder.optimize(new int[]{0,1,9}, 2); fail(); } catch (IllegalArgumentException expected) {}
        assertArrayEquals(new int[]{0,1,2}, TriangleOrder.optimize(new int[]{0,1,2}, 200001));
        assertArrayEquals(new int[0], TriangleOrder.optimize(new int[0], 1));
    }
}
