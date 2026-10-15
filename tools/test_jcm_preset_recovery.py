"""Static integration regression check for JCM's own built-in PIDS presets.

The external Joban Client Mod supplies assets/jsblock/joban_custom_resources.json.
Sillon must not copy that file or require JCM as a hard dependency.
"""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
code=(root/"fabric/src/main/java/org/mtr/mod/client/JcmPresetRecovery.java").read_text()
init=(root/"fabric/src/main/java/org/mtr/mod/InitClient.java").read_text()
assert 'getBuiltInPresets' in code
assert 'if (!existing.isEmpty())' in code, "Do not overwrite working JCM presets"
assert 'manager.getResourceAsStream(PRESET_RESOURCE)' in code
assert '/assets/jsblock/joban_custom_resources.json' in code
assert 'getMethod("loadJson", JsonObject.class)' in code
assert 'ClassNotFoundException' in code
assert 'attemptsLeft' in code and 'nextCheck' in code
assert 'JcmPresetRecovery.tick()' in init
assert 'JcmPresetRecovery::invalidate' in init
assert not (root/"fabric/src/main/resources/assets/jsblock/joban_custom_resources.json").exists(), "Do not redistribute JCM presets"
print("PASS: conditional, bounded and resource-owning JCM preset recovery")
