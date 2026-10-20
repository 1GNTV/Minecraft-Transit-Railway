"""Exercise production geometry and persistence, with an isolated config directory."""
from pathlib import Path
import subprocess,os
root=Path(__file__).resolve().parents[1]
out=root/'build/builder-headless';out.mkdir(parents=True,exist_ok=True)
jar=root/'build/signage-delivery/Sillon-Signaletique-Forge-1.20.1.jar'
source=root/'fabric/src/main/java/org/mtr/mod/builder'
subprocess.run(['javac','--release','17','-cp',str(jar),'-d',str(out),*[str(source/(x+'.java')) for x in ['MapGeometry','DoorGuideGeometry','ConsistPresets']],str(root/'tools/builder/BuilderRegression.java')],check=True)
run=out/'run';run.mkdir(exist_ok=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+str(jar),'BuilderRegression'],cwd=run,check=True)
# Existing packet discriminators must retain their exact order.
import re
init=(root/'fabric/src/main/java/org/mtr/mod/Init.java').read_text()
names=re.findall(r'REGISTRY.registerPacket\(([^,]+)',init)
assert all('Builder' in n for n in names[-2:]) and not any('Builder' in n for n in names[:-2]),names
print('PASS appended builder packets preserve existing discriminator order')
