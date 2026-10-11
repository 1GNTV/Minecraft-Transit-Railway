package org.mtr.mod.render;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Cross-thread completion queue; stale/rejected work must release its CPU image. */
public final class GenerationUploadQueue {
    private record Entry(long generation, Runnable upload, Runnable discard) {}
    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong generation = new AtomicLong();
    private final int capacity;
    public GenerationUploadQueue(int capacity) { this.capacity = capacity; }
    public long generation() { return generation.get(); }
    public boolean offer(long token, Runnable upload, Runnable discard) {
        if (token != generation.get()) { discard.run(); return false; }
        while (true) {
            final int count = size.get();
            if (count >= capacity) { discard.run(); return false; }
            if (size.compareAndSet(count, count + 1)) break;
        }
        queue.add(new Entry(token, upload, discard));
        return true;
    }
    /** Render-thread only. Budget counts stale disposal as well as uploads. */
    public int drain(int budget) {
        int processed = 0;
        while (processed < budget) {
            final Entry entry = queue.poll();
            if (entry == null) break;
            size.decrementAndGet(); processed++;
            if (entry.generation() == generation.get()) entry.upload().run();
            else entry.discard().run();
        }
        return processed;
    }
    public void invalidate() { generation.incrementAndGet(); drain(Integer.MAX_VALUE); }
    public int size() { return size.get(); }
}
