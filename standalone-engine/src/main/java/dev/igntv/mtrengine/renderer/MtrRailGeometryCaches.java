package dev.igntv.mtrengine.renderer;

import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.mod.client.MinecraftClientData;
import java.util.ArrayList;

/** Standalone rail geometry sample cache, pruned on the official sync hook. */
public final class MtrRailGeometryCaches {
    private static final RailGeometryCache CACHE=new RailGeometryCache();
    private MtrRailGeometryCaches(){}
    public static void render(RailMath math,RailMath.RenderRail draw,
                              double interval,float offset1,float offset2){
        CACHE.render(math,draw,interval,offset1,offset2);
    }
    public static void retain(MinecraftClientData data){
        var live=new ArrayList<Rail>();
        data.railWrapperList.values().forEach(wrapper->live.add(wrapper.getRail()));
        CACHE.retainRails(live);
    }
}
