package org.mtr.mod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.Init;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JCM 2.x compatibility fallback: restore its own bundled built-in presets
 * if the JCM custom-resource reload hook did not populate its preset registry.
 * This class never bundles JCM assets, and does nothing if JCM is absent or
 * the presets are already present. Resource loading remains owned by JCM.
 */
public final class JcmPresetRecovery {
    private static final String MANAGER_CLASS = "com.lx862.jcm.mod.data.pids.PIDSManager";
    private static final String PRESET_RESOURCE = "/assets/jsblock/joban_custom_resources.json";
    private static final boolean ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("mtr.compat.jcmPresetRecovery", "true"));
    private static int attemptsLeft = 4;
    private static long nextCheck;
    private static boolean jcmMissing;

    private JcmPresetRecovery() {}

    /** Called when the resource manager reloads, but loaded only on a later client tick. */
    public static void invalidate() {
        attemptsLeft = 4;
        nextCheck = 0;
        jcmMissing = false;
    }

    /** Called from the Minecraft client thread. No repeated work after success. */
    public static void tick() {
        if (!ENABLED || attemptsLeft <= 0 || jcmMissing
                || MinecraftClient.getInstance().getWorldMapped() == null) return;
        long now = System.currentTimeMillis();
        if (nextCheck == 0) {
            nextCheck = now + 2500;
            return;
        }
        if (now < nextCheck) return;
        nextCheck = now + 5000;
        attemptsLeft--;
        try {
            Class<?> manager = Class.forName(MANAGER_CLASS);
            java.lang.reflect.Method getBuiltins = manager.getMethod("getBuiltInPresets");
            List<?> existing = (List<?>) getBuiltins.invoke(null);
            if (!existing.isEmpty()) {
                attemptsLeft = 0;
                return;
            }

            // Read from JCM's OWN JAR, not a copied asset in Sillon. This
            // bypasses a missing/out-of-order resource-manager callback only
            // when the ordinary JCM initialization produced zero built-ins.
            try (InputStream stream = manager.getResourceAsStream(PRESET_RESOURCE)) {
                if (stream == null) {
                    Init.LOGGER.warn("Sillon/JCM: built-in PIDS presets missing; JCM embedded JSON not found");
                    return;
                }
                JsonObject json;
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    json = JsonParser.parseReader(reader).getAsJsonObject();
                }
                if (!json.has("pids_images") || !json.get("pids_images").isJsonArray()) {
                    Init.LOGGER.warn("Sillon/JCM: invalid embedded preset JSON in Joban Client Mod");
                    return;
                }
                manager.getMethod("loadJson", JsonObject.class).invoke(null, json);
            }

            int recovered = ((List<?>) getBuiltins.invoke(null)).size();
            if (recovered > 0) {
                attemptsLeft = 0;
                Init.LOGGER.info("Sillon/JCM: restored {} built-in PIDS presets from the original JCM JAR", recovered);
            } else {
                Init.LOGGER.warn("Sillon/JCM: JCM built-in PIDS presets are still empty after recovery");
            }
        } catch (ClassNotFoundException missing) {
            // JCM is optional. Do not require its classes when launching Sillon.
            jcmMissing = true;
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            Init.LOGGER.warn("Sillon/JCM: failed to recover optional built-in PIDS presets", error);
        }
    }
}
