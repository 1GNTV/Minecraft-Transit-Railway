package org.mtr.mod.render;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Experimental post-TransformPatcher Oculus bridge. Unlike @ModifyArgs, this
 * modifies a single Java Map local variable containing the entire transformed
 * shader program. No Sponge Mixin synthetic Args class is generated.
 *
 * The bridge is opt-in and returns the IDENTICAL input map when disabled,
 * unsupported, or when shader preflight compilation fails.
 */
public final class OculusShaderMapBridge {
    private static final boolean ENABLED=Boolean.getBoolean("mtr.experimental.oculusMapBridge");
    private OculusShaderMapBridge() {}

    public static Map<?,?> patch(Map<?,?> transformed) {
        if(!ENABLED)return transformed;
        return tryPatch(transformed,OculusInstanceRenderer::prepare);
    }

    /** Package-neutral for isolated unit testing with fake shader enums. */
    public static Map<?,?> tryPatch(Map<?,?> transformed,
        BiFunction<String,String,InstanceShaderSource.Pair> prepare) {
        if(transformed==null || transformed.isEmpty() || prepare==null)return transformed;
        Object vertexKey=null,fragmentKey=null;
        String vertex=null,fragment=null;
        for(var entry:transformed.entrySet()){
            Object key=entry.getKey(),value=entry.getValue();
            if(!(key instanceof Enum<?> stage))return transformed;
            switch(stage.name()){
                case "VERTEX" -> {vertexKey=key;if(!(value instanceof String))return transformed;vertex=(String)value;}
                case "FRAGMENT" -> {fragmentKey=key;if(!(value instanceof String))return transformed;fragment=(String)value;}
                case "GEOMETRY","TESS_CONTROL","TESS_EVAL" -> {
                    // Instancing cannot bypass geometry/tessellation stages safely.
                    if(value!=null)return transformed;
                }
                default -> {}
            }
        }
        if(vertex==null || fragment==null)return transformed;
        final InstanceShaderSource.Pair patched;
        try {
            patched=prepare.apply(vertex,fragment);
        } catch (RuntimeException unsupportedShader) {
            // Shader-pack syntax and optional Oculus integrations vary widely.
            // A failed candidate must never stop normal shader loading.
            return transformed;
        }
        if(patched==null)return transformed;
        Map<Object,Object> copy=new HashMap<>(transformed);
        copy.put(vertexKey,patched.vertex());
        copy.put(fragmentKey,patched.fragment());
        OculusInstanceRenderer.invalidatePrograms();
        return copy;
    }
}
