#!/usr/bin/env python3
"""Add a non-distributed 1.21.1 Fabric Stonecutter target to MTR France source checkout."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
p = root / "gradle.properties"
s = p.read_text(encoding="utf-8")
s = "\n".join(line for line in s.splitlines() if not line.startswith("org.gradle.java.home=")) + "\n"
p.write_text(s, encoding="utf-8")

p = root / "settings.gradle.kts"
s = p.read_text(encoding="utf-8")
assert 'mc("1.20.4", "fabric")' in s and 'vcsVersion = "1.20.4-fabric"' in s, "Unexpected MTR France Stonecutter settings"
s = s.replace('mc("1.20.4", "fabric")', 'mc("1.20.4", "fabric")\n        mc("1.21.1", "fabric")')
s = s.replace('vcsVersion = "1.20.4-fabric"', 'vcsVersion = "1.21.1-fabric"')
p.write_text(s, encoding="utf-8")

p = root / "stonecutter.gradle.kts"
s = p.read_text(encoding="utf-8")
assert 'stonecutter active "1.20.4-fabric"' in s, "Unexpected Stonecutter active version"
p.write_text(s.replace('stonecutter active "1.20.4-fabric"', 'stonecutter active "1.21.1-fabric"'), encoding="utf-8")

p = root / "versions/1.21.1-fabric/gradle.properties"
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text("""loom.platform=fabric
dependency.fabric_loader=0.16.14
dependency.fabric_api=0.116.6+1.21.1
dependency.mod_menu=11.0.3
dependency.mtr=4.1.0-beta.2
dependency.universal_craft_minecraft=1.21.1
pack_format=34
""", encoding="utf-8")
print("MTR France 1.21.1 Stonecutter target prepared; actual 4.1 API compatibility not yet established.")
