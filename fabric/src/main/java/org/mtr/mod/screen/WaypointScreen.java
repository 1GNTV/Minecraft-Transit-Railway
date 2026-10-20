package org.mtr.mod.screen;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.builder.*;
import org.mtr.mod.client.*;
import org.mtr.mod.data.SillonSignageNetworkSnapshot;
import org.mtr.mod.packet.PacketBuilderWayfinding;
import java.util.*;

public final class WaypointScreen extends ScreenExtension {
    private final BlockPos pos;
    private final WalkGraph.Node original;
    private final List<Long> links;
    private TextFieldWidgetExtension label;
    private String name,kind,error="";
    private long platform;
    private int mode,page;
    public WaypointScreen(BlockPos pos){
        this.pos=pos;var node=WayfindingClient.graph().get(pos.asLong());
        original=node==null?new WalkGraph.Node(pos.asLong(),pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5,0,"","passage",0,List.of()):node;
        name=original.label();kind=original.kind();platform=original.platform();links=new ArrayList<>(original.links());
    }
    private void refresh(){MinecraftClient.getInstance().openScreen(new Screen(this));}
    private void button(int x,int y,int w,String label,Runnable action){addChild(new ClickableWidget(BuilderUi.button(x,y,w,label,action)));}
    private List<SillonSignageNetworkSnapshot.Platform> platforms(){return SillonSignageNetwork.snapshot().platforms().stream().filter(p->Math.hypot(p.center().x()-pos.getX(),p.center().z()-pos.getZ())<192).sorted(Comparator.comparing(SillonSignageNetworkSnapshot.Platform::name)).toList();}
    private String platformName(long id){var data=SillonSignageNetwork.snapshot();var p=data.platforms().stream().filter(q->q.id()==id).findFirst().orElse(null);if(p==null)return id==0?"Aucun quai":"Quai "+id;var s=data.stations().stream().filter(q->q.id()==p.stationId()).findFirst().orElse(null);return (s==null?"":s.name().split("[|]")[0]+" · ")+"quai "+p.name();}
    @Override protected void init2(){
        super.init2();button(12,height-28,76,"Annuler",()->MinecraftClient.getInstance().openScreen(null)));
        if(mode!=0){
            int rows=Math.max(1,(height-96)/26);
            if(mode==1){var list=platforms();page=Math.min(page,Math.max(0,(list.size()-1)/rows));for(int n=0;n<rows&&page*rows+n<list.size();n++){var p=list.get(page*rows+n);button(12,42+n*26,width-24,BuilderUi.fit(platformName(p.id()),width-40),()->{platform=p.id();kind="platform";mode=0;refresh();});}}
            else{page=Math.min(page,Math.max(0,(links.size()-1)/rows));for(int n=0;n<rows&&page*rows+n<links.size();n++){long id=links.get(page*rows+n);var node=WayfindingClient.graph().get(id);button(12,42+n*26,width-24,BuilderUi.fit("Délier "+(node==null?Long.toString(id):node.label().isBlank()?String.format(Locale.ROOT,"%.0f / %.0f / %.0f",node.x(),node.y(),node.z()):node.label()),width-40),()->{links.remove(id);refresh();});}}
            button(width-124,height-28,24,"‹",()->{page=Math.max(0,page-1);refresh();});button(width-96,height-28,24,"›",()->{page++;refresh();});button(width-68,height-28,56,"Retour",()->{mode=0;refresh();});return;
        }
        label=new TextFieldWidgetExtension(12,46,width-24,20,64,TextCase.DEFAULT,null,"Nom du passage / de la sortie");label.setText2(name);label.setChangedListener2(t->name=t);addChild(new ClickableWidget(label));
        var types=List.of("passage","platform","exit","stairs","lift");var titles=List.of("Passage","Quai","Sortie","Escalier","Ascenseur");
        button(12,76,width-24,"Type : "+titles.get(types.indexOf(kind)),()->{kind=types.get((types.indexOf(kind)+1)%types.size());refresh();});
        button(12,102,width-24,BuilderUi.fit(platformName(platform)+"…",width-40),()->{mode=1;page=0;refresh();});
        button(12,128,(width-30)/2,"Retirer le quai",()->{platform=0;refresh();});
        button(18+(width-30)/2,128,(width-30)/2,links.size()+" liaisons…",()->{mode=2;page=0;refresh();});
        button(width-116,height-28,104,"Enregistrer",this::save);
    }
    private void save(){
        try{
            if(kind.equals("platform")&&platform==0)throw new IllegalArgumentException("Associe un quai à ce point.");
            WalkGraph update=new WalkGraph();update.put(new WalkGraph.Node(original.id(),original.x(),original.y(),original.z(),platform,name,kind,original.revision(),links));
            InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketBuilderWayfinding(1,pos.asLong(),0,update.encode()));MinecraftClient.getInstance().openScreen(null);
        }catch(RuntimeException failure){error=failure.getMessage();}
    }
    @Override public void render(GraphicsHolder g,int mx,int my,float delta){
        BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"SILLON  /  POINT DE CHEMINEMENT",12,12,BuilderUi.ACCENT);
        if(mode==0){BuilderUi.text(g,"Identification",12,32,BuilderUi.MUTED);BuilderUi.text(g,BuilderUi.fit("Maj + clic au brush sur deux points pour les relier.",width-24),12,161,BuilderUi.MUTED);if(height>=300)BuilderUi.text(g,"Place un point à chaque tournant et palier du chemin réel.",12,177,BuilderUi.MUTED);}
        BuilderUi.text(g,BuilderUi.fit(error.isEmpty()?WayfindingClient.feedback:error,width-24),12,height-45,error.isEmpty()?BuilderUi.MUTED:0xFFFFAD82);super.render(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled2(double x,double y,double amount){if(mode!=0){page=Math.max(0,page+(amount>0?-1:1));refresh();return true;}return super.mouseScrolled2(x,y,amount);}
    @Override public void tick2(){if(label!=null)label.tick2();}
    @Override public void onClose2(){if(mode!=0){mode=0;refresh();}else save();}
    @Override public boolean isPauseScreen2(){return false;}
}
