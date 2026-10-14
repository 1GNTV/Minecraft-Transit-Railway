"""Create an isolated Forge dev superflat fixture; never writes to a user's game profile."""
from pathlib import Path
import struct,gzip,json
ROOT=Path(__file__).resolve().parents[1]
LAB=ROOT/'forge/run/saves/PIXEL_LAB'
assert str(LAB.resolve()).startswith(str((ROOT/'forge/run').resolve()))
if (LAB/'level.dat').exists():raise SystemExit('Lab already exists; preserving it')
LAB.mkdir(parents=True,exist_ok=True)
def string(v):b=v.encode();return struct.pack('>H',len(b))+b
def encode(t,v):
    if t==1:return struct.pack('>b',v)
    if t==3:return struct.pack('>i',v)
    if t==4:return struct.pack('>q',v)
    if t==5:return struct.pack('>f',v)
    if t==6:return struct.pack('>d',v)
    if t==8:return string(v)
    if t==9:sub,items=v;return bytes([sub])+struct.pack('>i',len(items))+b''.join(encode(sub,i) for i in items)
    if t==10:return b''.join(bytes([st])+string(k)+encode(st,sv) for k,(st,sv) in v.items())+b'\0'
    raise ValueError(t)
def noise(settings,source):return (10,{'type':(8,'minecraft:noise'),'settings':(8,settings),'biome_source':source})
over=(10,{'type':(8,'minecraft:overworld'),'generator':(10,{'type':(8,'minecraft:flat'),'settings':(10,{'biome':(8,'minecraft:plains'),'features':(1,0),'lakes':(1,0),'layers':(9,(10,[{'block':(8,name),'height':(3,h)} for name,h in [('minecraft:bedrock',1),('minecraft:dirt',2),('minecraft:grass_block',1)]])),'structure_overrides':(9,(8,[]))})})})
dim={'minecraft:overworld':over,'minecraft:the_nether':(10,{'type':(8,'minecraft:the_nether'),'generator':noise('minecraft:nether',(10,{'type':(8,'minecraft:multi_noise'),'preset':(8,'minecraft:nether')}))}),'minecraft:the_end':(10,{'type':(8,'minecraft:the_end'),'generator':noise('minecraft:end',(10,{'type':(8,'minecraft:the_end')}))})}
data={'DataVersion':(3,3465),'Version':(10,{'Id':(3,3465),'Name':(8,'1.20.1'),'Snapshot':(1,0)}),'LevelName':(8,'SILLON PIXEL isolated lab'),'GameType':(3,1),'hardcore':(1,0),'Difficulty':(1,0),'allowCommands':(1,1),'initialized':(1,1),'SpawnX':(3,0),'SpawnY':(3,-60),'SpawnZ':(3,0),'Time':(4,6000),'DayTime':(4,6000),'GameRules':(10,{'doDaylightCycle':(8,'false'),'doMobSpawning':(8,'false')}),'WorldGenSettings':(10,{'seed':(4,0),'generate_features':(1,0),'bonus_chest':(1,0),'dimensions':(10,dim)}),'DataPacks':(10,{'Enabled':(9,(8,['vanilla','mod:forge','mod:mtr','file/pixel-fixture'])),'Disabled':(9,(8,[]))})}
with gzip.open(LAB/'level.dat','wb') as f:f.write(bytes([10])+string('')+encode(10,{'Data':(10,data)}))
pack=LAB/'datapacks/pixel-fixture';(pack/'data/minecraft/tags/functions').mkdir(parents=True);(pack/'data/pixel/functions').mkdir(parents=True)
(pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Isolated PANAM geometry test; no live timetable fixture'}}))
(pack/'data/minecraft/tags/functions/tick.json').write_text(json.dumps({'values':['pixel:tick']}))
(pack/'data/pixel/functions/tick.mcfunction').write_text('execute as @a[tag=!pixel_fixture] at @s run function pixel:prepare\n')
(pack/'data/pixel/functions/prepare.mcfunction').write_text('''setblock 0 -59 0 mtr:panam_pids[facing=north]
setblock 0 -59 -1 mtr:panam_pids[facing=south]
setblock 0 -59 4 mtr:panam_pids[facing=east]
setblock 1 -59 4 mtr:panam_pids[facing=west]
tp @s 3.5 -60 0 90 2
gamemode creative @s
tag @s add pixel_fixture
''')
(ROOT/'forge/run/options.txt').write_text('guiScale:2\nrenderDistance:8\nsimulationDistance:5\npauseOnLostFocus:false\nfov:0.0\n')
print(LAB)
