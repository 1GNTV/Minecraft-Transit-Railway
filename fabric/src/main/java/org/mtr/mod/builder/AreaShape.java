package org.mtr.mod.builder;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Position;
public interface AreaShape {
    RegionPolygon sillon$getPolygon();
    void sillon$setPolygon(RegionPolygon polygon);
    static RegionPolygon polygon(AreaBase<?,?> area){return ((AreaShape)(Object)area).sillon$getPolygon();}
    static RegionPolygon outline(AreaBase<?,?> area){RegionPolygon p=polygon(area);return p==null?RegionPolygon.rectangle(area.getMinX(),area.getMinZ(),area.getMaxX(),area.getMaxZ()):p;}
    static void apply(AreaBase<?,?> area,RegionPolygon polygon){
        ((AreaShape)(Object)area).sillon$setPolygon(polygon);
        if(polygon!=null)area.setCorners(new Position((long)Math.floor(polygon.minX()),area.getMinY(),(long)Math.floor(polygon.minZ())),new Position((long)Math.ceil(polygon.maxX()),area.getMaxY(),(long)Math.ceil(polygon.maxZ())));
    }
}
