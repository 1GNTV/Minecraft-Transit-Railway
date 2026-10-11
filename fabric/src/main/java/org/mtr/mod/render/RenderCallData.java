package org.mtr.mod.render;

import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeState;

/** Implemented on the actual mapping RenderCall by the Forge performance mixin. */
public interface RenderCallData {
    VertexArray mtr$array();
    VertexAttributeState mtr$attributes();
    RenderPipelineMetrics.Family mtr$family();
    default boolean mtr$visible(){return ModelPartVisibility.visible(mtr$array(),mtr$attributes());}
    default BoundedDrawGrouping.Rect mtr$screenBounds(){return ModelPartVisibility.screenBounds(mtr$array(),mtr$attributes());}
}
