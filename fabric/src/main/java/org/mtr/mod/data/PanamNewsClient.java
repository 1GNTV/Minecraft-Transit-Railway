package org.mtr.mod.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Shared, immutable client-side snapshot of operator messages (never a live traffic feed). */
public final class PanamNewsClient {
    private static String global = "";
    private static Map<Long, String> byRoute = Collections.emptyMap();
    private static long revision;

    private PanamNewsClient() {}

    public static synchronized void update(String globalMessage, long[] ids, String[] messages) {
        HashMap<Long, String> next = new HashMap<>();
        for (int i = 0; i < ids.length && i < messages.length && i < 128; i++) {
            if (!messages[i].isEmpty()) next.put(ids[i], messages[i]);
        }
        global = globalMessage;
        byRoute = Collections.unmodifiableMap(next);
        revision++;
    }

    public static synchronized void reset() {
        global = "";
        byRoute = Collections.emptyMap();
        revision++;
    }

    public static long revision() {
        return revision;
    }

    public static String global() {
        return global;
    }

    public static String forRoute(long route) {
        return byRoute.getOrDefault(route, "");
    }

    /** Immutable full-network snapshot, including lines not serving this station. */
    public static Map<Long,String> routes() {
        return byRoute;
    }
}
