from pathlib import Path
import subprocess,threading,queue,time,shutil,sys,json
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
process=subprocess.Popen(["./gradlew","forge:runServer","-PminecraftVersion=1.20.1","-Dorg.gradle.jvmargs=-Xmx3G"],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
lines=queue.Queue()
def read():
    for line in process.stdout:lines.put(line)
threading.Thread(target=read,daemon=True).start()
started=False;done_at=None;deadline=time.time()+600
while process.poll() is None and time.time()<deadline:
    try:line=lines.get(timeout=1);print(line,end="",flush=True)
    except queue.Empty:line=""
    if not started and 'Done (' in line:
        started=True
        for command in commands:process.stdin.write(command+"\n")
        process.stdin.flush();done_at=time.time()
    if started and time.time()-done_at>15:
        process.stdin.write("save-all\nstop\n");process.stdin.flush();done_at=float("inf")
if process.poll() is None:process.terminate();raise RuntimeError("QA server timed out")
if process.returncode:raise RuntimeError("QA server failed")
if not started:raise RuntimeError("QA world never started")
destination=run/"saves/SignageQA"
shutil.copytree(run/"signage-qa-world",destination,dirs_exist_ok=True)
print("SIGNAGE_QA_WORLD_READY",destination)
