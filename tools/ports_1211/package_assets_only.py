#!/usr/bin/env python3
"""Package honest, NONFUNCTIONAL resource-only diagnostic JARs for Fabric 1.21.1.

These are *not* ports of JCM or MTR France: no Java code, registries, mixins,
block entities, trains, or client screens are included. DO NOT use in a world.
"""
from pathlib import Path
import argparse
import json
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--source", required=True)
parser.add_argument("--mod", choices=("jcm", "mtrfrance"), required=True)
parser.add_argument("--out", required=True)
args = parser.parse_args()

root = Path(args.source)
out = Path(args.out)
resources = root / ("fabric/src/main/resources" if args.mod == "jcm" else "src/main/resources")
assert resources.is_dir(), f"Resources not found: {resources}"

mod_id, title = (
    ("jsblock", "Joban Client Mod") if args.mod == "jcm"
    else ("mtrfranceaddon", "MTR France Addon")
)
metadata = {
    "schemaVersion": 1,
    "id": mod_id,
    "version": "0.0.0-sillon-1211-assets-only",
    "name": f"{title} - NONFUNCTIONAL ASSETS ONLY",
    "description": "Diagnostic assets-only JAR; no Java, blocks, trains, GUIs, mixins or registries. DO NOT USE IN WORLDS.",
    "authors": ["Sillon experimental port; original assets remain credited to upstream authors"],
    "license": "MIT",
    "environment": "*",
    "depends": {"fabricloader": ">=0.16.0", "minecraft": "1.21.1", "mtr": ">=4.1.0-beta.2"},
    "custom": {"sillon_port": {"status": "assets-only; NOT a functional port", "source": args.mod}},
}
out.parent.mkdir(parents=True, exist_ok=True)
count = 0
with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as jar:
    jar.writestr("fabric.mod.json", json.dumps(metadata, indent=2) + "\n")
    jar.writestr("SILLON-PORT-WARNING.txt",
                 "NONFUNCTIONAL DIAGNOSTIC ONLY. Contains upstream assets, no Java code.\n"
                 "Do NOT use in an existing world; blocks/trains/screens are NOT registered.\n"
                 "This is NOT a port of the original mod.\n")
    for p in sorted(resources.rglob("*")):
        if not p.is_file():
            continue
        rel = p.relative_to(resources).as_posix()
        if rel == "fabric.mod.json" or rel.endswith(".mixins.json") or rel.startswith("META-INF/"):
            continue
        if not (rel.startswith("assets/") or rel.startswith("data/") or rel.startswith("resourcepacks/")):
            continue
        jar.write(p, rel)
        count += 1
with zipfile.ZipFile(out) as jar:
    assert jar.testzip() is None
    assert "fabric.mod.json" in jar.namelist()
    assert not any(n.endswith(".class") for n in jar.namelist())
print(f"Packaged {out}: {count} upstream resource files; no code; NOT FUNCTIONAL.")
