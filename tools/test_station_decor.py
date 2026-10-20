#!/usr/bin/env python3
from pathlib import Path
import subprocess,os
root=Path(__file__).resolve().parents[1];out=root/'build/decor-headless';out.mkdir(parents=True,exist_ok=True)
stub=root/'build/signage-headless/src/org/mtr/mapping/holder/CompoundTag.java'
if not stub.exists():subprocess.run(['python','tools/test_sillon_signage_reference.py'],cwd=root,check=True)
stub_target=out/'src/org/mtr/mapping/holder/CompoundTag.java';stub_target.parent.mkdir(parents=True,exist_ok=True)
stub_target.write_text(stub.read_text().replace('public final class CompoundTag {','public final class CompoundTag { public boolean contains(String key){return values.containsKey(key);}'));stub=stub_target
src=root/'fabric/src/main/java/org/mtr/mod/decor'
subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(out),str(stub),*[str(p) for p in src.glob('*.java')],str(root/'tools/decor/DecorRegression.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-Xmx512m','-cp',str(out)+os.pathsep+str(root/'fabric/src/main/resources'),'DecorRegression'],check=True,cwd=root)
