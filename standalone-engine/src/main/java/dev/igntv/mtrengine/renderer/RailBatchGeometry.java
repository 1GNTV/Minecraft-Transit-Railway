package dev.igntv.mtrengine.renderer;

import org.mtr.mapping.holder.Matrix4f;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.Vertex;
import java.util.List;

/** Pure CPU helpers. Source meshes must never be mutated by a sector bake. */
public final class RailBatchGeometry {
    private RailBatchGeometry() {}

    public static boolean eligible(List<RawMesh> meshes) {
        if (meshes.isEmpty()) return false;
        for (RawMesh mesh : meshes) {
            if (mesh.materialProperties.translucent || mesh.materialProperties.cutoutHack || !mesh.materialProperties.writeDepthBuf || mesh.materialProperties.vertexAttributeState.lightmapUV != null) return false;
            for (Face face : mesh.faces) if (face.vertices.length != 3) return false;
            for (Vertex vertex : mesh.vertices) if (vertex.position == null || vertex.normal == null) return false;
        }
        return true;
    }

    public static RawMesh copy(RawMesh source) {
        final RawMesh copy = new RawMesh(source.materialProperties);
        for (Vertex vertex : source.vertices) copy.vertices.add(new Vertex(vertex));
        for (Face face : source.faces) copy.faces.add(new Face(face.vertices.clone()));
        return copy;
    }

    public static RawMesh bake(RawMesh source, double x, double y, double z, float yaw, float pitch, float rollDegrees, int packedLight) {
        final RawMesh copy = copy(source);
        final org.joml.Matrix4f matrix = new org.joml.Matrix4f().translation((float) x, (float) y, (float) z).rotateY(yaw).rotateX(pitch).rotateZ((float) Math.toRadians(rollDegrees));
        copy.applyMatrix(new Matrix4f(matrix));
        final int light = Utilities.exchangeLightmapUVBits(packedLight);
        for (Vertex vertex : copy.vertices) vertex.light = light;
        return copy;
    }
}
