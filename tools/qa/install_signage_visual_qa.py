from pathlib import Path
import shutil
target=Path("forge/src/main/java/org/mtr/init")
shutil.copy2("tools/qa/SignageVisualQa.java",target/"SignageVisualQa.java")
main=target/"MTR.java"
source=main.read_text()
source=source.replace("DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> InitClient::init);",
"DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> InitClient::init);\n        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> SignageVisualQa::register);")
main.write_text(source)
run=Path("forge/run");run.mkdir(parents=True,exist_ok=True)
(run/"options.txt").write_text("onboardAccessibility:false\nguiScale:2\nfullscreen:false\nmaxFps:30\nrenderDistance:3\n")

gradle=Path("forge/build.gradle")
with gradle.open("a") as f:
    f.write('\nminecraft.runs.client { args "--quickPlaySingleplayer", "SignageQA"; jvmArgs "-Xmx3G" }\n')
