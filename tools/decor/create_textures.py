"""Original small tiled materials, no third-party artwork embedded."""
from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
import random,math
root=Path(__file__).resolve().parents[2]
out=root/'fabric/src/main/resources/assets/mtr/textures/decor';out.mkdir(parents=True,exist_ok=True)
r=random.Random(841)
for mat in ['concrete','stone','metal','wood','accent','glass','glow','perforated','fire','hazard','poster','label']:
    im=Image.new('RGBA',(256,256),(255,255,255,255));p=im.load()
    if mat in ('concrete','stone','metal','wood','perforated'):
        for y in range(256):
            for x in range(256):
                noise=r.gauss(0,2 if mat=='metal' else 5)
                grain=math.sin(y*.72+math.sin(x*.022)*3)*7+math.sin(y*.19)*5 if mat=='wood' else 0
                val=int(max(155,min(255,235+noise+grain)));p[x,y]=(val,val,val,255)
        d=ImageDraw.Draw(im)
        if mat=='stone':d.line([(0,0),(255,0)],fill=(185,185,185),width=2);d.line([(0,0),(0,255)],fill=(185,185,185),width=2)
        if mat=='metal':
            for y in range(0,256,3):d.line([(0,y),(255,y)],fill=(225+r.randrange(12),)*3)
        if mat=='perforated':
            for y in range(8,256,16):
                for x in range(8,256,16):d.ellipse((x-3,y-3,x+3,y+3),fill=(45,49,51))
    d=ImageDraw.Draw(im)
    if mat=='glass':
        im=Image.new('RGBA',(256,256),(225,245,249,255));d=ImageDraw.Draw(im);d.line((0,200,200,0),fill=(245,255,255),width=8)
    if mat=='fire':
        d.rectangle((0,0,255,255),fill='#b62b32');d.rounded_rectangle((90,92,166,218),radius=20,fill='white');d.rectangle((110,70,143,100),fill='white');d.line((112,74,170,74,189,173),fill='white',width=10)
    if mat=='hazard':
        d.polygon([(128,12),(246,237),(10,237)],fill='#f2ca3c',outline='#282d30',width=12);d.polygon([(143,65),(99,151),(134,151),(116,203),(172,126),(137,126)],fill='#252c30')
    if mat=='poster':
        im=Image.new('RGBA',(256,256),'#0c2430');d=ImageDraw.Draw(im)
        for yy,color in [(180,'#36abc0'),(207,'#d2b26c'),(234,'#ecece4')]:d.line([(0,yy),(70,yy),(110,yy-45),(230,yy-45),(256,yy-71)],fill=color,width=5)
        font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',27);small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',12)
        d.text((20,24),'SILLON',font=font,fill='#f2eee4');d.text((21,62),'EXPLORER LE RÉSEAU',font=small,fill='#a6c7cc');d.ellipse((20,95,55,130),outline='#d2b26c',width=3)
    im.save(out/(mat+'.png'))
print('12 original materials generated')
