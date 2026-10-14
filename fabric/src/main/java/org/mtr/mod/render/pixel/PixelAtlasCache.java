package org.mtr.mod.render.pixel;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.mtr.mod.Init;
import org.mtr.mod.render.MoreRenderLayers;
import org.mtr.mod.render.MainRenderer;
import java.awt.Font;
import java.nio.file.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Shared GPU-resident atlases. All GL ownership stays on the render thread. */
public final class PixelAtlasCache {
    public static final PixelAtlasCache INSTANCE=new PixelAtlasCache();
    private static final long MAX_BYTES=64L*1024*1024;
    private final LinkedHashMap<PanamSurface.Spec,Entry> ready=new LinkedHashMap<>(32,.75f,true);
    private final HashSet<PanamSurface.Spec> pending=new HashSet<>();
    private final ArrayBlockingQueue<Result> uploads=new ArrayBlockingQueue<>(8);
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"Sillon PIXEL compositor");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private volatile long generation;
    private long bytes,serial;
    private Font regular,bold;
    private final AtomicLong builds=new AtomicLong(),buildNanos=new AtomicLong();
    private long uploaded,cacheHits,quads;
    public static final class Entry {
        public final PanamSurface surface;public final Identifier texture;private long touched;
        Entry(PanamSurface surface,Identifier texture,long touched){this.surface=surface;this.texture=texture;this.touched=touched;}
    }
    private static final class Result {
        final long generation;final PanamSurface surface;final PanamSurface.Spec spec;final NativeImage image;
        Result(long generation,PanamSurface.Spec spec,PanamSurface surface,NativeImage image){this.generation=generation;this.spec=spec;this.surface=surface;this.image=image;}
    }
    private PixelAtlasCache(){}
    public Entry get(PanamSurface.Spec spec,long now){
        Entry entry=ready.get(spec);if(entry!=null){entry.touched=now;cacheHits++;return entry;}
        if(pending.contains(spec)||pending.size()>=8)return null;
        loadFonts();final Font r=regular,b=bold;final long token=generation;
        pending.add(spec);
        try{worker.execute(()->{
            if(token!=generation)return;
            PanamSurface surface=null;NativeImage nativeImage=null;long start=System.nanoTime();
            try{
                surface=new PanamSurface(spec,r,b);
                nativeImage=new NativeImage(NativeImageFormat.getAbgrMapped(),(int)surface.atlasWidth,(int)surface.atlasHeight,false);
                for(int y=0;y<surface.atlasHeight;y++)for(int x=0;x<surface.atlasWidth;x++){int c=surface.image.getRGB(x,y);nativeImage.setPixelColor(x,y,(c&0xFF00FF00)|((c>>16)&255)|((c&255)<<16));}
                builds.incrementAndGet();buildNanos.addAndGet(System.nanoTime()-start);
            }catch(Exception error){if(nativeImage!=null)nativeImage.close();nativeImage=null;Init.LOGGER.error("PIXEL surface preparation failed",error);}
            finally{if(surface!=null){surface.image.flush();surface.image=null;}}
            Result result=new Result(token,spec,surface,nativeImage);
            if(token!=generation||!uploads.offer(result)){if(nativeImage!=null)nativeImage.close();MinecraftClient.getInstance().execute(()->{if(token==generation)pending.remove(spec);});}
        });}catch(RejectedExecutionException rejected){pending.remove(spec);}
        return null;
    }
    public void tick(){
        long now=System.currentTimeMillis();
        Iterator<Entry> it=ready.values().iterator();
        while(it.hasNext()){Entry e=it.next();if(now-e.touched>60000){release(e);it.remove();}}
        Result result=uploads.poll();if(result==null)return;
        if(result.generation!=generation){if(result.image!=null)result.image.close();return;}
        pending.remove(result.spec);PanamSurface s=result.surface;if(s==null||result.image==null)return;
        long cost=(long)s.atlasWidth*(long)s.atlasHeight*4;
        it=ready.values().iterator();while(bytes+cost>MAX_BYTES&&it.hasNext()){Entry e=it.next();if(now-e.touched>2000){release(e);it.remove();}}
        if(bytes+cost>MAX_BYTES){result.image.close();return;}
        NativeImage image=result.image;
        NativeImageBackedTexture texture=null;Identifier id=new Identifier(Init.MOD_ID,"pixel/atlas_"+generation+"_"+(serial++));boolean registered=false;
        try{
            texture=new NativeImageBackedTexture(image);texture.setFilter(true,false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id,new AbstractTexture(texture.data));registered=true;
            ready.put(result.spec,new Entry(s,id,now));bytes+=cost;uploaded++;
        }catch(Exception error){Init.LOGGER.error("PIXEL atlas upload failed",error);}
        finally{if(!registered){if(texture==null)image.close();else texture.close();}}
    }
    private void release(Entry e){MainRenderer.cancelRender(e.texture);MinecraftClient.getInstance().getTextureManager().destroyTexture(e.texture);MoreRenderLayers.removeFromCache(e.texture);bytes-=(long)e.surface.atlasWidth*(long)e.surface.atlasHeight*4;}
    public void reset(){generation++;worker.getQueue().clear();for(Entry e:ready.values())release(e);ready.clear();pending.clear();Result r;while((r=uploads.poll())!=null)if(r.image!=null)r.image.close();regular=null;bold=null;}
    private void loadFonts(){
        if(regular!=null)return;
        Font[] loaded=new Font[2];
        // Optional user-provided licensed Parisine font: never bundled in the JAR.
        Path personalFonts=Paths.get(System.getProperty("user.home",""),".sillon","fonts");
        for(int i=0;i<2;i++){
            Path file=personalFonts.resolve(i==0?"Parisine-Regular.otf":"Parisine-Bold.otf");
            if(Files.isRegularFile(file))try(InputStream input=Files.newInputStream(file)){
                loaded[i]=Font.createFont(Font.TRUETYPE_FONT,input);
            }catch(Exception error){Init.LOGGER.warn("PANAM Parisine local loading failed: " + file,error);}
        }
        // A resource pack may supply fonts its owner is licensed to use. None are downloaded at runtime.
        ResourceManagerHelper.readDirectory("font",(id,input)->{
            String name=id.toString();int slot=name.endsWith("panam/parisine-regular.otf")?0:name.endsWith("panam/parisine-bold.otf")?1:-1;
            if(slot>=0&&name.startsWith("mtr:")&&loaded[slot]==null)try{loaded[slot]=Font.createFont(Font.TRUETYPE_FONT,input);}catch(Exception e){Init.LOGGER.warn("Invalid personal PANAM font {}",id);}
        });
        for(int i=0;i<2;i++){final int index=i;if(loaded[i]==null)ResourceManagerHelper.readResource(new Identifier(Init.MOD_ID,i==0?"font/panam/noto-sans-regular.ttf":"font/noto-sans-semibold.ttf"),input->{try{loaded[index]=Font.createFont(Font.TRUETYPE_FONT,input);}catch(Exception e){Init.LOGGER.error("PIXEL fallback font",e);}});}
        regular=loaded[0]==null?new Font(Font.SANS_SERIF,Font.PLAIN,16):loaded[0];bold=loaded[1]==null?regular.deriveFont(Font.BOLD):loaded[1];
    }
    public void quad(){quads++;}
    public String diagnostics(){return "PIXEL builds="+builds.get()+" compositorNs="+buildNanos.get()+" uploads="+uploaded+" hits="+cacheHits+" quads="+quads+" residentBytes="+bytes+" pending="+pending.size();}
}
