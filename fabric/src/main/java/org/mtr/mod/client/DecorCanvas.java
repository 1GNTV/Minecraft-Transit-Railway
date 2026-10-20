package org.mtr.mod.client;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.mtr.mod.decor.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Resource-pack-aware editor preview, using the same production mesh and image identifier. */
public final class DecorCanvas {
    static {DecorPreview.setResourceResolver(path->{BufferedImage[] result={null};try{ResourceManagerHelper.readResource(new Identifier(path),input->{try{result[0]=ImageIO.read(input);}catch(Exception ignored){}});}catch(Exception ignored){}return result[0];});}
    public static NativeImage generate(DecorSpec spec,int angle){
        BufferedImage image=DecorPreview.render(spec,640,480,angle);NativeImage out=new NativeImage(NativeImageFormat.getAbgrMapped(),640,480,false);
        for(int y=0;y<480;y++)for(int x=0;x<640;x++){int argb=image.getRGB(x,y);out.setPixelColor(x,y,(argb&0xFF00FF00)|((argb>>16)&255)|((argb&255)<<16));}
        image.flush();return out;
    }
}
