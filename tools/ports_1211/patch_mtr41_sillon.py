#!/usr/bin/env python3
"""First real Sillon optimization transplant onto upstream MTR 4.1 Stonecutter.

This intentionally ports ONLY the bounded rail-editor node scan cache.
It is not a full Sillon SOLIS port. The upstream render pipeline must remain intact.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
p = root / "src/main/java/org/mtr/render/RenderRails.java"
s = p.read_text(encoding="utf-8")
assert "package org.mtr.render;" in s, "Expected MTR 4.1 source"
assert "import java.util.Collections;" in s, "Unexpected imports"
assert "private static final int INVALID_NODE_CHECK_RADIUS = 16;" in s, "Unexpected render class"
assert "EDITOR_NODE_CACHE_TTL_NANOS" not in s, "Patch already applied"

s = s.replace(
    "import java.util.Collections;",
    "import java.util.ArrayList;\nimport java.util.Collections;",
    1,
)
s = s.replace(
    "\tprivate static final int INVALID_NODE_CHECK_RADIUS = 16;",
    "\tprivate static final int INVALID_NODE_CHECK_RADIUS = 16;\n"
    "\t// Sillon: cache node coordinates, never the mutable block states.\n"
    "\tprivate static final long EDITOR_NODE_CACHE_TTL_NANOS = 250_000_000L;\n"
    "\tprivate static final ArrayList<BlockPos> EDITOR_NODE_CANDIDATES = new ArrayList<>();\n"
    "\tprivate static ClientLevel editorNodeCacheWorld;\n"
    "\tprivate static BlockPos editorNodeCacheOrigin;\n"
    "\tprivate static long editorNodeCacheTimestamp;",
    1,
)

start_marker = "\t\t\t// Render nodes with the connected block state but isn't actually connected\n"
end_marker = "\n\t\t}\n\t}\n\n\tpublic static boolean isHoldingRailRelated"
assert s.count(start_marker) == 1 and s.count(end_marker) == 1, "Unexpected render node loop"
start = s.index(start_marker)
end = s.index(end_marker, start)
replacement = """\t\t\t// Sillon: avoid 35,937 block-state lookups on every render pass while editing.
\t\t\t// Rebuild on world/position changes or every 250 ms so newly placed nodes appear.
\t\t\tfinal BlockPos playerBlock = clientPlayerEntity.blockPosition();
\t\t\tfinal long now = System.nanoTime();
\t\t\tif (editorNodeCacheWorld != clientWorld || editorNodeCacheOrigin == null ||
\t\t\t\t!editorNodeCacheOrigin.equals(playerBlock) ||
\t\t\t\tnow - editorNodeCacheTimestamp >= EDITOR_NODE_CACHE_TTL_NANOS) {
\t\t\t\tEDITOR_NODE_CANDIDATES.clear();
\t\t\t\tfor (int x = -INVALID_NODE_CHECK_RADIUS; x <= INVALID_NODE_CHECK_RADIUS; x++) {
\t\t\t\t\tfor (int y = -INVALID_NODE_CHECK_RADIUS; y <= INVALID_NODE_CHECK_RADIUS; y++) {
\t\t\t\t\t\tfor (int z = -INVALID_NODE_CHECK_RADIUS; z <= INVALID_NODE_CHECK_RADIUS; z++) {
\t\t\t\t\t\t\tfinal BlockPos pos = playerBlock.offset(x, y, z);
\t\t\t\t\t\t\tif (clientWorld.getBlockState(pos).getBlock() instanceof BlockNode) {
\t\t\t\t\t\t\t\tEDITOR_NODE_CANDIDATES.add(pos);
\t\t\t\t\t\t\t}
\t\t\t\t\t\t}
\t\t\t\t\t}
\t\t\t\t}
\t\t\t\teditorNodeCacheWorld = clientWorld;
\t\t\t\teditorNodeCacheOrigin = playerBlock;
\t\t\t\teditorNodeCacheTimestamp = now;
\t\t\t}
\t\t\tfor (final BlockPos blockPos : EDITOR_NODE_CANDIDATES) {
\t\t\t\tfinal BlockState blockState = clientWorld.getBlockState(blockPos);
\t\t\t\tif (blockState.getBlock() instanceof BlockNode) {
\t\t\t\t\trenderNode(blockState, blockPos, () ->
\t\t\t\t\t\tblockState.getValue(BlockNode.IS_CONNECTED) &&
\t\t\t\t\t\t!MinecraftClientData.getInstance().positionsToRail.containsKey(MTR.blockPosToPosition(blockPos)),
\t\t\t\t\t\tMainRenderer.getFlashingLight());
\t\t\t\t}
\t\t\t}"""
s = s[:start] + replacement + s[end:]
assert s.count("EDITOR_NODE_CANDIDATES") >= 3
assert "for (int x = -INVALID_NODE_CHECK_RADIUS" in s
p.write_text(s, encoding="utf-8")
print("Ported Sillon rail-editor node scan cache onto MTR 4.1 source.")
