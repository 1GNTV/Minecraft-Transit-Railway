import org.mtr.mod.builder.*;
import java.util.*;
import java.nio.file.*;
public final class BuilderRegression {
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
    static void near(double a,double b,String name){check(Math.abs(a-b)<1e-8,name);}
    public static void main(String[] args)throws Exception {
        for(double scale:new double[]{1/128D,.2,1,8,64})for(double amount:new double[]{-2,-.15,.2,2}){
            double next=MapGeometry.zoom(scale,amount),center=123,offset=117,updated=MapGeometry.anchoredCenter(center,offset,scale,next);near(center+offset/scale,updated+offset/next,"Cursor anchored zoom");check(next>=1/128D&&next<=64,"Zoom bounds");
        }
        List<MapGeometry.Rect> labels=new ArrayList<>();var viewport=new MapGeometry.Rect(144,30,400,240);for(int n=0;n<100;n++){var r=MapGeometry.place(200+n%8*15,60+n/8*13,70,16,labels,viewport);if(r!=null){check(viewport.contains(r.x(),r.y())&&viewport.contains(r.x()+r.w(),r.y()+r.h()),"Label viewport");for(var other:labels)if(other!=r)check(!r.overlaps(other),"Label collision");}}
        near(MapGeometry.segmentDistance(2,3,0,0,4,0),3,"Segment distance");
        var polygon=List.of(new DoorGuideGeometry.Point(.1,-.8),new DoorGuideGeometry.Point(2.1,.2),new DoorGuideGeometry.Point(1.7,1),new DoorGuideGeometry.Point(-.3,0));double sum=0;for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++){var clipped=DoorGuideGeometry.clip(polygon,x,z);sum+=DoorGuideGeometry.area(clipped);for(var p:clipped)check(p.x()>=x-1e-9&&p.x()<=x+1+1e-9&&p.z()>=z-1e-9&&p.z()<=z+1+1e-9,"Platform-cell clip");}near(sum,DoorGuideGeometry.area(polygon),"Rotated door footprint area conserved");
        ConsistPresets.save("TRAIN","QA composition",List.of("pack:leading","pack:middle","pack:middle","pack:trailing"));check(ConsistPresets.load("TRAIN").get("QA composition").equals(List.of("pack:leading","pack:middle","pack:middle","pack:trailing")),"Ordered preset roundtrip");ConsistPresets.save("BOAT","QA boat",List.of("boat:test"));ConsistPresets.delete("TRAIN","QA composition");check(ConsistPresets.load("TRAIN").isEmpty()&&ConsistPresets.load("BOAT").size()==1,"Transport groups survive delete");boolean refused=false;try{ConsistPresets.save("TRAIN"," ",List.of());}catch(java.io.IOException e){refused=true;}check(refused,"Invalid name refused");
        System.out.println("PASS cursor zoom, labels, rotated platform door clipping, named consist ordering/persistence/mode isolation");
    }
}
