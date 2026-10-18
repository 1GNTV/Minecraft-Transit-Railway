"""Prepare missing reference assets; never depend on changing web downloads for a rebuilt JAR."""
from pathlib import Path
from urllib.request import urlopen,Request
import fitz,json,hashlib,io
from PIL import Image,ImageDraw
root=Path(".");out=root/"docs/signage/photos";out.mkdir(parents=True,exist_ok=True)
icons=root/"fabric/src/main/resources/assets/mtr/signage/icons"
atlas=Image.open(icons/"ratp_assistance.png").convert("RGBA")
for name,box in {
"ratp_assistance_hearing":(0,0,250,250),"ratp_assistance_visual":(250,0,500,250),
"ratp_assistance_audio":(0,250,250,500),"ratp_assistance_cognitive":(250,250,500,500)}.items():
    atlas.crop(box).save(icons/(name+".png"))
logo=Image.open(icons/"ratp_logo.png").convert("RGBA")
white=Image.new("RGBA",logo.size,"white");white.putalpha(logo.getchannel("A"));white.save(icons/"ratp_logo_white.png")
sources={
"rer-forum":"https://upload.wikimedia.org/wikipedia/commons/2/26/Panneau_Gare_Ch%C3%A2telet_Halles_RER_Paris_Ligne_B_-_Paris_I_%28FR75%29_-_2022-07-05_-_2.jpg",
"rivoli":"https://chateletleshalles.com/sortie11/sortie-11-panneau.jpg",
"chatelet-fabricant":"https://girod-group.com/wp-content/uploads/2018/04/habillage-email-architectural-ratp.jpg",
"ratp-parisine":"https://www.ratp.fr/sites/default/files/styles/scale_m/public/article/image/montage-typographie-parisine.jpg",
"gpe-arep":"https://media.licdn.com/dms/image/v2/D4E22AQH6_jT9jiAFNA/feedshare-shrink_800/B4EZZIgYuXHgAk-/0/1744973173857?e=2147483647&t=QPFWWyyn50HMBGy-OkN-QF-eV7zdT1gG9tZEyRcGn6M&v=beta"}
manifest=json.loads((out/"sources.json").read_text()) if (out/"sources.json").exists() else {}
def get(url):return urlopen(Request(url,headers={"User-Agent":"Mozilla/5.0"}),timeout=60).read()
for name,url in sources.items():
    if (out/(name+".jpg")).exists():continue
    try:
        data=get(url);im=Image.open(io.BytesIO(data)).convert("RGB");im.thumbnail((1400,1100));im.save(out/(name+".jpg"),quality=92)
        manifest[name]={"url":url,"sha256":hashlib.sha256(data).hexdigest()}
    except Exception as e:manifest[name]={"url":url,"error":str(e)}
url="https://eu.ftp.opendatasoft.com/stif/PlansRegion/Plans/PLAN_POCHE_RESEAU.pdf"
destination=root/"fabric/src/main/resources/assets/mtr/signage/maps/metro.png"
if not destination.exists():
    pdf=get(url);doc=fitz.open(stream=pdf,filetype="pdf");page=doc[0]
    scale=min(2560/page.rect.width,2560/page.rect.height)
    destination.parent.mkdir(parents=True,exist_ok=True)
    page.get_pixmap(matrix=fitz.Matrix(scale,scale)).save(str(destination))
manifest.pop("metro-plan",None)
manifest["regional-plan"]={"url":url,"date":"2026","credit":"Île-de-France Mobilités","resource_sha256":hashlib.sha256(destination.read_bytes()).hexdigest()}
pages=[59,61,62,70,72,73,78,79,80,81,83,85,88,89,92,93,94,95,127,128,132,133]
if any(not (root/"docs/signage"/("prescriptions-"+str(p)+".png")).exists() for p in pages):
    pdf=get("https://eu.ftp.opendatasoft.com/stif/PrescriptionsSignaletiques/IDFM_Prescriptions%20signaletiques_V3.1.pdf")
    doc=fitz.open(stream=pdf,filetype="pdf")
    for p in pages:
        if p<=len(doc):doc[p-1].get_pixmap(matrix=fitz.Matrix(1,1)).save(str(root/"docs/signage"/("prescriptions-"+str(p)+".png")))
manifest["derived-icons"]={"source":"MTR France pinned catalog","changes":"4 quadrants extracted from ratp_assistance; ratp_logo white alpha variant"}
(out/"sources.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
photos=list(out.glob("*.jpg"));sheet=Image.new("RGB",(1200,400*max(1,(len(photos)+1)//2)),"#e9edf1");d=ImageDraw.Draw(sheet)
for i,p in enumerate(photos):
    im=Image.open(p);im.thumbnail((580,355));x=(i%2)*600;y=(i//2)*400
    sheet.paste(im,(x+(600-im.width)//2,y+28+(355-im.height)//2));d.text((x+12,y+8),p.stem,fill="black")
sheet.save(root/"docs/signage/reference-photos.jpg",quality=92)
print("Prepared",len(list(icons.glob("*.png"))),"icons;",len(photos),"reference photos; regional map SHA256",manifest["regional-plan"]["resource_sha256"])
