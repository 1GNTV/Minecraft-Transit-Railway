package org.sillon.qa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import org.mtr.core.data.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.NativeImageBackedTexture;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.render.TrainSilhouetteCache;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.screen.ConsistBuilderScreen;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real full-page GPU regression: the old 48-slot cache churned at 48 cards plus five cars. */
public final class PreviewVisualQa {
    private static boolean active;
    private static int stage,ticks;
    private static long frames,readyAt=-1;
    private static ConsistBuilderScreen screen;
    private static List<VehicleResource> cars;
    private static Map<TrainSilhouetteCache.Key,Object> baseline;
    public static void register(){MinecraftForge.EVENT_BUS.addListener(PreviewVisualQa::tick);MinecraftForge.EVENT_BUS.addListener((TickEvent.RenderTickEvent e)->{if(e.phase==TickEvent.Phase.END)frames++;});}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void page(int value)throws Exception{Field f=ConsistBuilderScreen.class.getDeclaredField("page");f.setAccessible(true);f.setInt(screen,value);}
    private static int perPage()throws Exception{Method m=ConsistBuilderScreen.class.getDeclaredMethod("perPage");m.setAccessible(true);return (int)m.invoke(screen);}
    private static TrainSilhouetteCache.Key key(VehicleResource r,int car,int total){return new TrainSilhouetteCache.Key(r.getId(),r.hasContextualModels()?car:0,r.hasContextualModels()?total:1);}
    @SuppressWarnings("unchecked") private static Map<TrainSilhouetteCache.Key,Object> expected()throws Exception{
        Field f=TrainSilhouetteCache.class.getDeclaredField("CACHE");f.setAccessible(true);Map<TrainSilhouetteCache.Key,Object> cache=(Map<TrainSilhouetteCache.Key,Object>)f.get(null);
        List<VehicleResource> matches=(List<VehicleResource>)field(screen,"matches");int page=(int)field(screen,"page"),count=perPage();Map<TrainSilhouetteCache.Key,Object> result=new LinkedHashMap<>();
        for(int n=page*count;n<Math.min(matches.size(),(page+1)*count);n++)result.put(key(matches.get(n),0,1),cache.get(key(matches.get(n),0,1)));
        for(int n=0;n<cars.size();n++)result.put(key(cars.get(n),n,cars.size()),cache.get(key(cars.get(n),n,cars.size())));
        return result;
    }
    private static boolean ready(Map<TrainSilhouetteCache.Key,Object> expected)throws Exception{for(var e:expected.entrySet()){if(e.getValue()==null)return false;check(!(boolean)field(e.getValue(),"failed"),"Preview failed: "+e.getKey());if(!(boolean)field(e.getValue(),"ready"))return false;}return true;}
    private static void pixels(Map<TrainSilhouetteCache.Key,Object> expected)throws Exception{
        int old=GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_2D);try{for(var e:expected.entrySet()){var texture=(NativeImageBackedTexture)field(e.getValue(),"texture");GL33.glBindTexture(GL33.GL_TEXTURE_2D,texture.getGlId());var rgba=java.nio.ByteBuffer.allocateDirect(512*128*4);GL33.glGetTexImage(GL33.GL_TEXTURE_2D,0,GL33.GL_RGBA,GL33.GL_UNSIGNED_BYTE,rgba);int visible=0;for(int i=3;i<rgba.capacity();i+=4)if((rgba.get(i)&255)>0)visible++;check(visible>200,"Empty GPU thumbnail: "+e.getKey()+" pixels="+visible);}}finally{GL33.glBindTexture(GL33.GL_TEXTURE_2D,old);}System.out.println("PREVIEW_QA_NONEMPTY "+expected.size());
    }
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"preview-"+(Boolean.getBoolean("sillon.qa.shader")?"shader-":"native-")+name+".png",mc.getMainRenderTarget(),m->System.out.println("PREVIEW_CAPTURE "+m.getString()));}
    private static void next(){stage++;ticks=0;readyAt=-1;}
    public static void begin(Minecraft mc){try{
        active=true;stage=-1;ticks=0;readyAt=-1;mc.options.hideGui=false;GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1920,1200);mc.options.guiScale().set(2);mc.resizeDisplay();TrainSilhouetteCache.clear();
        List<VehicleResource> all=new ArrayList<>();CustomResourceLoader.iterateVehicles(TransportMode.TRAIN,all::add);all.sort(Comparator.comparing(r->r.getName().getString()));check(all.size()>110,"Need real train catalogue");
        cars=new ArrayList<>();for(VehicleResource r:all.subList(48,all.size()))if(r.getId().contains("cm_stock")&&cars.size()<5)cars.add(r);for(int n=70;cars.size()<5;n++)if(!cars.contains(all.get(n)))cars.add(all.get(n));
        var siding=new Siding(new Position(0,4,0),new Position(0,4,150),150,TransportMode.TRAIN,new ClientData());var consist=new ObjectArrayList<VehicleCar>();for(var r:cars)consist.add(new VehicleCar(r.getId(),r.getLength(),r.getWidth(),r.getBogie1Position(),r.getBogie2Position(),r.getCouplingPadding1(),r.getCouplingPadding2()));siding.setVehicleCars(consist);screen=new ConsistBuilderScreen(siding,null);
    }catch(Throwable e){e.printStackTrace();System.exit(2);}}
    private static void tick(TickEvent.ClientTickEvent event){if(!active||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();try{
        if(++ticks>6000)throw new IllegalStateException("Preview regression timeout stage="+stage+" ready="+TrainSilhouetteCache.readyCount());
        if(stage==-1){if(mc.getWindow().getWidth()!=1920||mc.getWindow().getHeight()!=1200)return;mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(screen);check(perPage()==48,"Did not reproduce user viewport: "+perPage());next();return;}
        if(stage==3&&(mc.getWindow().getWidth()!=640||mc.getWindow().getHeight()!=480))return;
        var wanted=expected();boolean ready=ready(wanted);if(ready&&readyAt<0)readyAt=frames;
        if(stage==0&&ready&&frames>=readyAt+20){check(wanted.size()>48,"Cache overflow not reproduced");pixels(wanted);baseline=new LinkedHashMap<>(wanted);capture(mc,"1920-full-page");page(1);next();}
        else if(stage==1&&ready&&frames>=readyAt+10){pixels(wanted);capture(mc,"1920-second-page");page(0);next();}
        else if(stage==2&&ready&&frames>=readyAt+10){for(var e:baseline.entrySet())check(wanted.get(e.getKey())==e.getValue(),"Returning to page discarded a ready thumbnail: "+e.getKey());pixels(wanted);mc.options.guiScale().set(1);mc.resizeDisplay();check(perPage()==TrainSilhouetteCache.MAX_CATALOG_PROFILES,"Catalogue exceeds bounded thumbnail working set");GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),640,480);mc.options.guiScale().set(2);mc.resizeDisplay();next();}
        else if(stage==3&&ready&&frames>=readyAt+10){pixels(wanted);capture(mc,"640-compact");Files.writeString(Path.of(Boolean.getBoolean("sillon.qa.shader")?"preview-shader-qa-success.txt":"preview-native-qa-success.txt"),"All 48 visible catalogue thumbnails plus five different consist resources are ready, have nonempty GPU pixels and remain cached across page navigation; second page and compact view verified; large catalogue pagination capped at 96. Actual assembled Forge JAR.");System.out.println("PREVIEW_QA_SUCCESS");active=false;ChantierVisualQa.begin(mc);}
    }catch(Throwable e){System.err.println("PREVIEW_QA_FAILED stage="+stage);e.printStackTrace();System.exit(2);}}
}
