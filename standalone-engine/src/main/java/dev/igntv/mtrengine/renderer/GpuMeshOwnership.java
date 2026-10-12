package dev.igntv.mtrengine.renderer;

import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.object.VertexArray;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Own buffers added by Work compilation. Never retain the VAO through its cleanup action. */
public final class GpuMeshOwnership {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final WeakHashMap<VertexArray, Lease> LEASES = new WeakHashMap<>();
    private static final class Shared {
        final Mesh mesh;
        final AtomicInteger references = new AtomicInteger();
        Shared(Mesh mesh) { this.mesh = mesh; }
    }
    private static final class Lease implements Runnable {
        final Shared shared;
        Cleaner.Cleanable cleanable;
        volatile boolean released;
        Lease(Shared shared) { this.shared = shared; shared.references.incrementAndGet(); }
        @Override public synchronized void run() {
            if (released) return;
            released = true;
            // Mapping buffer close dispatches to the render thread when necessary.
            if (shared.references.decrementAndGet() == 0) shared.mesh.close();
        }
    }
    private GpuMeshOwnership() {}
    public static synchronized void attach(VertexArray array, Mesh mesh) {
        if (LEASES.containsKey(array)) throw new IllegalStateException("VAO already owns a Work mesh");
        bind(array, new Shared(mesh));
    }
    private static void bind(VertexArray array, Shared shared) {
        final Lease lease = new Lease(shared);
        lease.cleanable = CLEANER.register(array, lease);
        LEASES.put(array, lease);
    }
    public static synchronized void alias(VertexArray alias, VertexArray original) {
        final Lease lease = LEASES.get(original);
        if (lease != null && !lease.released) bind(alias, lease.shared);
        Reference.reachabilityFence(original);
    }
    public static synchronized void close(VertexArray array) {
        final Lease lease = LEASES.remove(array);
        if (lease != null) lease.cleanable.clean();
    }
}
