#!/usr/bin/env python3
"""Safely restore the real rail-gradient callback name used by JCM's mixin.

Only a unique javac-generated RenderRails.renderRailStats callback with the
exact expected argument descriptor may be renamed. Do not create dead shims.
The constant pool update preserves the invokedynamic bootstrap linkage and
all instructions. Apply to the already built shaded Forge JAR.
"""
from pathlib import Path
import re
import subprocess
import os
import tempfile
import zipfile
from patch_sillon_jcm_abi import rewrite_cp

TARGET="org/mtr/mod/render/RenderRails.class"
NAME="lambda$renderRailStats$24"
DESC="(Lorg/mtr/mapping/holder/BlockPos;DLorg/mtr/mapping/holder/BlockPos;"+("Ljava/lang/String;"*6)+"Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V"

def get_callbacks(jar):
    run=subprocess.run(["javap","-p","-s","-classpath",str(jar),"org.mtr.mod.render.RenderRails"],
                       check=True,capture_output=True,text=True)
    lines=run.stdout.splitlines()
    result={}
    for i,line in enumerate(lines):
        match=re.search(r"(lambda\$renderRailStats\$\d+)\s*\(",line)
        if match and i+1<len(lines) and "descriptor:" in lines[i+1]:
            result[match.group(1)]=lines[i+1].split("descriptor:",1)[1].strip()
    return result

def main():
    jars=[Path(os.environ["SILLON_JAR_PATH"])] if os.getenv("SILLON_JAR_PATH") else list(Path("forge/build/libs").glob("*-all.jar"))
    if len(jars)!=1:
        raise SystemExit(f"Expected exactly one shaded Forge JAR, got {jars}")
    jar=jars[0]
    callbacks=get_callbacks(jar)
    if len(callbacks)!=1:
        raise SystemExit(f"Expected exactly one real rail-stat callback, got {callbacks}")
    old, actual=next(iter(callbacks.items()))
    if actual!=DESC:
        raise SystemExit(f"JCM rail-gradient callback capture ABI mismatch: {old} {actual}; expected {DESC}")
    if old==NAME:
        print("PASS JCM rail-gradient ABI already stable; no patch needed")
        return
    handle,tmp=tempfile.mkstemp(prefix="sillon-railstat-",suffix=".jar",dir=jar.parent)
    os.close(handle)
    try:
        with zipfile.ZipFile(jar,"r") as source,zipfile.ZipFile(tmp,"w") as dest:
            dest.comment=source.comment
            for entry in source.infolist():
                content=source.read(entry)
                if entry.filename==TARGET:
                    content=rewrite_cp(content,{old:NAME})
                dest.writestr(entry,content)
        with zipfile.ZipFile(tmp) as proof:
            bad=proof.testzip()
            if bad: raise ValueError(f"Broken JAR member: {bad}")
        patched=get_callbacks(tmp)
        if patched!={NAME:DESC}:
            raise ValueError(f"Patch verification failed: {patched}")
        os.replace(tmp,jar)
        print(f"PASS real JCM rail gradient callback restored: {old} -> {NAME}")
    finally:
        if os.path.exists(tmp): os.unlink(tmp)

if __name__=="__main__": main()
