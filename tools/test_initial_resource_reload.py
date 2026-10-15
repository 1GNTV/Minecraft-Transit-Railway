"""Sillon startup regression: missing MTR resource reload would hide ALL trains,
rails and JCM custom preset packs even if the independent rendering paths work."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
custom=(root/"fabric/src/main/java/org/mtr/mod/client/CustomResourceLoader.java").read_text()
init=(root/"fabric/src/main/java/org/mtr/mod/InitClient.java").read_text()
assert "private static volatile boolean INITIAL_RESOURCES_LOADED;" in custom
assert "public static boolean hasLoadedInitialResources()" in custom
assert "INITIAL_RESOURCES_LOADED = false;" in custom
assert "INITIAL_RESOURCES_LOADED = true;" in custom
assert custom.index("INITIAL_RESOURCES_LOADED = true;") > custom.index('Init.LOGGER.info("Loaded {} vehicles')
assert 'REGISTRY_CLIENT.eventRegistryClient.registerResourceReloadEvent(CustomResourceLoader::reload)' in init
assert 'CustomResourceLoader.hasLoadedInitialResources()' in init
assert 'CustomResourceLoader.reload();' in init
assert 'ensureInitialCustomResources();' in init
assert 'JcmPresetRecovery.tick();' in init
assert 'MinecraftClient.getInstance().getWorldMapped() == null' in init
assert 'missing initial resource reload; recovering models, rails and addon resources' in init
assert 'initialResourceRecoveryAttempted = true;' in init
assert "40" in init
print("PASS: missing initial MTR reload is detected and recovered once on client thread")
