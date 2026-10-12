package dev.igntv.mtrengine.renderer;

import org.mtr.mod.client.MinecraftClientData;

/** Keeps original MTR's network index out of MTR classes.
 * Refreshed after every upstream MinecraftClientData.sync(). */
public final class MtrRailIndexes {
    private static MinecraftClientData data;
    private static final RailSpatialIndex INDEX=new RailSpatialIndex();
    private MtrRailIndexes(){}
    public static void rebuild(MinecraftClientData current) {
        if(current==null)return;
        data=current;
        INDEX.rebuild(current.railWrapperList.values());
        MtrRailGeometryCaches.retain(current);
    }
    public static Iterable<MinecraftClientData.RailWrapper> query(
            MinecraftClientData current,double x,double z,int radius) {
        if(current!=data)rebuild(current);
        return INDEX.query(x,z,radius);
    }
    public static void clear() {
        data=null;
        INDEX.rebuild(java.util.List.of());
        MtrRailGeometryCaches.clear();
    }
}
