"""Package models, inventory icons, localized names and survival drops for all station anchors."""
from pathlib import Path
from PIL import Image
import json,re
root=Path(__file__).resolve().parents[2];assets=root/'fabric/src/main/resources/assets/mtr';data=root/'fabric/src/main/resources/data/mtr'
source=(root/'fabric/src/main/java/org/mtr/mod/decor/DecorKind.java').read_text()
kinds=re.findall(r'^    [A-Z_]+\("([a-z_]+)","([^"]+)"',source,re.M)
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
write(assets/'models/block/decor_anchor.json',{'textures':{'particle':'minecraft:block/iron_block'},'elements':[]})
for id,title in kinds:
 name='sillon_decor_'+id
 write(assets/f'blockstates/{name}.json',{'variants':{'':{'model':'mtr:block/decor_anchor'}}})
 write(assets/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'mtr:item/'+name}})
 icon=Image.open(root/f'build/decor-review/images/{id}-0.png');icon.thumbnail((128,128));canvas=Image.new('RGBA',(128,128));canvas.alpha_composite(icon,((128-icon.width)//2,(128-icon.height)//2));p=assets/f'textures/item/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);canvas.save(p)
 write(data/f'loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'mtr:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for lang in ['fr_fr','en_us']:
 p=assets/f'lang/{lang}.json';value=json.loads(p.read_text()) if p.exists() else {}
 value['item.mtr.chantier']='Outil Chantier' if lang=='fr_fr' else 'Station Construction Tool'
 value['key.mtr.chantier']='Réglages du chantier' if lang=='fr_fr' else 'Construction tool settings'
 for id,title in kinds:value['block.mtr.sillon_decor_'+id]='Sillon · '+(title if lang=='fr_fr' else id.replace('_',' ').title())
 write(p,value)
print('Packaged',len(kinds),'station items, icons and drops')

write(assets/"models/item/chantier.json",{"parent":"minecraft:item/handheld","textures":{"layer0":"minecraft:item/stick","layer1":"mtr:item/brush"}})
