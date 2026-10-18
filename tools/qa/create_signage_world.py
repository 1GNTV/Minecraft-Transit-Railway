from pathlib import Path
import subprocess,shutil,json
run=Path("forge/run");run.mkdir(parents=True,exist_ok=True)
(run/"eula.txt").write_text("eula=true\n")
props={
"level-name":"signage-qa-world","level-type":"minecraft:flat","level-seed":"7331","online-mode":"false",
"view-distance":"3","simulation-distance":"3","max-tick-time":"120000","spawn-protection":"0","allow-flight":"true",
"generate-structures":"false","spawn-npcs":"false","spawn-animals":"false","spawn-monsters":"false",
"generator-settings":json.dumps({"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2},{"block":"minecraft:quartz_block","height":1}],"structure_overrides":[]},separators=(",",":"))}
(run/"server.properties").write_text("".join(k+"="+v+"\n" for k,v in props.items()))
commands=[
"gamerule doDaylightCycle false","gamerule doWeatherCycle false","gamerule doMobSpawning false",
"time set 6000","weather clear","setworldspawn 0 5 -12",
"fill -22 4 1 22 26 1 minecraft:quartz_block",
"fill -22 3 -22 22 3 2 minecraft:smooth_quartz"]
commands+=Path("build/signage-qa/fixtures-commands.txt").read_text().splitlines()
(run/"signage-world-commands.txt").write_text("\n".join(commands)+"\n",encoding="utf-8")
subprocess.run(["../../gradlew","runServer","-Dorg.gradle.jvmargs=-Xmx3G"],cwd="tools/qa-runtime",check=True,timeout=360)
if not (run/"signage-world-qa-success.txt").is_file():raise RuntimeError("Dedicated server did not verify the fixture documents")
destination=run/"saves/SignageQA"
shutil.copytree(run/"signage-qa-world",destination,dirs_exist_ok=True)
print("SIGNAGE_QA_WORLD_READY",destination)
