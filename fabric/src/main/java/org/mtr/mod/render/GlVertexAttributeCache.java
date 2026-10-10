package org.mtr.mod.render;

import org.lwjgl.opengl.GL33;
import java.util.Arrays;

/**
 * Within MTR's protected batching pass, skip OpenGL generic-attribute calls
 * that would write exactly the same values. Outside the pass we forward every call.
 * A fresh cache is used for each pass to preserve other mods' GL state.
 */
public final class GlVertexAttributeCache {
    private static final int CAPACITY = 16;
    private static final byte FLOAT2 = 1, FLOAT3 = 2, FLOAT4 = 3, INT2 = 4;
    private static final ThreadLocal<State> LOCAL = ThreadLocal.withInitial(State::new);

    private GlVertexAttributeCache() {}

    private static final class State {
        int depth;
        final byte[] type = new byte[CAPACITY];
        final int[] a = new int[CAPACITY];
        final int[] b = new int[CAPACITY];
        final int[] c = new int[CAPACITY];
        final int[] d = new int[CAPACITY];

        boolean same(int location, byte kind, int x, int y, int z, int w) {
            if (depth == 0 || location < 0 || location >= CAPACITY) return false;
            if (type[location] == kind && a[location] == x && b[location] == y && c[location] == z && d[location] == w) return true;
            type[location] = kind;
            a[location] = x;
            b[location] = y;
            c[location] = z;
            d[location] = w;
            return false;
        }
    }

    public static void begin() {
        final State state = LOCAL.get();
        if (state.depth++ == 0) Arrays.fill(state.type, (byte) 0);
    }

    public static void end() {
        final State state = LOCAL.get();
        if (state.depth > 0) state.depth--;
    }

    public static void attrib2f(int index, float x, float y) {
        if (!LOCAL.get().same(index, FLOAT2, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), 0, 0))
            GL33.glVertexAttrib2f(index, x, y);
    }

    public static void attrib3f(int index, float x, float y, float z) {
        if (!LOCAL.get().same(index, FLOAT3, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), 0))
            GL33.glVertexAttrib3f(index, x, y, z);
    }

    public static void attrib4f(int index, float x, float y, float z, float w) {
        if (!LOCAL.get().same(index, FLOAT4, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), Float.floatToRawIntBits(w)))
            GL33.glVertexAttrib4f(index, x, y, z, w);
    }

    public static void attribI2i(int index, int x, int y) {
        if (!LOCAL.get().same(index, INT2, x, y, 0, 0))
            GL33.glVertexAttribI2i(index, x, y);
    }
}
