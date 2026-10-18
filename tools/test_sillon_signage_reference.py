#!/usr/bin/env python3
"""Execute the real Java compositor and editor rules without starting Minecraft."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
out=root/"build/signage-headless"
stub=out/"src/org/mtr/mapping/holder/CompoundTag.java"
stub.parent.mkdir(parents=True,exist_ok=True)
types={"String":"String","Int":"int","Float":"float","Boolean":"boolean"}
defaults={"String":'""',"Int":"0","Float":"0f","Boolean":"false"}
methods=[]
for suffix,typ in types.items():
    wrapper={"int":"Integer","float":"Float","boolean":"Boolean","String":"String"}[typ]
    methods.append(f"public void put{suffix}(String key,{typ} value){{values.put(key,value);}}")
    methods.append(f"public {typ} get{suffix}(String key){{return ({wrapper})values.getOrDefault(key,{defaults[suffix]});}}")
stub.write_text("package org.mtr.mapping.holder; import java.util.*; public final class CompoundTag {private final Map<String,Object> values=new HashMap<>();"+"".join(methods)+"public String toString(){StringBuilder b=new StringBuilder(\"{\");values.forEach((k,v)->{if(b.length()>1)b.append(\",\");b.append(k).append(\":\");if(v instanceof String)b.append((char)34).append(((String)v).replace(\"\\\\\",\"\\\\\\\\\").replace(\"\\\"\",\"\\\\\\\"\").replace(\"\\n\",\"\\\\n\")).append((char)34);else if(v instanceof Boolean)b.append((Boolean)v?\"1b\":\"0b\");else b.append(v);});return b.append(\"}\").toString();}"+"}",encoding="utf-8")
base=root/"fabric/src/main/java/org/mtr/mod"
sources=[stub,root/"tools/signage/SignageRegression.java"]
for folder,names in {
"data":["SillonSignageOptions","SillonSignageRow","SillonSignageSpec","SillonSignagePresets","SillonSignageIconCatalog"],
"client":["SillonSignagePainter","SillonRATPSignageLayouts","SillonSignageGeometry"],
"screen":["SillonSignageEditorModel","SillonSignageEditorLayout"]}.items():
    sources.extend(base/folder/(name+".java") for name in names)
subprocess.run(["javac","--release","17","-encoding","UTF-8","-d",str(out/"classes"),*[str(p) for p in sources]],check=True)
sep=";" if __import__("os").name=="nt" else ":"
subprocess.run(["java","-Djava.awt.headless=true","-cp",str(out/"classes")+sep+str(root/"fabric/src/main/resources"),"SignageRegression"],check=True,cwd=root)
