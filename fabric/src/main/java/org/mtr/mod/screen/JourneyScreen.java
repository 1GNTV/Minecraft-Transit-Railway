package org.mtr.mod.screen;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.InitClient;
import org.mtr.mod.builder.BuilderUi;
import org.mtr.mod.client.SillonSignageNetwork;
import org.mtr.mod.data.SillonSignageNetworkSnapshot;
import org.mtr.mod.packet.PacketBuilderJourney;
import org.mtr.libraries.com.google.gson.*;
import java.util.*;

/** Public journey terminal, using live server timetable results instead of a separate routing model. */
public final class JourneyScreen extends ScreenExtension {
    private final ScreenExtension previous;
    private TextFieldWidgetExtension search;
    private long origin,destination,sent;
    private String query="",token="",message="";
    private boolean origins,results;
    private int page,legPage;
    private JsonObject response;
    public JourneyScreen(ScreenExtension previous){this.previous=previous;}
    private String primary(String value){return value.split("[|]",-1)[0];}
    private List<SillonSignageNetworkSnapshot.Station> stations(){var all=SillonSignageNetwork.snapshot().stations();return all.stream().filter(s->s.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(SillonSignageNetworkSnapshot.Station::name)).toList();}
    private String station(long id){return SillonSignageNetwork.snapshot().stations().stream().filter(s->s.id()==id).findFirst().map(s->primary(s.name())).orElse("Choisir le départ");}
    private void refresh(){MinecraftClient.getInstance().openScreen(new Screen(this));}
    private void button(int x,int y,int w,String caption,Runnable action){addChild(new ClickableWidget(BuilderUi.button(x,y,w,caption,action)));}
    @Override protected void init2(){
        super.init2();
        button(12,30,width-24,BuilderUi.fit("Départ : "+station(origin),width-40),()->{results=false;origins=true;query="";page=0;refresh();});
        if(!results){
            search=new TextFieldWidgetExtension(12,59,width-24,20,120,TextCase.DEFAULT,null,origins?"Rechercher le départ":"Où souhaites-tu aller ?");
            search.setText2(query);search.setChangedListener2(t->{query=t;page=0;});addChild(new ClickableWidget(search));
        }else button(12,height-53,130,"Autre destination",()->{results=false;response=null;query="";page=0;refresh();});
        button(12,height-28,76,"Fermer",this::onClose2);
        if(!results){button(width-68,height-28,25,"‹",()->page=Math.max(0,page-1));button(width-39,height-28,27,"›",()->page++);}
        else{button(width-110,height-28,98,"Actualiser",this::request);}
    }
    private void request(){
        if(origin==0||destination==0){message="Choisis une station de départ et d’arrivée.";return;}
        token=UUID.randomUUID().toString();sent=System.currentTimeMillis();response=null;results=true;legPage=0;message="Recherche dans les horaires MTR…";
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketBuilderJourney(token,origin,destination));refresh();
    }
    @Override public void tick2(){
        if(search!=null)search.tick2();
        var data=SillonSignageNetwork.snapshot();if(origin==0&&!data.stations().isEmpty()){var p=MinecraftClient.getInstance().getPlayerMapped();if(p!=null)origin=data.stations().stream().min(Comparator.comparingDouble(s->Math.pow(s.center().x()-p.getX(),2)+Math.pow(s.center().z()-p.getZ(),2))).orElseThrow().id();refresh();}
        if(!token.isEmpty()){String raw=PacketBuilderJourney.take(token);if(raw!=null){token="";try{response=JsonParser.parseString(raw).getAsJsonObject();message=response.has("error")?response.get("error").getAsString():"";}catch(RuntimeException e){message="Réponse du calculateur illisible.";}refresh();}else if(System.currentTimeMillis()-sent>15000){token="";message="Le calcul MTR prend trop de temps. Tu peux relancer la recherche.";}}
    }
    private static String text(JsonObject object,String key){return object.has(key)?object.get(key).getAsString():"";}
    private String time(JsonObject object,String key){try{long ms=object.get(key).getAsLong();return ms<=0?"":java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));}catch(RuntimeException e){return "";}}
    @Override public void render(GraphicsHolder g,int mx,int my,float dt){
        BuilderUi.rect(g,0,0,width,height,BuilderUi.BG);BuilderUi.text(g,"SILLON  /  ITINÉRAIRE VOYAGEUR",12,12,BuilderUi.ACCENT);
        if(!results){
            var all=stations();int rows=Math.max(1,(height-139)/28);page=Math.min(page,Math.max(0,(all.size()-1)/rows));
            for(int i=0;i<rows&&page*rows+i<all.size();i++){var s=all.get(page*rows+i);int y=89+i*28;BuilderUi.rect(g,12,y,width-24,24,mx>=12&&mx<=width-12&&my>=y&&my<y+24?0xFF304B5A:BuilderUi.PANEL);BuilderUi.text(g,BuilderUi.fit(primary(s.name()),width-44),20,y+8,BuilderUi.TEXT);}
            BuilderUi.text(g,BuilderUi.fit(message.isEmpty()?(origins?"Choisis la station de départ":"Choisis ta destination")+" · "+all.size()+" stations":message,width-24),12,height-45,BuilderUi.MUTED);
        }else{
            BuilderUi.text(g,BuilderUi.fit("Vers "+station(destination),width-24),12,59,BuilderUi.TEXT);
            if(response!=null&&response.has("legs")){
                JsonArray legs=response.getAsJsonArray("legs");int rows=Math.max(1,(height-142)/58);legPage=Math.min(legPage,Math.max(0,legs.size()-rows));
                for(int i=0;i<rows&&legPage+i<legs.size();i++){var leg=legs.get(legPage+i).getAsJsonObject();int y=77+i*58;
                    BuilderUi.rect(g,12,y,width-24,53,BuilderUi.PANEL);BuilderUi.rect(g,12,y,4,53,0xFF000000|leg.get("color").getAsInt());
                    BuilderUi.text(g,BuilderUi.fit(primary(text(leg,"line"))+" · "+primary(text(leg,"direction")),width-42),22,y+6,BuilderUi.ACCENT);
                    BuilderUi.text(g,BuilderUi.fit(primary(text(leg,"from"))+" → "+primary(text(leg,"to")),width-42),22,y+20,BuilderUi.TEXT);
                    String details="";if(!text(leg,"startPlatformName").isBlank())details="Quai "+text(leg,"startPlatformName")+" · ";int count=leg.has("stops")?leg.get("stops").getAsInt():-1;
                    details+=time(leg,"startTime")+" → "+time(leg,"endTime");if(count>0)details+=" · "+count+" arrêts";else if(leg.has("walkingDistance")&&leg.get("walkingDistance").getAsLong()>0)details+=" · "+leg.get("walkingDistance").getAsLong()+" m à pied";
                    BuilderUi.text(g,BuilderUi.fit(details,width-42),22,y+36,BuilderUi.MUTED);
                }
            }
            if(!message.isEmpty())BuilderUi.text(g,BuilderUi.fit(message,width-24),12,height-68,0xFFFFD09A);
        }
        super.render(g,mx,my,dt);
    }
    @Override public boolean mouseClicked2(double x,double y,int button){
        if(super.mouseClicked2(x,y,button))return true;if(results||button!=0)return false;
        int rows=Math.max(1,(height-139)/28);var all=stations();int at=(int)((y-89)/28);if(x>=12&&x<width-12&&y>=89&&at<rows&&page*rows+at<all.size()){long id=all.get(page*rows+at).id();if(origins){origin=id;origins=false;query="";page=0;refresh();}else{destination=id;request();}return true;}return false;
    }
    @Override public boolean mouseScrolled2(double x,double y,double amount){if(results)legPage=Math.max(0,legPage+(amount>0?-1:1));else page=Math.max(0,page+(amount>0?-1:1));return true;}
    @Override public void onClose2(){MinecraftClient.getInstance().openScreen(previous==null?null:new Screen(previous));}
    @Override public boolean isPauseScreen2(){return false;}
}
