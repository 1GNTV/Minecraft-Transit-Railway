#!/usr/bin/env python3
"""Restore JCM 2.3.0-beta.1 lambda callback names in the final Sillon Forge JAR.

javac-generated synthetic lambda names are not stable APIs. Sillon retains the
real callback bodies/captures, but moves their synthetic identifiers back to the
names used by the original MTR ABI. This fixes the actual invokedynamic handles,
not dummy unused compatibility methods.

The patch changes only CONSTANT_Utf8 entries in two class files; no instructions,
control flow, stack maps, descriptors, or class loading order are changed.
Build-time only: zero runtime dispatch cost. Abort on unexpected compiler output.
"""
from pathlib import Path
import os
import struct
import tempfile
import zipfile

TARGETS = {
    "org/mtr/mod/render/RenderVehicles.class": {
        # Relocate the unrelated existing index to a stable internal name.
        "lambda$render$12": "sillon$renderRidingEntities",
        "lambda$render$11": "lambda$render$12",
    },
    "org/mtr/mod/render/RenderRails.class": {
        "lambda$render$5": "sillon$renderEditorOverlay",
        "lambda$render$4": "lambda$render$5",
    },
}

def rewrite_cp(data, names):
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java class file")
    cp_count = struct.unpack_from(">H", data, 8)[0]
    out = bytearray(data[:10])
    offset = 10
    i = 1
    hits = {key: 0 for key in names}
    while i < cp_count:
        tag = data[offset]
        out.append(tag)
        offset += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, offset)[0]
            value = data[offset + 2:offset + 2 + length]
            key = value.decode("utf-8", "replace")
            if key in names:
                hits[key] += 1
                value = names[key].encode("utf-8")
            out += struct.pack(">H", len(value)) + value
            offset += 2 + length
        else:
            widths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2,
                      9: 4, 10: 4, 11: 4, 12: 4, 15: 3,
                      16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
            if tag not in widths:
                raise ValueError(f"Unsupported constant-pool tag {tag}")
            width = widths[tag]
            out += data[offset:offset + width]
            offset += width
            if tag in (5, 6):
                i += 1
        i += 1
    missing = [key for key, count in hits.items() if count != 1]
    if missing:
        raise ValueError(f"Unexpected javac lambda names/counts: {hits} ({missing})")
    out += data[offset:]
    return bytes(out)

def main():
    jars = [Path(os.environ["SILLON_JAR_PATH"])] if os.getenv("SILLON_JAR_PATH") else list(Path("forge/build/libs").glob("*-all.jar"))
    if len(jars) != 1:
        raise SystemExit(f"Expected exactly one Forge shaded JAR, found {jars}")
    target = jars[0]
    fd, temp_name = tempfile.mkstemp(prefix="sillon-jcm-", suffix=".jar", dir=target.parent)
    os.close(fd)
    try:
        found = set()
        with zipfile.ZipFile(target, "r") as src, zipfile.ZipFile(temp_name, "w") as dst:
            dst.comment = src.comment
            for item in src.infolist():
                content = src.read(item)
                if item.filename in TARGETS:
                    content = rewrite_cp(content, TARGETS[item.filename])
                    found.add(item.filename)
                    print(f"Restored real JCM callback ABI: {item.filename}")
                dst.writestr(item, content)
        if found != set(TARGETS):
            raise ValueError(f"Missing expected classes: {set(TARGETS) - found}")
        with zipfile.ZipFile(temp_name) as proof:
            bad = proof.testzip()
            if bad:
                raise ValueError(f"Corrupted JAR member: {bad}")
        os.replace(temp_name, target)
        print(f"PASS: safely patched {target.name} without changing render instructions")
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)

if __name__ == "__main__":
    main()
