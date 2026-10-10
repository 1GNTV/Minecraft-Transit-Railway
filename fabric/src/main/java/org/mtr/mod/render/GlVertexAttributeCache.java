package org.mtr.mod.render;

import org.lwjgl.opengl.GL33;

import java.util.Arrays;

/** Generic attribute cache confined to shaderless MTR material batches. */
public final class GlVertexAttributeCache {

	private static final int CAPACITY = 16;
	private static final byte FLOAT2 = 1, FLOAT3 = 2, FLOAT4 = 3, INT2 = 4;
	private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.v4.attributeCache", "true"));
	private static final ThreadLocal<State> LOCAL = ThreadLocal.withInitial(State::new);

	private GlVertexAttributeCache() {}

	private static final class State {
		int depth;
		boolean allowed;
		final byte[] types = new byte[CAPACITY];
		final int[] a = new int[CAPACITY], b = new int[CAPACITY], c = new int[CAPACITY], d = new int[CAPACITY];

		boolean same(int index, byte type, int x, int y, int z, int w) {
			if (!ENABLED || depth != 1 || !allowed || index < 0 || index >= CAPACITY) {
				return false;
			}
			if (types[index] == type && a[index] == x && b[index] == y && c[index] == z && d[index] == w) {
				return true;
			}
			types[index] = type;
			a[index] = x;
			b[index] = y;
			c[index] = z;
			d[index] = w;
			return false;
		}
	}

	public static void begin(boolean shaderless) {
		final State state = LOCAL.get();
		state.depth++;
		state.allowed = shaderless;
		invalidate();
	}

	public static void end() {
		final State state = LOCAL.get();
		if (state.depth > 0) {
			state.depth--;
		}
		// A nested pass may have touched arbitrary state. Never resume its parent's cache.
		state.allowed = false;
		invalidate();
	}

	public static void invalidate() {
		Arrays.fill(LOCAL.get().types, (byte) 0);
	}

	public static boolean isActive() {
		final State state = LOCAL.get();
		return ENABLED && state.depth == 1 && state.allowed;
	}

	/** GL draws can leave current values undefined for enabled attribute arrays. */
	public static void invalidate(int firstLocation, int span) {
		final byte[] types = LOCAL.get().types;
		for (int i = Math.max(0, firstLocation); i < Math.min(CAPACITY, firstLocation + span); i++) {
			types[i] = 0;
		}
	}

	public static void attrib2f(int index, float x, float y) {
		if (!LOCAL.get().same(index, FLOAT2, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), 0, 0)) {
			GL33.glVertexAttrib2f(index, x, y);
		}
	}

	public static void attrib3f(int index, float x, float y, float z) {
		if (!LOCAL.get().same(index, FLOAT3, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), 0)) {
			GL33.glVertexAttrib3f(index, x, y, z);
		}
	}

	public static void attrib4f(int index, float x, float y, float z, float w) {
		if (!LOCAL.get().same(index, FLOAT4, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), Float.floatToRawIntBits(w))) {
			GL33.glVertexAttrib4f(index, x, y, z, w);
		}
	}

	public static void attribI2i(int index, int x, int y) {
		if (!LOCAL.get().same(index, INT2, x, y, 0, 0)) {
			GL33.glVertexAttribI2i(index, x, y);
		}
	}
}
