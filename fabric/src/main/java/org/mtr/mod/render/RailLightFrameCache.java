package org.mtr.mod.render;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.mapper.LightType;
import org.mtr.mapping.mapper.LightmapTextureManager;

/** Main + shader cascades reuse world lighting samples only inside one render tick.
 * Dynamic lighting can change after any tick; nothing survives beginFrame().
 * Coordinates outside Minecraft's standard packed BlockPos range bypass cache.
 */
public final class RailLightFrameCache {
    private static final boolean ENABLED=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railLightCache","true"));
    private static final int MAX_ENTRIES=65_536;
    private static final Long2IntOpenHashMap LIGHT=new Long2IntOpenHashMap(2048);
    private static long hits, misses;
    static {LIGHT.defaultReturnValue(Integer.MIN_VALUE);}
    private RailLightFrameCache(){}
    public static void beginFrame(){LIGHT.clear();hits=misses=0;}
    private static int sample(ClientWorld world,BlockPos pos){
        return LightmapTextureManager.pack(world.getLightLevel(LightType.getBlockMapped(),pos),
             world.getLightLevel(LightType.getSkyMapped(),pos));
    }
    public static int get(ClientWorld world,BlockPos pos){
        if(!ENABLED)return sample(world,pos);
        int x=pos.getX(),y=pos.getY(),z=pos.getZ();
        if(x < -33554432 || x > 33554431 || z < -33554432 || z > 33554431 || y < -2048 || y > 2047)
            return sample(world,pos);
        long key=(((long)x&0x3ffffffL)<<38)|(((long)z&0x3ffffffL)<<12)|((long)y&0xfffL);
        int cached=LIGHT.get(key);
        if(cached!=Integer.MIN_VALUE){hits++;return cached;}
        misses++;
        int light=sample(world,pos);
        if(LIGHT.size()<MAX_ENTRIES)LIGHT.put(key,light);
        return light;
    }
    public static long hits(){return hits;}
    public static long misses(){return misses;}
}
