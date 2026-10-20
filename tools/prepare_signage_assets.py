#!/usr/bin/env python3
"""Pinned reference assets for Sillon's signage. Run only in the preparation workflow."""
from pathlib import Path
from urllib.request import urlopen, Request
import subprocess, shutil, zipfile, hashlib, json, re
import fitz
from PIL import Image, ImageDraw, ImageFont
root=Path(".")
out=root/"docs/signage"
out.mkdir(parents=True,exist_ok=True)
assets=root/"fabric/src/main/resources/assets/mtr/signage"
assets.mkdir(parents=True,exist_ok=True)
def download(url,path):
    data=urlopen(Request(url,headers={"User-Agent":"Sillon reference preparation"}),timeout=120).read()
    path.write_bytes(data)
    return hashlib.sha256(data).hexdigest()
pin="eaf2a8130ac31f12534480a567c9246f35c0463e"
subprocess.run(["git","clone","--depth","1","--filter=blob:none","--sparse","https://github.com/1GNTV/MTR-France-Addon.git","/tmp/mtr-france-signage"],check=True)
subprocess.run(["git","-C","/tmp/mtr-france-signage","sparse-checkout","set","src/main/resources/assets/mtr/mtr_france_sign"],check=True)
subprocess.run(["git","-C","/tmp/mtr-france-signage","fetch","--depth","1","origin",pin],check=True)
subprocess.run(["git","-C","/tmp/mtr-france-signage","checkout",pin],check=True)
src=Path("/tmp/mtr-france-signage/src/main/resources/assets/mtr/mtr_france_sign")
catalog=[]
for p in sorted(src.rglob("*.png")):
    image=Image.open(p)
    name=p.stem
    dest=assets/"icons"/(name+".png")
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(p,dest)
    catalog.append({"id":name,"path":"signage/icons/"+dest.name,"width":image.width,"height":image.height,
                    "source":str(p.relative_to(Path("/tmp/mtr-france-signage"))),"sha256":hashlib.sha256(p.read_bytes()).hexdigest()})
(assets/"catalog.json").write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding="utf-8")
licenses=root/"fabric/src/main/resources/META-INF/licenses"
licenses.mkdir(parents=True,exist_ok=True)
shutil.copy2("/tmp/mtr-france-signage/LICENSE.txt",licenses/"MTR-France-Addon-MIT.txt")
urls={
"prescriptions":"https://eu.ftp.opendatasoft.com/stif/PrescriptionsSignaletiques/IDFM_Prescriptions%20signaletiques_V3.1.pdf",
"colors":"https://eu.ftp.opendatasoft.com/stif/PrescriptionsSignaletiques/Bibliotheque_nuanciers.zip",
"fonts":"https://eu.ftp.opendatasoft.com/stif/PrescriptionsSignaletiques/Bibliotheque_polices.zip",
"ratp":"https://www.ratp.fr/sites/default/files/inline-files/fiches_equipements_1.pdf"}
manifest={"mtr_france_commit":pin,"icon_count":len(catalog),"sources":{}}
for name,url in urls.items():
    try:
        p=Path("/tmp")/(name+(".pdf" if name in ("prescriptions","ratp") else ".zip"))
        digest=download(url,p)
        manifest["sources"][name]={"url":url,"sha256":digest}
        if name=="fonts":
            fontdir=assets/"fonts";fontdir.mkdir(exist_ok=True)
            with zipfile.ZipFile(p) as z:
                for item in z.infolist():
                    if not item.is_dir() and item.filename.lower().endswith((".otf",".ttf",".txt",".pdf")) and "__MACOSX" not in item.filename:
                        target=fontdir/Path(item.filename).name
                        target.write_bytes(z.read(item))
            print("OFFICIAL FONTS",sorted(x.name for x in fontdir.iterdir()))
        if name=="colors":
            with zipfile.ZipFile(p) as z:
                print("COLOR FILES",z.namelist())
                for item in z.infolist():
                    if not item.is_dir() and "__MACOSX" not in item.filename:
                        (out/Path(item.filename).name).write_bytes(z.read(item))
        if name in ("prescriptions","ratp"):
            pdf=fitz.open(p)
            chosen=[]
            keywords=("flèches","nuancier","couleurs","règles de composition","parisine","signalétique","indices de ligne","typographie","grand paris")
            for i,page in enumerate(pdf):
                text=page.get_text()
                if (name=="ratp" and "Le fond gris est réservé" in text) or (name=="prescriptions" and sum(k in text.lower() for k in keywords)>=2):
                    chosen.append(i)
                    print("REFERENCE PAGE",name,i+1,text[:1600].replace("\n"," "))
            manifest["sources"][name]["matching_pages"]=[i+1 for i in chosen]
            picks=chosen[:24] if name=="prescriptions" else chosen[:1]
            for i in picks:
                pdf[i].get_pixmap(matrix=fitz.Matrix(1,1)).save(str(out/(name+"-"+str(i+1)+".png")))
    except Exception as e:
        print("REFERENCE DOWNLOAD FAILED",name,str(e))
        manifest["sources"][name]={"url":url,"error":str(e)}
(out/"assets-provenance.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
# Gallery gives a direct visual check of the actual bundled pictograms and their aspect ratios.
tiles=140;cols=10;rows=(len(catalog)+cols-1)//cols
sheet=Image.new("RGB",(tiles*cols,tiles*rows),"#e5e8ed");d=ImageDraw.Draw(sheet)
font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",10)
for i,item in enumerate(catalog):
    x=(i%cols)*tiles;y=(i//cols)*tiles
    d.rectangle((x+3,y+3,x+137,y+136),fill="white")
    im=Image.open(assets/"icons"/(item["id"]+".png")).convert("RGBA")
    im.thumbnail((108,97))
    sheet.paste(im,(x+(tiles-im.width)//2,y+8),im)
    d.text((x+6,y+113),item["id"],font=font,fill="#16213c")
sheet.save(out/"pictograms-contact-sheet.jpg",quality=88)
print("ASSET PREPARATION COMPLETE",len(catalog))
