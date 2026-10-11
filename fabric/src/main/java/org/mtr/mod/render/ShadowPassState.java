package org.mtr.mod.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Optional Oculus state read; absence/failure leaves the mappings' original detector intact. */
public final class ShadowPassState {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.next.shadowState", "true"));
    private static volatile MethodHandle reader = ENABLED ? findReader() : null;
    private ShadowPassState() {}

    private static MethodHandle findReader() {
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState", false, ShadowPassState.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(type, "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    /** Read each time: main and shadow passes can switch within one frame. Null means original fallback. */
    public static Boolean current() {
        MethodHandle value = reader;
        if (value == null) return null;
        try {
            return (boolean) value.invokeExact();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            reader = null;
            return null;
        }
    }
}
