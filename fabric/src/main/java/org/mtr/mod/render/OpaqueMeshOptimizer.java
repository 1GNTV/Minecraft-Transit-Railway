package org.mtr.mod.render;

import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import java.util.Arrays;

/** Lossless attribute/triangle remapping, only at upload of compatible solid geometry. */
public final class OpaqueMeshOptimizer {
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.next.meshOrder", "true"));
    private OpaqueMeshOptimizer() {}
    public static RawMesh optimize(RawMesh source) {
        if (!ENABLED || source.materialProperties.translucent || source.materialProperties.cutoutHack || !source.materialProperties.writeDepthBuf || source.faces.size() < 16 || source.vertices.size() > 200_000 || source.faces.size() > 200_000) return source;
        int[] indices = new int[source.faces.size() * 3];
        for (int f = 0; f < source.faces.size(); f++) {
            int[] face = source.faces.get(f).vertices; if (face.length != 3) return source;
            System.arraycopy(face, 0, indices, f * 3, 3);
        }
        int[] ordered = TriangleOrder.optimize(indices, source.vertices.size());
        int[] remap = new int[source.vertices.size()]; Arrays.fill(remap, -1);
        RawMesh result = new RawMesh(source.materialProperties);
        for (int i = 0; i < ordered.length; i++) {
            int original = ordered[i];
            if (remap[original] < 0) { remap[original] = result.vertices.size(); result.vertices.add(source.vertices.get(original)); }
            ordered[i] = remap[original];
        }
        for (int i = 0; i < remap.length; i++) if (remap[i] < 0) result.vertices.add(source.vertices.get(i));
        for (int i = 0; i < ordered.length; i += 3) result.faces.add(new Face(new int[]{ordered[i], ordered[i + 1], ordered[i + 2]}));
        return result;
    }
}
