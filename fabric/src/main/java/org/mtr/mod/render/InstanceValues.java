package org.mtr.mod.render;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.*;

/** Values after material overrides, in the same packed RGBA/light ordering as mapping apply(). */
public final class InstanceValues {
    private InstanceValues() {}
    public static Integer value(RenderCallData call,VertexAttributeType type){
        VertexAttributeState state=call.mtr$attributes(),material=call.mtr$array().materialProperties.vertexAttributeState;
        return switch(type){case COLOR -> material.color!=null?material.color:state.color;case UV_LIGHTMAP -> material.lightmapUV!=null?material.lightmapUV:state.lightmapUV;case UV_OVERLAY -> material.overlayUV!=null?material.overlayUV:state.overlayUV;default -> null;};
    }
    public static boolean varying(VertexArray array,VertexAttributeType type){return array.mapping.sources.get(type)==VertexAttributeSource.GLOBAL;}
}
