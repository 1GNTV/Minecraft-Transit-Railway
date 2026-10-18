"""Add test-only shader runtime dependencies after the user JAR has already been staged."""
from pathlib import Path
from urllib.request import urlopen,Request
import hashlib,json
run=Path("forge/run")
shader=run/"shaderpacks/ComplementaryReimagined_r5.3.zip"
shader.parent.mkdir(parents=True,exist_ok=True)
url="https://cdn.modrinth.com/data/HVnmMxH1/versions/71Hn3myZ/ComplementaryReimagined_r5.3.zip"
shader.write_bytes(urlopen(Request(url,headers={"User-Agent":"Sillon personal compatibility QA"}),timeout=60).read())
config=run/"config";config.mkdir(parents=True,exist_ok=True)
for name in ["oculus.properties","iris.properties"]:
    (config/name).write_text("shaderPack=ComplementaryReimagined_r5.3.zip\nenableShaders=true\ndisableUpdateMessage=true\nmaxShadowRenderDistance=4\n")
with Path("tools/qa-runtime/build.gradle").open("a") as f:
    f.write('''
dependencies {
    runtimeOnly fg.deobf("maven.modrinth:GchcoXML:iQ1SwGc3")
    runtimeOnly fg.deobf("maven.modrinth:sk9rgfiA:UTbfe5d1")
}
minecraft.runs.client { jvmArgs "-Dsillon.qa.shader=true" }
''')
out=Path("build/signage-qa");out.mkdir(parents=True,exist_ok=True)
(out/"shader-runtime.json").write_text(json.dumps({
"oculus":"1.20.1-1.8.0 (iQ1SwGc3)","embeddium":"0.3.31+mc1.20.1 (UTbfe5d1)",
"shader":"Complementary Reimagined r5.3","shader_url":url,
"shader_sha256":hashlib.sha256(shader.read_bytes()).hexdigest(),
"renderer":"Mesa llvmpipe in CI; no hardware FPS claim"},indent=2))
# Keep shader cost small on the software rasterizer.
(shader.parent/(shader.name+".txt")).write_text("SHADOW_QUALITY=0\nSHADOW_DISTANCE=32.0\n")
