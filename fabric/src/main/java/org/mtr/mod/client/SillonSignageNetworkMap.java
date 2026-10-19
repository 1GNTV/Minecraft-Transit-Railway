package org.mtr.mod.client;
import org.mtr.mod.data.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import static org.mtr.mod.client.SillonSignagePainter.*;

/** Legible octilinear network schematic generated from real stop identities and line topology. */
public final class SillonSignageNetworkMap {
    private record Edge(long a,long b){static Edge of(long a,long b){return new Edge(Math.min(a,b),Math.max(a,b));}}
    private SillonSignageNetworkMap(){}
    public static void draw(Graphics2D g,SillonSignageSpec s,int w,int h){
        String family=SillonRATPSignageLayouts.family(s);int ink=s.foreground();float pad=Math.min(w,h)*.045f;
        float header=h*(s.design().translations()?.17f:.13f);
        if(s.design().translations())text(g,"Network map",font(family,false,h*.023f),ink,pad,pad*.55f,w-2*pad,h*.035f,"left");
        inkText(g,s.title(),font(family,true,h*.15f),ink,pad,header*.29f,w-2*pad,header*.36f,"left");
        if(s.design().translations())text(g,"Mapa de la red",font(family,false,h*.023f),ink,pad,header*.71f,w-2*pad,h*.035f,"left");
        Map<Long,SillonSignagePlan.Stop> stops=new LinkedHashMap<>();Map<Long,Set<String>> connections=new HashMap<>();Map<String,SillonSignagePlan.Line> lines=new LinkedHashMap<>();
        Set<Long> termini=new HashSet<>();
        for(var line:s.plan().lines()){
            String id=line.mode()+"/"+line.label()+"/"+line.color();lines.putIfAbsent(id,line);
            if(!line.stops().isEmpty()){termini.add(line.stops().get(0).stationId());termini.add(line.stops().get(line.stops().size()-1).stationId());}
            for(var stop:line.stops()){stops.putIfAbsent(stop.stationId(),stop);connections.computeIfAbsent(stop.stationId(),k->new HashSet<>()).add(id);}
        }
        if(stops.isEmpty()){text(g,"Aucune desserte renseignée",font(family,false,h*.035f),ink,pad,h*.45f,w-pad*2,h*.1f,"center");return;}
        int legendCols=Math.max(1,(int)((w-2*pad)/(h*.19f))),legendRows=(lines.size()+legendCols-1)/legendCols;
        float legendUnit=Math.min(h*.04f,h*.16f/Math.max(1,legendRows)),legendH=legendRows*legendUnit*1.3f+pad;
        float mapX=pad+w*.07f,mapY=header+pad,mapW=w-2*mapX,mapH=h-mapY-legendH-pad*1.5f;
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=minX,maxZ=maxX;Map<Long,double[]> source=new LinkedHashMap<>();int n=0;
        for(var p:stops.values()){double x=p.positioned()?p.x():(n%6)*100,z=p.positioned()?p.z():(n/6)*100;n++;source.put(p.stationId(),new double[]{x,z});minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
        double scale=Math.min(mapW/Math.max(1,maxX-minX),mapH/Math.max(1,maxZ-minZ));
        float cell=Math.max(12,Math.min(w,h)/(float)Math.max(18,Math.sqrt(stops.size())*5));Map<Long,Point2D.Float> points=new LinkedHashMap<>();Set<String> occupied=new HashSet<>();
        for(var e:source.entrySet()){
            float x=(float)(mapX+(mapW-(maxX-minX)*scale)/2+(e.getValue()[0]-minX)*scale),y=(float)(mapY+(mapH-(maxZ-minZ)*scale)/2+(e.getValue()[1]-minZ)*scale);
            int gx=Math.round(x/cell),gy=Math.round(y/cell);String key=gx+"/"+gy;int d=0;
            while(!occupied.add(key)){gx+=++d%2==0?-d:d;key=gx+"/"+gy;}
            points.put(e.getKey(),new Point2D.Float(gx*cell,gy*cell));
        }
        Map<Edge,LinkedHashMap<String,Integer>> edges=new LinkedHashMap<>();
        for(var line:s.plan().lines())for(int i=1;i<line.stops().size();i++){
            long a=line.stops().get(i-1).stationId(),b=line.stops().get(i).stationId();if(a==b)continue;
            edges.computeIfAbsent(Edge.of(a,b),k->new LinkedHashMap<>()).putIfAbsent(line.mode()+"/"+line.label()+"/"+line.color(),line.color());
        }
        float stroke=Math.max(3,Math.min(w,h)*.0048f);List<Line2D.Float> segments=new ArrayList<>();
        for(var e:edges.entrySet()){
            var a=points.get(e.getKey().a);var b=points.get(e.getKey().b);float dx=b.x-a.x,dy=b.y-a.y,length=(float)Math.hypot(dx,dy);int index=0;
            for(int colour:e.getValue().values()){
                float offset=(index++-(e.getValue().size()-1)*.5f)*stroke*1.35f,ox=length==0?0:-dy/length*offset,oy=length==0?0:dx/length*offset;
                float elbowX=Math.abs(dx)>Math.abs(dy)?a.x+Math.copySign(Math.abs(dy),dx):b.x;
                float elbowY=Math.abs(dx)>Math.abs(dy)?b.y:a.y+Math.copySign(Math.abs(dx),dy);
                Path2D path=new Path2D.Float();path.moveTo(a.x+ox,a.y+oy);path.lineTo(elbowX+ox,elbowY+oy);path.lineTo(b.x+ox,b.y+oy);
                g.setColor(new Color(colour));g.setStroke(new BasicStroke(stroke,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(path);
            }
            segments.add(new Line2D.Float(a,b));
        }
        List<Long> order=new ArrayList<>(stops.keySet());order.sort(Comparator.<Long>comparingInt(id->id==s.plan().currentStation()?0:connections.get(id).size()>1?1:termini.contains(id)?2:3));
        float textSize=Math.max(10,Math.min(h*.022f,mapW/(float)Math.max(22,Math.sqrt(stops.size())*7)))*s.options().textScale();List<Rectangle2D.Float> labels=new ArrayList<>();
        for(long id:order){var p=points.get(id);boolean change=connections.get(id).size()>1;float r=stroke*(change?1.25f:.72f);g.setColor(Color.WHITE);g.fill(new Ellipse2D.Float(p.x-r,p.y-r,r*2,r*2));g.setStroke(new BasicStroke(Math.max(1,stroke*.23f)));g.setColor(new Color(ink));g.draw(new Ellipse2D.Float(p.x-r,p.y-r,r*2,r*2));
            Font face=font(family,change||termini.contains(id)||id==s.plan().currentStation(),textSize);String name=stops.get(id).name();float tw=(float)face.getStringBounds(name,g.getFontRenderContext()).getWidth(),th=textSize*1.4f,gap=r+5;
            float[][] places={{p.x+gap,p.y-th/2},{p.x-tw/2,p.y-gap-th},{p.x-tw/2,p.y+gap},{p.x-gap-tw,p.y-th/2},{p.x+gap,p.y-gap-th},{p.x-gap-tw,p.y+gap},{p.x+gap,p.y+gap},{p.x-gap-tw,p.y-gap-th}};
            Rectangle2D.Float chosen=null;double best=Double.MAX_VALUE;
            for(float[] at:places){var box=new Rectangle2D.Float(Math.max(pad,Math.min(w-pad-tw,at[0])),Math.max(header,Math.min(h-legendH-th,at[1])),tw,th);double cost=0;
                for(var prior:labels)if(prior.intersects(box)){var intersection=prior.createIntersection(box);cost+=intersection.getWidth()*intersection.getHeight()*10;}
                for(var line:segments)if(line.intersects(box))cost+=th*2;
                for(var point:points.values())if(box.contains(point))cost+=th*th*2;
                if(cost<best){chosen=box;best=cost;}
            }
            if(chosen!=null){g.setColor(new Color(s.background()));g.fill(new RoundRectangle2D.Float(chosen.x-2,chosen.y,chosen.width+4,chosen.height,4,4));text(g,name,face,ink,chosen.x,chosen.y,chosen.width,chosen.height,"left");labels.add(chosen);}
        }
        float y=h-legendH;g.setColor(new Color(0xBCBCBC));g.setStroke(new BasicStroke(1));g.draw(new Line2D.Float(pad,y-pad*.3f,w-pad,y-pad*.3f));int index=0;
        for(var line:lines.values()){float x=pad+(index%legendCols)*(w-pad*2)/legendCols,ly=y+(index/legendCols)*legendUnit*1.3f;
            String symbols=SillonSignageLineBadge.modeIcon(line.mode())+","+new SillonSignageLineBadge(line.mode(),line.color(),line.label()).token();
            icons(g,contrastSymbols(symbols,s.background(),ink),x,ly,(w-pad*2)/legendCols,legendUnit,legendUnit);index++;
        }
    }
}
