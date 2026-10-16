package org.mtr.mod.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/** Arrival-row selection shared by the PANAM display tests and renderer.
 * Reproduces the base MTR PIDS page and hidden-row cursor semantics without
 * discarding valid server arrivals for arbitrary time/identity reasons. */
public final class PanamArrivalSelection {
    private PanamArrivalSelection() {}

    public static <T> List<T> select(List<T> arrivals, int rows, int page, IntPredicate hidden) {
        int cursor = Math.max(0, page) * rows;
        List<T> shown = new ArrayList<>();
        for (int slot = 0; slot < rows; slot++) {
            if (hidden.test(slot)) continue;
            if (cursor >= arrivals.size()) break;
            shown.add(arrivals.get(cursor++));
        }
        return shown;
    }

    /** Same server timestamp correction as the vanilla RenderPIDS renderer. */
    public static long clientArrivalMillis(long epoch, long millisOffset) {
        return epoch - millisOffset;
    }

    public static long etaSeconds(long epoch, long millisOffset, long now) {
        return (epoch - millisOffset - now) / 1000;
    }
}
