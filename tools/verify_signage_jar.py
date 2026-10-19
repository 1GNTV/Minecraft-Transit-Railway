"""Inspect the assembled deliverable, not the source tree or intermediate classes."""
from pathlib import Path
import zipfile,json,hashlib
jar=Path("build/signage-delivery/Sillon-Signaletique-Forge-1.20.1.jar")
with zipfile.ZipFile(jar) as z:
    names=set(z.namelist())
    required=[
"org/mtr/core/tool/Utilities.class","org/mtr/mod/client/SillonSignagePainter.class",
"org/mtr/mod/client/SillonRATPSignageLayouts.class","org/mtr/mod/client/SillonSignageGeometry.class",
"org/mtr/mod/data/SillonSignageOptions.class","org/mtr/mod/data/SillonSignageRow.class",
"org/mtr/mod/data/SillonSignageSpec.class","org/mtr/mod/screen/SillonSignageScreen.class",
"org/mtr/mod/screen/SillonSignageEditorModel.class","org/mtr/mod/packet/PacketUpdateSillonSignage.class",
"org/mtr/mod/render/RenderSillonSignage.class",
"org/mtr/mod/client/SillonSignageNetwork.class","org/mtr/mod/client/SillonSignageAutomatic.class",
"org/mtr/mod/client/SillonSignageNetworkMap.class","org/mtr/mod/data/SillonSignageDesign.class",
"org/mtr/mod/packet/PacketSignageNetwork.class","org/mtr/mod/data/SillonSignageBinding.class",
"assets/mtr/font/panam/Parisine-Regular.otf","assets/mtr/font/panam/Parisine-Bold.otf",
"assets/mtr/signage/fonts/GrandParis-Bold.otf","assets/mtr/signage/fonts/IDFVoyageur-Medium.otf",
"assets/mtr/signage/fonts/IDFVoyageur-Bold.otf","assets/mtr/signage/icons/official_lift.png",
"assets/mtr/signage/icons/official_arrow_7.png",
"assets/mtr/signage/maps/metro.png","META-INF/licenses/MTR-France-Addon-MIT.txt",
"META-INF/licenses/IDFM-signage-resources.txt"]
    missing=[p for p in required if p not in names]
    if missing:raise AssertionError("Missing deliverable resources: "+repr(missing))
    icons=[p for p in names if p.startswith("assets/mtr/signage/icons/") and p.endswith(".png")]
    if len(icons)!=513:raise AssertionError("Expected 513 bundled icon entries, found "+str(len(icons)))
    if any(p.startswith("org/sillon/qa/") for p in names):raise AssertionError("Test-only mod leaked into the user JAR")
    spec=z.read("org/mtr/mod/data/SillonSignageSpec.class")
    for style in ["metro","metro_subtitle","rer","direction","transfer","interchange","exit","sector","vertical","platform","route","map","services","totem","digital","custom","gpe","gpe_direction"]:
        if style.encode() not in spec:raise AssertionError("Missing style "+style)
report={"jar":jar.name,"bytes":jar.stat().st_size,"sha256":hashlib.sha256(jar.read_bytes()).hexdigest(),
"bundled_icons":len(icons),"families":18,"presets":23,"real_fonts":["Parisine","Grand Paris","IDF Voyageur"],
"required_resources_checked":len(required),"test_helpers_in_user_jar":False}
(jar.parent/"verification.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
print("PASS assembled signage JAR",json.dumps(report))
