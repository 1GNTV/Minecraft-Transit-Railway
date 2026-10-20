"""Exercise production geometry and persistence, with an isolated config directory."""
from pathlib import Path
import subprocess,os
root=Path(__file__).resolve().parents[1]
out=root/'build/builder-headless';out.mkdir(parents=True,exist_ok=True)
jar=root/'build/signage-delivery/Sillon-Signaletique-Forge-1.20.1.jar'
source=root/'fabric/src/main/java/org/mtr/mod/builder'
subprocess.run(['javac','--release','17','-cp',str(jar),'-d',str(out),*[str(source/(x+'.java')) for x in ['MapGeometry','DoorGuideGeometry','ConsistPresets','ConsistDraft','RegionPolygon','WalkGraph']],str(root/'tools/builder/BuilderRegression.java'),str(root/'tools/builder/BuilderNextRegression.java'),str(root/'tools/builder/BuilderJourneyRegression.java')],check=True)
run=out/'run';run.mkdir(exist_ok=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+str(jar),'BuilderRegression'],cwd=run,check=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+str(jar),'BuilderNextRegression'],cwd=run,check=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+str(jar),'BuilderJourneyRegression'],cwd=run,check=True)
# Existing packet discriminators must retain their exact order.
import re
init=(root/'fabric/src/main/java/org/mtr/mod/Init.java').read_text()
names=re.findall(r'REGISTRY.registerPacket\(([^,]+)',init)
expected=["PacketAddBalance.class","PacketBlockRails.class","PacketBroadcastRailActions.class","PacketCheckRouteIdHasDisabledAnnouncements.class","PacketDeleteData.class","PacketDeleteRailAction.class","PacketDepotClear.class","PacketDepotInstantDeploy.class","PacketDepotGenerate.class","PacketDriveTrain.class","PacketFetchArrivals.class","PacketForwardClientRequest.class","PacketUpdateKeyDispenserConfig.class","PacketOpenBlockEntityScreen.class","PacketOpenDashboardScreen.class","PacketOpenLiftCustomizationScreen.class","PacketOpenPIDSConfigScreen.class","PacketOpenTicketMachineScreen.class","PacketPressLiftButton.class","PacketRequestData.class","PacketSetRouteIdHasDisabledAnnouncements.class","PacketTurnOnBlockEntity.class","PacketUpdateData.class","PacketUpdateEyeCandyConfig.class","PacketUpdateLastRailStyles.class","PacketUpdateLiftTrackFloorConfig.class","PacketUpdatePIDSConfig.class","PacketUpdatePanamScreen.class","PacketUpdateSillonSignage.class","PacketStationDecor.class","PacketSignageNetwork.class","PacketPanamNewsSync.class","PacketPanamNewsRequest.class","PacketPanamBulletinEdit.class","PacketOpenPanamControlScreen.class","PacketUpdateRailwaySignConfig.class","PacketUpdateSignalConfig.class","PacketUpdateTrainAnnouncerConfig.class","PacketUpdateTrainScheduleSensorConfig.class","PacketUpdateTrainSensorConfig.class","PacketUpdateVehiclesLifts.class","PacketUpdateVehicleRidingEntities.class","org.mtr.mod.packet.PacketBuilderPlan.class","org.mtr.mod.packet.PacketBuilderRail.class"]
assert names[:len(expected)]==expected and names[len(expected):]==["PacketBuilderJourney.class","PacketBuilderWayfinding.class","PacketChantier.class"],names
print('PASS appended builder packets preserve existing discriminator order')
