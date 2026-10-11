package org.mtr.mod.render;

import java.util.List;
import java.util.function.BiPredicate;

/** Allocation-free partitioning; never moves a draw across a different state. */
public final class ConsecutiveRuns {
    @FunctionalInterface public interface Run { void accept(int start, int end); }
    private ConsecutiveRuns() {}
    public static <T> void visit(List<T> list, int maximum, BiPredicate<T, T> compatible, Run run) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        for (int start = 0; start < list.size();) {
            int end = start + 1;
            while (end < list.size() && end - start < maximum && compatible.test(list.get(start), list.get(end))) end++;
            run.accept(start, end);
            start = end;
        }
    }
}
