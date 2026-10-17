#!/usr/bin/env python3
"""Apply reproducible, source-only first-pass Fabric 1.21.1 migration to JCM v2."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
p = root / "gradle.properties"
s = p.read_text(encoding="utf-8")
assert "minecraft_version=1.20.4" in s and "mtr_version=4.0.5" in s, "Unexpected JCM baseline"
p.write_text(s.replace("minecraft_version=1.20.4", "minecraft_version=1.21.1").replace("mtr_version=4.0.5", "mtr_version=4.1.0-beta.2"), encoding="utf-8")

p = root / "fabric/gradle.properties"
s = p.read_text(encoding="utf-8")
s = s.replace("loom_version=1.10-SNAPSHOT", "loom_version=1.17.14")
s += """
# Sillon 1.21.1 port: versioned build dependencies
yarn_mappings_1.21.1=1.21.1+build.3
fabric_api_version_1.21.1=0.116.6+1.21.1
mod_menu_version_1.21.1=11.0.3
"""
p.write_text(s, encoding="utf-8")

p = root / "settings.gradle"
s = p.read_text(encoding="utf-8")
# This port is Fabric-only: don't evaluate the incompatible ForgeGradle plugin under Gradle 9.
assert 'include ("forge")' in s, "Unexpected JCM settings"
p.write_text(s.replace('include ("forge")', '// Forge omitted for Fabric 1.21.1 port'), encoding="utf-8")

p = root / "gradle/wrapper/gradle-wrapper.properties"
s = p.read_text(encoding="utf-8")
s = s.replace("gradle-8.14-bin.zip", "gradle-9.5.1-bin.zip")
p.write_text(s, encoding="utf-8")

p = root / "fabric/src/main/resources/fabric.mod.json"
s = p.read_text(encoding="utf-8")
assert '>=4.0.5 <4.1' in s, "Unexpected JCM MTR dependency"
p.write_text(s.replace('>=4.0.5 <4.1', '>=4.1.0-beta.2 <4.2'), encoding="utf-8")
print("JCM 1.21.1 dependency and metadata port scaffolding prepared; source API migration remains to be validated.")
