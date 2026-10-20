package org.mtr.mod.builder;
import org.mtr.mapping.mapper.GuiDrawing;
import java.util.*;
import java.util.function.DoubleUnaryOperator;
/** Polygon fill clipped to the viewport; concave regions use their validated triangulation. */
public final class PolygonDrawing {
    private PolygonDrawing(){}
    public static void draw(GuiDrawing drawing,RegionPolygon polygon,DoubleUnaryOperator x,DoubleUnaryOperator z,double left,double top,double right,double bottom,int fill,int border){
        var points=polygon.points();
        for(int[] triangle:polygon.triangles()){
            List<RegionPolygon.Point> clipped=new ArrayList<>();for(int i:triangle){var p=points.get(i);clipped.add(new RegionPolygon.Point(x.applyAsDouble(p.x()),z.applyAsDouble(p.z())));}
            for(int edge=0;edge<4;edge++)clipped=clip(clipped,edge,new double[]{left,top,right,bottom}[edge]);
            if(clipped.size()<3)continue;var a=clipped.get(0);for(int i=1;i<clipped.size()-1;i++){var b=clipped.get(i);var c=clipped.get(i+1);drawing.drawRectangle(a.x(),a.z(),b.x(),b.z(),c.x(),c.z(),c.x(),c.z(),fill);}
        }
        if(border!=0)for(int i=0;i<points.size();i++){var a=points.get(i);var b=points.get((i+1)%points.size());line(drawing,x.applyAsDouble(a.x()),z.applyAsDouble(a.z()),x.applyAsDouble(b.x()),z.applyAsDouble(b.z()),left,top,right,bottom,2,border);}
    }
    private static List<RegionPolygon.Point> clip(List<RegionPolygon.Point> input,int edge,double bound){
        List<RegionPolygon.Point> out=new ArrayList<>();if(input.isEmpty())return out;var a=input.get(input.size()-1);boolean ai=inside(a,edge,bound);
        for(var b:input){boolean bi=inside(b,edge,bound);if(ai!=bi){double av=edge%2==0?a.x():a.z(),bv=edge%2==0?b.x():b.z(),t=(bound-av)/(bv-av);out.add(new RegionPolygon.Point(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t));}if(bi)out.add(b);a=b;ai=bi;}return out;
    }
    private static boolean inside(RegionPolygon.Point p,int edge,double bound){double v=edge%2==0?p.x():p.z();return edge<2?v>=bound:v<=bound;}
    public static void line(GuiDrawing d,double x,double y,double X,double Y,double left,double top,double right,double bottom,double width,int color){
        double dx=X-x,dy=Y-y,t0=0,t1=1;
        double[] p={-dx,dx,-dy,dy},q={x-left,right-x,y-top,bottom-y};
        for(int i=0;i<4;i++){if(Math.abs(p[i])<1e-9){if(q[i]<0)return;}else{double t=q[i]/p[i];if(p[i]<0)t0=Math.max(t0,t);else t1=Math.min(t1,t);if(t0>t1)return;}}
        BuilderUi.line(d,x+dx*t0,y+dy*t0,x+dx*t1,y+dy*t1,width,color);
    }
}
