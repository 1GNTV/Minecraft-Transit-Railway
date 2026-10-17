#!/usr/bin/env python3
"""Strict human-evidence release gate. Normal CI builds are experimental only.

This checks evidence presence, NOT its truth. Reviewers must inspect screenshots,
logs, benchmark captures and test notes before marking a requirement validated.
"""
import json
import sys
from pathlib import Path

root=Path(__file__).resolve().parents[1]
data=json.loads((root/"docs/SILLON-NEXT-EVIDENCE.json").read_text(encoding="utf-8"))
required={
    "gpu_rail_culling","multi_draw_indirect","train_instancing","multi_material_atlas",
    "gpu_scene_selection","shader_compatibility","rail_geometry_cache_invalidation",
    "rail_lighting_and_visibility","train_motion_and_fps_regressions",
    "panam_shader_display","panam_ui_item_brush","panam_network_line_badges",
    "jcm_compatibility","mtr_france_compatibility","dense_station_benchmarks",
    "forge_1201_build","fabric_1204_build"
}
errors=[]
records=data.get("requirements",{})
for name in sorted(required):
    item=records.get(name,{})
    for platform in ("forge-1201","fabric-1204"):
        result=item.get(platform,{})
        links=result.get("evidence",[])
        if result.get("validated") is not True or not isinstance(links,list) or not links:
            errors.append(f"{name} [{platform}] not independently validated")
for extra in sorted(set(records)-required):
    errors.append(f"Unknown requirement: {extra}")
if errors:
    print(f"BLOCKED: Sillon NEXT release gate: {len(errors)} missing/invalid platform checks")
    for message in errors:
        print(" - "+message)
    sys.exit(1)
print("Evidence manifest complete. HUMAN REVIEW STILL REQUIRED before release.")
