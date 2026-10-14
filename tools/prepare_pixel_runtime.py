"""Prepare arguments for an official Forge installation in the isolated lab only."""
from pathlib import Path
import json,os,urllib.request,hashlib,shutil,zipfile,re
ROOT=Path(__file__).resolve().parents[1]
GAME=Path(os.environ['TEMP'])/'sillon-pixel-runtime/game'
assert GAME.resolve().is_relative_to(Path(os.environ['TEMP']).resolve())
version=json.loads((GAME/'versions/1.20.1-forge-47.4.28/1.20.1-forge-47.4.28.json').read_text())
vanilla=json.loads((GAME/'versions/1.20.1/1.20.1.json').read_text())
cache=Path(os.environ['TEMP'])/'codex-sillon-gradle'
jar_cache={}
for p in cache.rglob('*.jar'):jar_cache.setdefault(p.name,[]).append(p)
def allowed(rules):
    allow=not rules
    for rule in rules:
        osrule=rule.get('os',{})
        if osrule.get('name','windows')!='windows' or osrule.get('arch','x86_64') not in ('x86_64','amd64'):continue
        if rule.get('features'):continue
        allow=rule['action']=='allow'
    return allow
def maven_path(name):
    parts=name.split(':');group,artifact,ver=parts[:3];return '/'.join([group.replace('.','/'),artifact,ver,artifact+'-'+ver+('-'+parts[3] if len(parts)>3 else '')+'.jar'])
def library(meta):
    art=meta.get('downloads',{}).get('artifact',{});rel=art.get('path',maven_path(meta['name']));path=GAME/'libraries'/rel
    if not path.exists():
        path.parent.mkdir(parents=True,exist_ok=True);sha=art.get('sha1')
        existing=next((p for p in jar_cache.get(path.name,[]) if not sha or hashlib.sha1(p.read_bytes()).hexdigest()==sha),None)
        if existing:shutil.copyfile(existing,path)
        else:
            url=art.get('url',meta.get('url','https://libraries.minecraft.net/')+rel)
            print('Fetching',path.name,flush=True);urllib.request.urlretrieve(url,path)
        if sha:assert hashlib.sha1(path.read_bytes()).hexdigest()==sha,path
    return path
libs={m['name']:m for m in vanilla['libraries']+version['libraries']}
classpath=[];natives=GAME/'natives';natives.mkdir(exist_ok=True)
for meta in libs.values():
    if not allowed(meta.get('rules',[])):continue
    path=library(meta);classpath.append(str(path))
    if 'natives-windows' in meta['name']:
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if name.endswith('.dll'):
                    dest=natives/Path(name).name;dest.write_bytes(z.read(name))
# Forge's patched SRG client is already a declared library. Adding the vanilla
# obfuscated client under a different filename creates two Minecraft modules.
assets=cache/'caches/forge_gradle/assets'
mapping={'auth_player_name':'PIXEL_LAB','version_name':version['id'],'game_directory':str(GAME),'assets_root':str(assets),'assets_index_name':vanilla['assetIndex']['id'],'auth_uuid':'00000000000000000000000000000001','auth_access_token':'0','clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release','natives_directory':str(natives),'launcher_name':'Sillon-Pixel-Lab','launcher_version':'1','classpath':os.pathsep.join(classpath),'library_directory':str(GAME/'libraries'),'classpath_separator':os.pathsep}
def args(values):
    result=[]
    for item in values:
        if isinstance(item,dict):
            if not allowed(item.get('rules',[])):continue
            item=item['value']
        for value in item if isinstance(item,list) else [item]:
            value=re.sub(r'\$\{([^}]+)\}',lambda m:mapping[m[1]],value);result.append(value)
    return result
arguments=['-Xmx3G',*args(vanilla['arguments']['jvm']),*args(version['arguments']['jvm']),version['mainClass'],*args(vanilla['arguments']['game']),*args(version['arguments']['game']),'--width','1024','--height','768','--quickPlaySingleplayer','PIXEL_LAB']
target=GAME.parent/'launch-args.txt';target.write_text('\n'.join(json.dumps(a.replace('\\','/')) for a in arguments),encoding='utf-8')
world=GAME/'saves/PIXEL_LAB'
if not world.exists():shutil.copytree(ROOT/'forge/run/saves/PIXEL_LAB',world)
shutil.copyfile(ROOT/'forge/run/options.txt',GAME/'options.txt')
(GAME/'mods').mkdir(exist_ok=True)
print('Prepared official Forge runtime',target)
