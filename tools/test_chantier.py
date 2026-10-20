"""Geometry plus actual Transport Simulation Core transactions; no screenshot mocks."""
from pathlib import Path
import subprocess,os
root=Path(__file__).resolve().parents[1]
out=root/'build/chantier-headless';out.mkdir(parents=True,exist_ok=True)
jar=root/'build/signage-delivery/Sillon-Signaletique-Forge-1.20.1.jar'
source=root/'fabric/src/main/java/org/mtr/mod/chantier'
subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',str(jar),'-d',str(out),*[str(source/(n+'.java')) for n in ['ChantierPlan','ChantierRaster','ChantierMesh','ChantierNetwork']],str(root/'tools/chantier/ChantierRegression.java'),str(root/'tools/chantier/ChantierNetworkRegression.java')],check=True)
for test in ['ChantierRegression','ChantierNetworkRegression']:
 subprocess.run(['java','-cp',str(out)+os.pathsep+str(jar),test],check=True)
