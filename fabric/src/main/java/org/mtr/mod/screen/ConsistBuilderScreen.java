package org.mtr.mod.screen;

import org.mtr.core.data.*;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.*;
import org.mtr.mod.builder.*;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.render.TrainSilhouetteCache;
import org.mtr.mod.resource.VehicleResource;
import java.util.*;

/** A staged editor over the original MTR Siding/VehicleCar data and update packet. */
public final class ConsistBuilderScreen extends ScreenExtension {
    private final Siding siding;private final ScreenExtension previous;private final List<VehicleResource> catalog=new ArrayList<>();private final Map<String,VehicleResource> byId=new HashMap<>();
    private final List<VehicleCar> draft=new ArrayList<>();private TextFieldWidgetExtension search,presetName;private int page,carPage,selected=-1,presetIndex,drag=-1;private String query="",name="",message="";private int divider,rows,slots;
    public ConsistBuilderScreen(Siding siding,ScreenExtension previous){this.siding=siding;this.previous=previous;draft.addAll(siding.getVehicleCars());CustomResourceLoader.iterateVehicles(siding.getTransportMode(),r->{catalog.add(r);byId.put(r.getId(),r);});catalog.sort(Comparator.comparing(r->r.getName().getString()));}
    private void refresh(){MinecraftClient.getInstance().openScreen(new Screen(this));}
    private void button(int x,int y,int w,String text,Runnable action){addChild(new ClickableWidget(BuilderUi.button(x,y,w,text,action)));}
    private List<VehicleResource> filtered(){String q=query.toLowerCase(Locale.ROOT);return catalog.stream().filter(r->(r.getName().getString()+" "+r.getId()).toLowerCase(Locale.ROOT).contains(q)).toList();}
    @Override protected void init2(){super.init2();divider=Math.max(130,width*43/100);slots=width<500?2:4;rows=Math.max(1,(height-143)/62);
        search=new TextFieldWidgetExtension(12,43,divider-24,20,120,TextCase.DEFAULT,null,"Rechercher un train");search.setText2(query);search.setChangedListener2(t->{query=t;page=0;});addChild(new ClickableWidget(search));
        presetName=new TextFieldWidgetExtension(divider+12,height-92,width-divider-24,18,64,TextCase.DEFAULT,null,"Nom de la composition");presetName.setText2(name);presetName.setChangedListener2(t->name=t);addChild(new ClickableWidget(presetName));
        button(12,height-65,48,"‹",()->{page=Math.max(0,page-1);});button(64,height-65,48,"›",()->{if((page+1)*rows<filtered().size())page++;});
        int right=width-divider-24,bw=Math.max(25,(right-12)/4),x=divider+12;
        button(x,43,bw,"←",()->move(-1));button(x+bw+4,43,bw,"→",()->move(1));button(x+2*(bw+4),43,bw,"+1",this::duplicate);button(x+3*(bw+4),43,bw,"−1",()->{if(selected>=0&&selected<draft.size()){draft.remove(selected);selected=Math.min(selected,draft.size()-1);}});
        button(x,68,bw,"‹",()->carPage=Math.max(0,carPage-1));button(x+bw+4,68,bw,"›",()->{if((carPage+1)*slots<draft.size())carPage++;});button(x+2*(bw+4),68,2*bw+4,"Inverser",()->{Collections.reverse(draft);selected=-1;});
        int pw=(right-4)/2;button(x,height-68,pw,"Enregistrer",this::savePreset);button(x+pw+4,height-68,pw,"Mes rames…",()->MinecraftClient.getInstance().openScreen(new Screen(new ConsistPresetScreen(this,siding.getTransportMode().name(),chosen->{name=chosen;loadPreset();refresh();}))));
        button(12,height-28,70,"Annuler",this::onClose2);button(88,height-28,96,"Menu classique",()->MinecraftClient.getInstance().openScreen(new Screen(new VehicleSelectorScreen(siding,previous))));button(width-112,height-28,100,"Appliquer",this::apply);
    }
    private void move(int delta){int to=selected+delta;if(selected>=0&&to>=0&&to<draft.size()){Collections.swap(draft,selected,to);selected=to;carPage=selected/slots;}}
    private void duplicate(){if(selected>=0&&selected<draft.size()&&draft.size()<siding.getTransportMode().maxLength)draft.add(selected,draft.get(selected));}
    private void add(VehicleResource r){if(draft.size()>=siding.getTransportMode().maxLength){message="Nombre maximal de voitures atteint.";return;}var c=new VehicleCar(r.getId(),r.getLength(),r.getWidth(),r.getBogie1Position(),r.getBogie2Position(),r.getCouplingPadding1(),r.getCouplingPadding2());draft.add(c);selected=draft.size()-1;carPage=selected/slots;}
    public double length(){return Siding.getTotalVehicleLength(new ObjectArrayList<>(draft));}
    private void apply(){if(length()>siding.getRailLength()+.001){message="Rame trop longue pour cette voie de garage.";return;}if(draft.stream().anyMatch(c->!byId.containsKey(c.getVehicleId()))){message="Ressource de train manquante : conserver ou recharger le pack avant de valider.";return;}siding.setVehicleCars(new ObjectArrayList<>(draft));InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addSiding(siding)));onClose2();}
    private void savePreset(){try{ConsistPresets.save(siding.getTransportMode().name(),name,draft.stream().map(VehicleCar::getVehicleId).toList());message="Composition enregistrée : "+name;}catch(Exception e){message="Enregistrement impossible : "+e.getMessage();}}
    private void loadPreset(){var ids=ConsistPresets.load(siding.getTransportMode().name()).get(name);if(ids==null){message="Choisis une composition enregistrée.";return;}if(ids.stream().anyMatch(id->!byId.containsKey(id))){message="Pack absent : la composition actuelle est conservée.";return;}if(ids.size()>siding.getTransportMode().maxLength){message="Composition incompatible avec ce mode de transport.";return;}draft.clear();ids.forEach(id->add(byId.get(id)));selected=-1;carPage=0;message="Composition chargée. Vérifie puis applique.";}
    private void profile(GraphicsHolder g,VehicleResource r,int index,int total,int x,int y,int w,int h){if(r==null){BuilderUi.text(g,"Ressource manquante",x,y+12,0xFFFFAD82);return;}Identifier id=TrainSilhouetteCache.request(r,index,total);if(id!=null){GuiDrawing d=new GuiDrawing(g);d.beginDrawingTexture(id);int ph=Math.max(1,Math.min(h,w/4));d.drawTexture(x,y+(h-ph)/2,x+w,y+(h+ph)/2,0,1,1,0);d.finishDrawingTexture();}else BuilderUi.text(g,BuilderUi.fit(TrainSilhouetteCache.status(r,index,total),w),x,y+12,BuilderUi.MUTED);}
    @Override public void render(GraphicsHolder g,int mx,int my,float dt){TrainSilhouetteCache.pump();BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.rect(g,7,35,divider-14,height-70,BuilderUi.PANEL);BuilderUi.rect(g,divider+5,35,width-divider-12,height-70,BuilderUi.PANEL);BuilderUi.text(g,"SILLON  /  COMPOSITION DE RAME",12,12,BuilderUi.ACCENT);if(width>=500)BuilderUi.text(g,BuilderUi.fit(siding.getName(),width-divider-24),divider+12,12,BuilderUi.TEXT);
        var list=filtered();page=Math.min(page,Math.max(0,(list.size()-1)/rows));for(int n=0;n<rows&&page*rows+n<list.size();n++){var r=list.get(page*rows+n);int y=71+n*62;boolean hover=mx>=12&&mx<divider-12&&my>=y&&my<y+58;BuilderUi.rect(g,12,y,divider-24,58,hover?0xFF30495A:0xFF223442);BuilderUi.text(g,BuilderUi.fit(r.getName().getString(),divider-45),18,y+5,BuilderUi.TEXT);profile(g,r,0,1,18,y+16,divider-44,32);BuilderUi.text(g,String.format(Locale.ROOT,"%.1f m  ·  cliquer pour ajouter",r.getLength()),18,y+47,BuilderUi.MUTED);}
        int available=width-divider-24,slot=Math.max(35,available/slots);for(int j=0;j<slots;j++){int i=carPage*slots+j;if(i>=draft.size())break;VehicleCar c=draft.get(i);int x=divider+12+j*slot;BuilderUi.rect(g,x,102,slot-3,Math.min(79,Math.max(30,height-210)),i==selected?0xFF365E65:0xFF223442);profile(g,byId.get(c.getVehicleId()),i,draft.size(),x+3,111,slot-9,Math.min(44,Math.max(12,height-242)));BuilderUi.text(g,Integer.toString(i+1),x+5,105,BuilderUi.ACCENT);if(height>=300)BuilderUi.text(g,BuilderUi.fit(byId.containsKey(c.getVehicleId())?byId.get(c.getVehicleId()).getName().getString():c.getVehicleId(),slot-8),x+4,102+Math.min(79,Math.max(30,height-210))-13,BuilderUi.TEXT);}
        BuilderUi.text(g,draft.size()+" voitures  ·  "+String.format(Locale.ROOT,"%.1f / %.1f m",length(),siding.getRailLength()),divider+12,109+Math.min(79,Math.max(30,height-210)),length()>siding.getRailLength()?0xFFFFAD82:BuilderUi.TEXT);if(height>=300)BuilderUi.text(g,"Glisser une voiture pour changer sa place",divider+12,205,BuilderUi.MUTED);
        BuilderUi.text(g,(page+1)+" / "+Math.max(1,(list.size()+rows-1)/rows),117,height-59,BuilderUi.MUTED);BuilderUi.text(g,BuilderUi.fit(message.isEmpty()&&selected>=0&&selected<draft.size()?"Voiture "+(selected+1)+" : "+(byId.containsKey(draft.get(selected).getVehicleId())?byId.get(draft.get(selected).getVehicleId()).getName().getString():draft.get(selected).getVehicleId()):message,width-24),12,height-41,0xFFFFD09A);super.render(g,mx,my,dt);
    }
    @Override public boolean mouseClicked2(double mx,double my,int button){if(super.mouseClicked2(mx,my,button))return true;if(button!=0)return false;var list=filtered();if(mx>=12&&mx<divider-12&&my>=71&&my<71+rows*62){int i=page*rows+(int)((my-71)/62);if(i<list.size())add(list.get(i));return true;}if(mx>=divider+12&&mx<width-12&&my>=102&&my<=102+Math.min(79,Math.max(30,height-210))){int i=carPage*slots+(int)((mx-divider-12)/Math.max(35,(width-divider-24)/slots));if(i<draft.size()){selected=i;drag=i;}return true;}return false;}
    @Override public boolean mouseReleased2(double mx,double my,int button){if(drag>=0&&my>=102&&my<=102+Math.min(79,Math.max(30,height-210))&&mx>=divider+12&&mx<width-12){int to=Math.min(draft.size()-1,carPage*slots+(int)((mx-divider-12)/Math.max(35,(width-divider-24)/slots)));VehicleCar c=draft.remove(drag);draft.add(to,c);selected=to;}drag=-1;return super.mouseReleased2(mx,my,button);}
    @Override public boolean mouseScrolled2(double x,double y,double amount){if(x<divider)page=Math.max(0,Math.min(Math.max(0,(filtered().size()-1)/rows),page+(amount>0?-1:1)));else carPage=Math.max(0,Math.min(Math.max(0,(draft.size()-1)/slots),carPage+(amount>0?-1:1)));return true;}
    @Override public void tick2(){search.tick2();presetName.tick2();}
    @Override public void onClose2(){MinecraftClient.getInstance().openScreen(previous==null?null:new Screen(previous));}
    @Override public boolean isPauseScreen2(){return false;}
}
