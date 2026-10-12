package dev.igntv.mtrengine.renderer;

import java.util.Arrays;

/** Bounded adjacency traversal; keeps each triangle's winding and provoking vertex. */
public final class TriangleOrder {
    private static final int CACHE = 32;
    private TriangleOrder() {}
    public static int[] optimize(int[] indices, int vertices) {
        if (indices.length % 3 != 0 || vertices < 1 || vertices > 200_000 || indices.length > 600_000) return indices.clone();
        int[] offsets = new int[vertices + 1];
        for (int index : indices) { if (index < 0 || index >= vertices) throw new IllegalArgumentException("Invalid vertex index"); offsets[index + 1]++; }
        for (int i = 1; i <= vertices; i++) offsets[i] += offsets[i - 1];
        int[] cursor = offsets.clone(), adjacency = new int[indices.length];
        for (int i = 0; i < indices.length; i++) adjacency[cursor[indices[i]]++] = i / 3;
        cursor = offsets.clone();
        boolean[] emitted = new boolean[indices.length / 3];
        int[] cache = new int[CACHE], result = new int[indices.length]; Arrays.fill(cache, -1);
        boolean[] cached = new boolean[vertices];
        int sequential = 0;
        for (int out = 0; out < emitted.length; out++) {
            int best = -1, score = -1;
            for (int vertex : cache) {
                if (vertex < 0) continue;
                while (cursor[vertex] < offsets[vertex + 1] && emitted[adjacency[cursor[vertex]]]) cursor[vertex]++;
                int scans = 0;
                for (int p = cursor[vertex]; p < offsets[vertex + 1] && scans < 4; p++, scans++) {
                    int face = adjacency[p]; if (emitted[face]) continue;
                    int hits = 0;
                    for (int n = 0; n < 3; n++) if (cached[indices[face * 3 + n]]) hits++;
                    if (hits > score) { score = hits; best = face; }
                }
            }
            if (best < 0) {
                while (emitted[sequential]) sequential++;
                best = sequential;
            }
            emitted[best] = true;
            for (int n = 0; n < 3; n++) {
                int index = indices[best * 3 + n]; result[out * 3 + n] = index;
                if (!cached[index] && cache[CACHE - 1] >= 0) cached[cache[CACHE - 1]] = false;
                touch(cache, index); cached[index] = true;
            }
        }
        // An estimate, not a GPU measurement: reject a reorder that worsens this cache model.
        return misses(result) <= misses(indices) ? result : indices.clone();
    }
    private static boolean contains(int[] cache, int value) { for (int item : cache) if (item == value) return true; return false; }
    private static void touch(int[] cache, int value) {
        int location = CACHE - 1;
        for (int i = 0; i < CACHE; i++) if (cache[i] == value) { location = i; break; }
        System.arraycopy(cache, 0, cache, 1, location); cache[0] = value;
    }
    public static int misses(int[] indices) {
        int[] cache = new int[CACHE]; Arrays.fill(cache, -1); int misses = 0;
        for (int value : indices) { if (!contains(cache, value)) misses++; touch(cache, value); }
        return misses;
    }
}
