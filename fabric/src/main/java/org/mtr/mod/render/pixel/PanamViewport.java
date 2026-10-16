package org.mtr.mod.render.pixel;

import java.util.Objects;
import org.mtr.mod.data.PanamScreenSettings;

/** CSS-like logical viewport, independent from the texture's raster density. */
public final class PanamViewport {
    public final int width,height,resolutionWidth,resolutionHeight;
    public PanamViewport(PanamScreenSettings s){
        float longest=1024/s.zoom,aspect=s.width/s.height;
        width=Math.max(8,Math.round(aspect>=1?longest:longest*aspect));
        height=Math.max(8,Math.round(aspect>=1?longest/aspect:longest));
        resolutionWidth=s.resolutionWidth;resolutionHeight=s.resolutionHeight;
    }
    @Override public boolean equals(Object o){if(!(o instanceof PanamViewport))return false;PanamViewport v=(PanamViewport)o;return width==v.width&&height==v.height&&resolutionWidth==v.resolutionWidth&&resolutionHeight==v.resolutionHeight;}
    @Override public int hashCode(){return Objects.hash(width,height,resolutionWidth,resolutionHeight);}
}
