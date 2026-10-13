package org.mtr.mod.render;

import java.util.*;

/**
 * Render-thread diff of rail-network snapshots. No Minecraft/GL dependencies.
 * Column invalidation covers all vertical sectors: custom rail model Y offsets
 * may span several chunk heights. Bounding boxes must include both the old
 * and new track position so moved/deleted rail meshes cannot linger.
 */
public final class RailColumnDelta {
    public record RailState(Object identity, int styleRevision,
                            double minX, double maxX, double minZ, double maxZ) {
        public RailState {
            Objects.requireNonNull(identity, "rail identity");
        }
    }
    public record Change(boolean full, Set<Long> columns, int changedRails) {}
    private final int maxDirtyColumns;
    private Map<String,RailState> previous = Map.of();

    public RailColumnDelta(int maxDirtyColumns) {
        if(maxDirtyColumns<=0)throw new IllegalArgumentException("maxDirtyColumns must be positive");
        this.maxDirtyColumns=maxDirtyColumns;
    }

    public Change update(Map<String,RailState> current) {
        Objects.requireNonNull(current,"current");
        HashSet<Long> dirty=new HashSet<>();
        int changed=0;
        boolean overflow=false;
        for(var entry:previous.entrySet()) {
            RailState next=current.get(entry.getKey());
            if(!same(entry.getValue(),next)) {
                changed++;
                if(!columns(entry.getValue(),dirty))overflow=true;
            }
        }
        for(var entry:current.entrySet()) {
            RailState prior=previous.get(entry.getKey());
            if(!same(prior,entry.getValue())) {
                if(prior==null)changed++;
                if(!columns(entry.getValue(),dirty))overflow=true;
            }
        }
        previous=new HashMap<>(current);
        // Defensive: an in-place update that was not expressible by geometry
        // identity/style fingerprint must not leave previously baked GPU data.
        if(changed==0 && !current.isEmpty())return new Change(true,Set.of(),0);
        if(overflow)return new Change(true,Set.of(),changed);
        return new Change(false,Set.copyOf(dirty),changed);
    }

    private static boolean same(RailState a,RailState b) {
        return a!=null && b!=null && a.identity==b.identity
                && a.styleRevision==b.styleRevision
                && Double.compare(a.minX,b.minX)==0 && Double.compare(a.maxX,b.maxX)==0
                && Double.compare(a.minZ,b.minZ)==0 && Double.compare(a.maxZ,b.maxZ)==0;
    }

    private boolean columns(RailState state,Set<Long> output) {
        if(!Double.isFinite(state.minX)||!Double.isFinite(state.maxX)
                ||!Double.isFinite(state.minZ)||!Double.isFinite(state.maxZ)
                ||state.minX>state.maxX||state.minZ>state.maxZ)return false;
        double x0=Math.floor(state.minX/16D),x1=Math.floor(state.maxX/16D);
        double z0=Math.floor(state.minZ/16D),z1=Math.floor(state.maxZ/16D);
        if(x0<Integer.MIN_VALUE || x1>Integer.MAX_VALUE
                || z0<Integer.MIN_VALUE || z1>Integer.MAX_VALUE)return false;
        long count=(long)(x1-x0+1)*(long)(z1-z0+1);
        if(count<=0 || count>maxDirtyColumns || output.size()+count>maxDirtyColumns)return false;
        for(int x=(int)x0;x<=x1;x++)for(int z=(int)z0;z<=z1;z++) {
            output.add((((long)x)<<32) ^ (z&0xffffffffL));
            if(output.size()>maxDirtyColumns)return false;
        }
        return true;
    }

    public void clear(){previous=Map.of();}
}
