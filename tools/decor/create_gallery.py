from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import re,json,zipfile,shutil
root=Path(__file__).resolve().parents[2];review=root/'build/decor-review'
source=(root/'fabric/src/main/java/org/mtr/mod/decor/DecorKind.java').read_text();kinds=re.findall(r'^    [A-Z_]+\("([a-z_]+)","([^"]+)".*?"([^"\n]+\\|[^"\n]+)"\),?$',source,re.M)
# Read explicit identifiers and titles; variant labels come from the Java catalog line.
kinds=[]
for line in source.splitlines():
 m=re.match(r'    [A-Z_]+\("([a-z_]+)","([^"]+)"',line)
 if m:kinds.append((m[1],m[2],re.findall(r'"([^"]*)"',line)[-1].split('|')))
font_path='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
def font(n,bold=False):return ImageFont.truetype(font_path.replace('.ttf','-Bold.ttf') if bold else font_path,n)
groups=[('Architecture et revêtements','01-architecture'),('Éclairages et mobilier','02-eclairage-mobilier'),('Mobilier et équipements','03-equipements'),('Finitions et équipements','04-finitions')]
for group,(title,name) in enumerate(groups):
 im=Image.new('RGB',(1400,1270),'#e9eeeb');d=ImageDraw.Draw(im)
 d.text((38,25),'SILLON  /  KIT DE GARE',font=font(17,True),fill='#387586');d.text((36,56),title,font=font(34,True),fill='#19343d')
 d.text((38,105),'Rendus des modèles du mod • variante de base et une alternative • paramètres réglables au brush',font=font(16),fill='#53676e')
 for j,(id,item,variants) in enumerate(kinds[group*5:(group+1)*5]):
  x=30+(j%2)*685;y=150+(j//2)*360;d.rounded_rectangle((x,y,x+655,y+338),radius=15,fill='white');d.text((x+19,y+15),f'{group*5+j+1:02d}  {item}',font=font(22,True),fill='#19343d')
  for n,v in enumerate([0,3]):
   model=Image.open(review/f'images/{id}-{v}.png').convert('RGBA');model.thumbnail((310,235));im.paste(model,(x+14+n*320+(310-model.width)//2,y+56+(235-model.height)//2),model)
   d.text((x+22+n*320,y+302),variants[v],font=font(16),fill='#546b72')
 x=715;y=870;d.rounded_rectangle((x,y,x+655,y+338),radius=15,fill='#19343d')
 d.text((x+28,y+30),'20 familles · 80 variantes',font=font(27,True),fill='#ffffff')
 for k,txt in enumerate(['Dimensions et matériaux personnalisables','Aperçu avant enregistrement','Pose répétée et sélection au brush','Modification groupée et annulation']):d.text((x+28,y+92+k*40),txt,font=font(19),fill='#c3d7dc')
 d.text((38,1233),'Modèles originaux Sillon • ces planches sont des rendus isolés, pas des captures Minecraft.',font=font(16),fill='#53676e')
 im.save(review/(name+'.jpg'),quality=94)
print('Four labelled gallery boards exported')
