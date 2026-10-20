import org.mtr.mod.builder.*;
import java.util.*;
public final class BuilderNextRegression {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var cars=new ArrayList<>(List.of("A","B","C","D","E"));var draft=new ConsistDraft<>(cars);
        draft.select(1,false,false);draft.select(3,true,false);check(draft.selectedCount()==3,"Shift range");
        draft.move(5);check(cars.equals(List.of("A","E","B","C","D")),"Group reorder preserves order");
        draft.undo();check(cars.equals(List.of("A","B","C","D","E")),"Undo reorder");
        draft.redo();check(cars.equals(List.of("A","E","B","C","D")),"Redo reorder");
        draft.select(1,false,false);draft.select(4,false,true);check(draft.delete().equals(List.of("E","D")),"Non-contiguous deletion");
        check(cars.equals(List.of("A","B","C")),"Delete only selection");draft.undo();
        draft.select(0,false,false);draft.duplicate(5);check(cars.size()==5,"Length cap");
        draft.duplicate(6);check(cars.size()==6&&cars.get(1).equals("A"),"Duplicate selected group");
        var l=RegionPolygon.decode("0,0;10,0;10,3;3,3;3,10;0,10");
        check(l.contains(1,9,0)&&l.contains(9,1,0),"L legs included");
        check(!l.contains(8,8,0),"L notch excluded");check(l.contains(3,7,0),"Boundary inclusive");
        check(l.contains(3.2,7,.3)&&!l.contains(2.9,7,-.2),"Signed margin");
        check(!l.intersects(RegionPolygon.rectangle(5,5,8,8)),"Notch region does not intersect");
        check(l.intersects(RegionPolygon.rectangle(1,8,2,9)),"Nested region intersects");
        check(RegionPolygon.decode(l.encode()).points().equals(l.points()),"Polygon round trip");
        double area=0;for(int[] t:l.triangles()){var a=l.points().get(t[0]);var b=l.points().get(t[1]);var c=l.points().get(t[2]);area+=Math.abs((b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x()))/2;}
        check(Math.abs(area-51)<1e-7,"Concave triangulation area");var inside=l.interior();check(l.contains(inside.x(),inside.z(),0),"Label inside polygon");
        try{RegionPolygon.decode("0,0;5,5;0,5;5,0");throw new AssertionError("Self intersection accepted");}catch(IllegalArgumentException expected){}
        try{RegionPolygon.decode("0,0;NaN,1;2,2");throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}
        check(RegionPolygon.decode("")==null,"Legacy rectangle");
        System.out.println("PASS grouped editing, undo/redo, deletion, concave areas, boundaries, margins, intersections, persistence and triangulation");
    }
}
