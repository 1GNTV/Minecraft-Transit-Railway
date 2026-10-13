#!/usr/bin/env python3
"""Guard the legacy RenderRails.render() local-capture ABI used by JCM/JSBlock.

JCM 2.3.0-beta.1 injects three times at renderRailStats and captures
minecraftClient, clientWorld, clientPlayerEntity, cullingTasks, cameraPosition,
camera, holdingRailRelated, railsToRender, hoverRails, plus brush/rail locals.
A seemingly harmless new live local before these calls crashes Mixin at runtime.
"""
from pathlib import Path
import re

src = Path("fabric/src/main/java/org/mtr/mod/render/RenderRails.java").read_text()
body = src.split("public static void render() {", 1)[1].split(
    "public static boolean isHoldingRailRelated", 1)[0]
ordered = [
    "final MinecraftClient minecraftClient",
    "final ClientWorld clientWorld",
    "final ClientPlayerEntity clientPlayerEntity",
    "final ObjectArrayList<Function<OcclusionCullingInstance, Runnable>> cullingTasks",
    "final Vector3d cameraPosition",
    "final Vec3d camera",
    "final boolean holdingRailRelated",
    "final ObjectArrayList<Rail> railsToRender",
    "final ObjectArraySet<Rail> hoverRails",
]
locations = [body.index(token) for token in ordered]
assert locations == sorted(locations), "JCM/JSBlock expected local order changed"
prefix = body[:locations[7]]
assert "final boolean shadowPass" not in prefix, "Shadow flag now leaks into JCM local captures"
assert "final boolean editorOverlay" not in prefix, "Editor flag now leaks into JCM local captures"
scope_begin = body.index("// JCM/JSBlock captures")
scope_end = body.index("} // End the spatial-index/metrics locals")
brush_begin = body.index("// Ghost rails (when holding brush)")
assert locations[7] < scope_begin < scope_end < brush_begin < locations[8]
scope = body[scope_begin:scope_end]
for name in ("shadowPass", "data", "railCandidates", "railScanStartNanos", "railCandidateCount", "visibleRailCount"):
    assert re.search(r"\b" + name + r"\b", scope), f"Missing scoped optimization: {name}"
for name in ("editorOverlay",):
    assert not re.search(r"\b" + name + r"\b", body), f"Unexpected live local: {name}"
assert body.count("renderRailStats(") >= 4, "Expected original rail-stat injection call sites"
assert "if (!OptimizedRenderer.renderingShadows())" in body
print("PASS: JCM/JSBlock rail-stat local-capture shape remains protected; indexed rendering preserved")
