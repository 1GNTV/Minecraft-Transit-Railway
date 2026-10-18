package org.mtr.mod.client;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.mtr.mod.data.SillonSignageSpec;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Minecraft upload bridge. All visual composition lives in the tested pure painter. */
public final class SillonSignageCanvas {
    static {
        SillonSignagePainter.setResourceResolver(path->{
            final BufferedImage[] image={null};
            try {
                String namespace="mtr",resource=path;
                int colon=path.indexOf(':');
                if(colon>=0){namespace=path.substring(0,colon);resource=path.substring(colon+1);}
                ResourceManagerHelper.readResource(new Identifier(namespace,resource),input->{
                    try{image[0]=ImageIO.read(input);}catch(Exception ignored){}
                });
            }catch(Exception ignored){}
            return image[0];
        });
    }
    private SillonSignageCanvas(){}
    public static NativeImage generate(SillonSignageSpec spec) {
        BufferedImage image=SillonSignagePainter.render(spec);
        int w=image.getWidth(),h=image.getHeight();
        NativeImage out=new NativeImage(NativeImageFormat.getAbgrMapped(),w,h,false);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int argb=image.getRGB(x,y);
            out.setPixelColor(x,y,(argb&0xFF00FF00)|((argb>>16)&255)|((argb&255)<<16));
        }
        image.flush();return out;
    }
}
