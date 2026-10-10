package org.mtr.mod.render;

import org.junit.Test;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.vertex.Vertex;

import static org.junit.Assert.*;

public class OpaqueMeshCombinerTest {

	private static MaterialProperties material(OptimizedModel.ShaderType type, String texture) {
		return new MaterialProperties(type, new Identifier("mtr", texture), null);
	}

	private static RawMesh triangle(MaterialProperties material, float offset) {
		final RawMesh mesh = new RawMesh(material);
		for (int i = 0; i < 3; i++) {
			final Vertex vertex = new Vertex();
			vertex.position = new Vector3f(offset + i, i == 1 ? 1 : 0, 0);
			vertex.normal = new Vector3f(0, 0, 1);
			mesh.vertices.add(vertex);
		}
		mesh.faces.add(new Face(new int[]{0, 1, 2}));
		return mesh;
	}

	@Test
	public void mergesEqualSolidMaterialsAndPreservesSourceTopology() {
		final RawMesh first = triangle(material(OptimizedModel.ShaderType.CUTOUT, "a"), 0);
		final RawMesh second = triangle(material(OptimizedModel.ShaderType.CUTOUT, "a"), 10);
		final OpaqueMeshCombiner combiner = new OpaqueMeshCombiner();
		combiner.append(first);
		combiner.append(second);
		assertEquals(2, combiner.sourceDraws());
		assertEquals(1, combiner.meshes().size());
		final RawMesh combined = combiner.meshes().get(0);
		assertEquals(6, combined.vertices.size());
		assertEquals(2, combined.faces.size());
		assertArrayEquals(new int[]{3, 4, 5}, combined.faces.get(1).vertices);
		assertArrayEquals(new int[]{0, 1, 2}, second.faces.get(0).vertices);
		combined.validateVertexIndex();
		combined.distinct();
		assertEquals(2, combined.faces.size());
		assertArrayEquals(new int[]{0, 1, 2}, first.faces.get(0).vertices);
		assertArrayEquals(new int[]{0, 1, 2}, second.faces.get(0).vertices);
	}

	@Test
	public void keepsTransparencyGlowAndTexturesSeparate() {
		final OpaqueMeshCombiner combiner = new OpaqueMeshCombiner();
		final MaterialProperties transparent = material(OptimizedModel.ShaderType.TRANSLUCENT, "a");
		final RawMesh first = triangle(transparent, 0), second = triangle(transparent, 1);
		final MaterialProperties glow = material(OptimizedModel.ShaderType.CUTOUT_GLOWING, "a");
		final RawMesh third = triangle(glow, 2), fourth = triangle(glow, 3);
		combiner.append(first);
		combiner.append(second);
		combiner.append(third);
		combiner.append(fourth);
		combiner.append(triangle(material(OptimizedModel.ShaderType.CUTOUT, "a"), 4));
		combiner.append(triangle(material(OptimizedModel.ShaderType.CUTOUT, "b"), 5));
		assertEquals(6, combiner.meshes().size());
		assertSame(first, combiner.meshes().get(0));
		assertSame(second, combiner.meshes().get(1));
		assertSame(third, combiner.meshes().get(2));
		assertSame(fourth, combiner.meshes().get(3));
	}

	@Test
	public void separateUploadsNeverShareMergedGeometry() {
		final RawMesh triangle = triangle(material(OptimizedModel.ShaderType.CUTOUT, "a"), 0);
		final OpaqueMeshCombiner body = new OpaqueMeshCombiner(), door = new OpaqueMeshCombiner();
		body.append(triangle);
		door.append(triangle);
		assertNotSame(body.meshes().get(0), door.meshes().get(0));
	}
}
