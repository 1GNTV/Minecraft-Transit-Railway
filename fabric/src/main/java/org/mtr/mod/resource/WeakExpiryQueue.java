package org.mtr.mod.resource;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.function.ToLongFunction;

/** Ordered live deadlines, with no strong owner references or scan of idle registry capacity. */
public final class WeakExpiryQueue<T> {
    public static final long DONE = Long.MIN_VALUE;
    public static final class Ticket<T> extends WeakReference<T> {
        private long deadline;
        private final long sequence;
        private Ticket(T owner, ReferenceQueue<T> queue, long deadline, long sequence) {
            super(owner, queue); this.deadline = deadline; this.sequence = sequence;
        }
    }
    private final ReferenceQueue<T> collected = new ReferenceQueue<>();
    private final TreeSet<Ticket<T>> deadlines = new TreeSet<>(Comparator
            .<Ticket<T>>comparingLong(ticket -> ticket.deadline).thenComparingLong(ticket -> ticket.sequence));
    private long sequence;

    public synchronized Ticket<T> add(T owner, long deadline) {
        Ticket<T> ticket = new Ticket<>(owner, collected, deadline, sequence++);
        deadlines.add(ticket);
        return ticket;
    }
    public synchronized void remove(Ticket<T> ticket) { deadlines.remove(ticket); }
    public synchronized int size() { return deadlines.size(); }
    public synchronized void expire(long now, ToLongFunction<T> nextDeadline) {
        for (var reference = collected.poll(); reference != null; reference = collected.poll()) deadlines.remove(reference);
        while (!deadlines.isEmpty() && deadlines.first().deadline < now) {
            Ticket<T> ticket = deadlines.pollFirst();
            T owner = ticket.get();
            if (owner == null) continue;
            long next = nextDeadline.applyAsLong(owner);
            // Strict expiry matches CachedResource: an object at its deadline is still valid.
            if (next != DONE && next >= now) {
                ticket.deadline = next;
                deadlines.add(ticket);
            }
        }
    }
}
