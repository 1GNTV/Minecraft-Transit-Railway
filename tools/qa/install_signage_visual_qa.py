"""Create a separate Forge mod that loads the actual assembled user JAR."""
from pathlib import Path
import shutil
maven=Path("build/qa-maven/org/sillon/tested-build/1.0");maven.mkdir(parents=True,exist_ok=True)
shutil.copy2("build/signage-delivery/Sillon-Signaletique-Forge-1.20.1.jar",maven/"tested-build-1.0.jar")
(maven/"tested-build-1.0.pom").write_text('<project><modelVersion>4.0.0</modelVersion><groupId>org.sillon</groupId><artifactId>tested-build</artifactId><version>1.0</version></project>')
target=Path("tools/qa-runtime/src/main/java/org/sillon/qa");target.mkdir(parents=True,exist_ok=True)
shutil.copy2("tools/qa/SignageVisualQa.java",target/"SignageVisualQa.java")
shutil.copy2("tools/qa/StationDecorVisualQa.java",target/"StationDecorVisualQa.java")
shutil.copy2("tools/qa/BuilderVisualQa.java",target/"BuilderVisualQa.java")
run=Path("forge/run");run.mkdir(parents=True,exist_ok=True)
(run/"options.txt").write_text("onboardAccessibility:false\ntutorialStep:none\nguiScale:2\nfullscreen:false\nmaxFps:30\nrenderDistance:3\n")
