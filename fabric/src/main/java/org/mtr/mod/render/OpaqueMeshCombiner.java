package org.mtr.mod.render;

import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.RawMesh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merge only equal solid materials within a single rigid model upload. */
public final class OpaqueMeshCombiner {

	private final Map<MaterialProperties, RawMesh> solids = new LinkedHashMap<>();
	private final List<RawMesh> ordered = new ArrayList<>();
	private int sourceDraws;

	public void append(RawMesh source) {
		if (!source.hasFaces()) return;
		sourceDraws++;
		final MaterialProperties material = source.materialProperties;
		if (material.translucent || material.cutoutHack || !material.writeDepthBuf) {
			ordered.add(source);
			return;
		}
		RawMesh combined = solids.get(material);
		if (combined == null) {
			combined = new RawMesh(material);
			solids.put(material, combined);
			ordered.add(combined);
		}
		// RawMesh.append copies faces and adjusts indices; source topology stays intact.
		combined.append(source);
	}

	public List<RawMesh> meshes() { return ordered; }
	public int sourceDraws() { return sourceDraws; }
}
