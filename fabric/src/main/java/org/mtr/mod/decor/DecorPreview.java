package org.mtr.mod.decor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Deterministic orthographic renderer of production meshes; used by the editor and audit. */
public final class DecorPreview {
    private static final Map<String,BufferedImage> TEXTURES=new HashMap<>();
    private record P(double x,double y,double z) {}
    private record Poly(DecorMesh.Face f,P[] p,double z) {}
    private static BufferedImage texture(String key){synchronized(TEXTURES){return TEXTURES.computeIfAbsent(key,k->{try(var in=DecorPreview.class.getResourceAsStream("/assets/mtr/textures/decor/"+k+".png")){return ImageIO.read(in);}catch(Exception e){return new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);}});}}
    public static BufferedImage render(DecorSpec s,int width,int height,double angle){
        DecorMesh mesh=DecorMesh.get(s);var out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();
        g.dispose();
        double yaw=Math.toRadians(angle),pitch=Math.toRadians(23);var polys=new ArrayList<Poly>();double minx=1e6,miny=1e6,maxx=-1e6,maxy=-1e6;
        for(var f:mesh.faces()){P[] pp=new P[4];int i=0;double zz=0;for(var p:List.of(f.a(),f.b(),f.c(),f.d())){
            double x=p.x()*Math.cos(yaw)+p.z()*Math.sin(yaw),z=-p.x()*Math.sin(yaw)+p.z()*Math.cos(yaw),y=p.y()*Math.cos(pitch)-z*Math.sin(pitch);z=p.y()*Math.sin(pitch)+z*Math.cos(pitch);
            pp[i++]=new P(x,-y,z);zz+=z;minx=Math.min(minx,x);maxx=Math.max(maxx,x);miny=Math.min(miny,-y);maxy=Math.max(maxy,-y);
        }polys.add(new Poly(f,pp,zz/4));}
        double scale=Math.min((width-48)/Math.max(.01,maxx-minx),(height-48)/Math.max(.01,maxy-miny));
        double left=(width-(maxx-minx)*scale)/2-minx*scale,top=(height-(maxy-miny)*scale)/2-miny*scale;
        double[] depth=new double[width*height];Arrays.fill(depth,Double.NEGATIVE_INFINITY);
        polys.sort(Comparator.comparingInt((Poly p)->p.f.surface().equals("glass")?1:0).thenComparingDouble(Poly::z));
        for(var poly:polys){P[] p=poly.p;double[] x=new double[4],y=new double[4],z=new double[4];for(int i=0;i<4;i++){x[i]=p[i].x*scale+left;y[i]=p[i].y*scale+top;z[i]=p[i].z;}
            // Both faces are included deliberately: thin architectural panels are visible from either side.
            var f=poly.f;double nx=(f.b().y()-f.a().y())*(f.c().z()-f.a().z())-(f.b().z()-f.a().z())*(f.c().y()-f.a().y());
            double ny=(f.b().z()-f.a().z())*(f.c().x()-f.a().x())-(f.b().x()-f.a().x())*(f.c().z()-f.a().z());
            double nz=(f.b().x()-f.a().x())*(f.c().y()-f.a().y())-(f.b().y()-f.a().y())*(f.c().x()-f.a().x());double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            double shade=f.surface().equals("glow")?1:.66+.30*Math.abs((nx*.3+ny*.8+nz*.5)/Math.max(.0001,len));
            BufferedImage tex=texture(f.surface());
            if(f.surface().equals("label")){tex=new BufferedImage(512,128,BufferedImage.TYPE_INT_RGB);Graphics2D gg=tex.createGraphics();gg.setColor(new Color(s.accent()));gg.fillRect(0,0,512,128);gg.setColor(Color.WHITE);gg.setFont(new Font("SansSerif",Font.BOLD,44));gg.drawString(s.label(),12,80);gg.dispose();}
            tri(out,tex,depth,z,x,y,new int[]{0,1,2},new double[]{0,0,f.u()},new double[]{f.v(),0,0},f.color(),shade,f.surface().equals("glass")?.28:1);
            tri(out,tex,depth,z,x,y,new int[]{0,2,3},new double[]{0,f.u(),f.u()},new double[]{f.v(),0,f.v()},f.color(),shade,f.surface().equals("glass")?.28:1);
        }
        return out;
    }
    private static void tri(BufferedImage out,BufferedImage tex,double[] depths,double[] zz,double[] xx,double[] yy,int[] ids,double[] u,double[] v,int color,double shade,double alpha){
        double ax=xx[ids[0]],ay=yy[ids[0]],bx=xx[ids[1]],by=yy[ids[1]],cx=xx[ids[2]],cy=yy[ids[2]],den=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy);if(Math.abs(den)<.01)return;
        int minx=Math.max(0,(int)Math.floor(Math.min(ax,Math.min(bx,cx)))),maxx=Math.min(out.getWidth()-1,(int)Math.ceil(Math.max(ax,Math.max(bx,cx))));
        int miny=Math.max(0,(int)Math.floor(Math.min(ay,Math.min(by,cy)))),maxy=Math.min(out.getHeight()-1,(int)Math.ceil(Math.max(ay,Math.max(by,cy))));
        for(int y=miny;y<=maxy;y++)for(int x=minx;x<=maxx;x++){
            double a=((by-cy)*(x-cx)+(cx-bx)*(y-cy))/den,b=((cy-ay)*(x-cx)+(ax-cx)*(y-cy))/den,c=1-a-b;if(a<-.001||b<-.001||c<-.001)continue;
            double z=a*zz[ids[0]]+b*zz[ids[1]]+c*zz[ids[2]];int index=y*out.getWidth()+x;if(z<depths[index]-0.00001)continue;if(alpha>=1)depths[index]=z;
            double U=a*u[0]+b*u[1]+c*u[2],V=a*v[0]+b*v[1]+c*v[2];int tx=(int)(U*tex.getWidth()),ty=(int)(V*tex.getHeight());
            int t=tex.getRGB(Math.floorMod(tx,tex.getWidth()),Math.floorMod(ty,tex.getHeight())),old=out.getRGB(x,y);double oldAlpha=((old>>>24)&255)/255.0,total=alpha+oldAlpha*(1-alpha);int result=(int)(total*255)<<24;
            for(int shift:new int[]{16,8,0}){int channel=(int)(((t>>shift)&255)*((color>>shift)&255)/255.0*shade);channel=(int)((channel*alpha+((old>>shift)&255)*oldAlpha*(1-alpha))/Math.max(.0001,total));result|=Math.min(255,channel)<<shift;}
            out.setRGB(x,y,result);
        }
    }
}
