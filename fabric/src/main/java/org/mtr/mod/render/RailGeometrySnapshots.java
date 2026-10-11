package org.mtr.mod.render;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.Vertex;
import org.mtr.mod.resource.OptimizedModelWrapper;
import java.util.*;

/** Partition only compatible materials; keep the other original arrays as ordinary draws. */
public final class RailGeometrySnapshots {
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.next.regions", "true"));
    public static final int PAGE_VERTICES = 16_000;
    private static final int MAX_RETAINED_VERTICES = 200_000;
    private static final WeakHashMap<OptimizedModel, Snapshot> SNAPSHOTS = new WeakHashMap<>();
    public record Chunk(List<RawMesh> meshes, int vertices) {}
    public record Snapshot(List<Chunk> chunks, int vertices, OptimizedModelWrapper remainder) {}
    private RailGeometrySnapshots() {}
    public static void retain(OptimizedModel model, List<RawMesh> meshes, List<VertexArray> arrays) {
        if (!ENABLED || meshes.size()!=arrays.size()) return;
        int retained=SNAPSHOTS.values().stream().mapToInt(Snapshot::vertices).sum();
        List<Chunk> chunks=new ArrayList<>(); List<VertexArray> residual=new ArrayList<>(); int count=0;
        for(int i=0;i<meshes.size();i++) {
            RawMesh mesh=meshes.get(i);
            // The per-resource and global budgets are unchanged. Unsupported parts never disappear.
            if(!RailBatchGeometry.eligible(List.of(mesh)) || retained+count+mesh.vertices.size()>MAX_RETAINED_VERTICES) { residual.add(arrays.get(i)); continue; }
            List<Chunk> split=split(mesh); if(split==null) { residual.add(arrays.get(i)); continue; }
            int size=split.stream().mapToInt(Chunk::vertices).sum();
            if(retained+count+size>MAX_RETAINED_VERTICES) { residual.add(arrays.get(i)); continue; }
            chunks.addAll(split);count+=size;
        }
        if(chunks.isEmpty())return;
        SNAPSHOTS.put(model,new Snapshot(List.copyOf(chunks),count,residual.isEmpty()?null:OptimizedModelWrapper.fromUploadedModel(new OptimizedModel(residual))));
    }
    private static List<Chunk> split(RawMesh mesh) {
        List<Chunk> result=new ArrayList<>(); RawMesh page=new RawMesh(mesh.materialProperties);Map<Integer,Integer> remap=new HashMap<>();
        for(Face face:mesh.faces) {
            int added=0;for(int index:face.vertices){if(index<0 || index>=mesh.vertices.size())return null;if(!remap.containsKey(index))added++;}
            if(page.vertices.size()+added>PAGE_VERTICES) { result.add(new Chunk(List.of(page),page.vertices.size()));page=new RawMesh(mesh.materialProperties);remap.clear(); }
            int[] indices=new int[3];
            for(int j=0;j<3;j++){int source=face.vertices[j];Integer mapped=remap.get(source);if(mapped==null){mapped=page.vertices.size();remap.put(source,mapped);page.vertices.add(new Vertex(mesh.vertices.get(source)));}indices[j]=mapped;}
            page.faces.add(new Face(indices));
        }
        if(!page.faces.isEmpty())result.add(new Chunk(List.of(page),page.vertices.size()));return result;
    }
    public static Snapshot get(OptimizedModel model) { return ENABLED ? SNAPSHOTS.get(model) : null; }
    public static void clear() { SNAPSHOTS.clear(); }
}
