package org.mtr.mod.decor;

import java.util.*;

/** Game-independent geometry. The exact same quads feed Minecraft and the review renderer. */
public final class DecorMesh {
    public record Point(float x,float y,float z) {}
    public record Face(Point a,Point b,Point c,Point d,String surface,int color,float u,float v,boolean boxUv) {}
    private final List<Face> faces=new ArrayList<>();
    public record Solid(double x,double y,double z,double X,double Y,double Z) {}
    private final List<Solid> solids=new ArrayList<>();
    private List<Face> faceView;
    public List<Solid> solids(){return Collections.unmodifiableList(solids);}
    private void solid(double x,double y,double z,double X,double Y,double Z){if(X>x&&Y>y&&Z>z)solids.add(new Solid(x,y,z,X,Y,Z));}
    private final DecorSpec s;
    public float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
    private static final Map<DecorSpec,DecorMesh> CACHE=Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<DecorSpec,DecorMesh> e){return size()>96;}});
    public static DecorMesh get(DecorSpec s){synchronized(CACHE){DecorMesh mesh=CACHE.get(s);if(mesh!=null)return mesh;mesh=new DecorMesh(s);CACHE.put(s,mesh);int weight=CACHE.values().stream().mapToInt(m->m.faces.size()).sum();var it=CACHE.entrySet().iterator();while(weight>180000&&CACHE.size()>1&&it.hasNext()){var e=it.next();weight-=e.getValue().faces.size();it.remove();}return mesh;}}
    public List<Face> faces(){return faceView==null?(faceView=Collections.unmodifiableList(faces)):faceView;}
    public DecorMesh(DecorSpec s){this.s=s;build();
        if((s.kind()==DecorKind.INTERCOM&&s.variant()==1)||(s.kind()==DecorKind.FIRE_CABINET&&s.variant()==2)||(s.kind()==DecorKind.VENT&&s.variant()==3)){
            for(int i=0;i<solids.size();i++){var q=solids.get(i);solids.set(i,new Solid(q.x(),q.y(),q.z()*.35-s.depth()*.25,q.X(),q.Y(),q.Z()*.35-s.depth()*.25));}
            var original=new ArrayList<>(faces);faces.clear();minX=minY=minZ=Float.POSITIVE_INFINITY;maxX=maxY=maxZ=Float.NEGATIVE_INFINITY;
            for(var f:original)face(inset(f.a()),inset(f.b()),inset(f.c()),inset(f.d()),f.surface(),f.color(),f.u(),f.v(),f.boxUv());
        }
        if(!s.label().isBlank() && (s.kind()==DecorKind.CABINET||s.kind()==DecorKind.HELP_POINT||s.kind()==DecorKind.INTERCOM)) face(p(-s.width()*.40,s.height()*.82,s.depth()/2+.015),p(-s.width()*.40,s.height()*.95,s.depth()/2+.015),p(s.width()*.40,s.height()*.95,s.depth()/2+.015),p(s.width()*.40,s.height()*.82,s.depth()/2+.015),"label",0xFFFFFF,1,1);}
    private Point inset(Point p){return new Point(p.x(),p.y(),p.z()*.35f-s.depth()*.25f);}
    private Point p(double x,double y,double z){return new Point((float)x,(float)y,(float)z);}
    private void face(Point a,Point b,Point c,Point d,String material,int color,float u,float v){face(a,b,c,d,material,color,u,v,false);}
    private void boxFace(Point a,Point b,Point c,Point d,String material,int color,float u,float v){face(a,b,c,d,material,color,u,v,true);}
    private void face(Point a,Point b,Point c,Point d,String material,int color,float u,float v,boolean boxUv){
        if(faces.size()>=14000)throw new IllegalStateException("Decoration mesh budget exceeded");
        for(Point p:List.of(a,b,c,d)){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);}
        faces.add(new Face(a,b,c,d,material,0xFF000000|color,u,v,boxUv));
    }
    private String body(){if(s.kind().materials.split("\\|")[s.material()].equals("Carrelage"))return "tile";return s.kind().materials.split("\\|")[s.material()].equals("Bois")?"wood":s.kind().materials.split("\\|")[s.material()].equals("Métal")?"metal":s.kind().materials.split("\\|")[s.material()].equals("Pierre")?"stone":"concrete";}
    private void box(double x,double y,double z,double X,double Y,double Z,String mat,int color){
        if(X<=x||Y<=y||Z<=z)return;solid(x,y,z,X,Y,Z);
        float u=(float)(X-x),v=(float)(Y-y),d=(float)(Z-z);if(mat.equals("fire")||mat.equals("hazard")){u=v=d=1;}
        boxFace(p(x,y,z),p(x,Y,z),p(X,Y,z),p(X,y,z),mat,color,u,v);
        boxFace(p(X,y,Z),p(X,Y,Z),p(x,Y,Z),p(x,y,Z),mat,color,u,v);
        boxFace(p(x,y,Z),p(x,Y,Z),p(x,Y,z),p(x,y,z),mat,color,d,v);
        boxFace(p(X,y,z),p(X,Y,z),p(X,Y,Z),p(X,y,Z),mat,color,d,v);
        boxFace(p(x,Y,z),p(x,Y,Z),p(X,Y,Z),p(X,Y,z),mat,color,u,d);
        boxFace(p(x,y,Z),p(x,y,z),p(X,y,z),p(X,y,Z),mat,color,u,d);
    }
    private void arch(Point a,Point b,Point c,Point d,String mat,int color,float u,float v){face(d,c,b,a,mat,color,u,v);}
    private void b(double x,double y,double z,double X,double Y,double Z){box(x,y,z,X,Y,Z,body(),s.color());}
    private void metal(double x,double y,double z,double X,double Y,double Z){box(x,y,z,X,Y,Z,"metal",0x788086);}
    private void dark(double x,double y,double z,double X,double Y,double Z){box(x,y,z,X,Y,Z,"metal",0x252D32);}
    private void tube(double ax,double ay,double az,double bx,double by,double bz,double r,String mat,int color){
        double dx=bx-ax,dy=by-ay,dz=bz-az,len=Math.sqrt(dx*dx+dy*dy+dz*dz);if(len<1e-6)return;int pieces=Math.abs(dx)<1e-6&&Math.abs(dz)<1e-6||Math.abs(dy)<1e-6&&(Math.abs(dx)<1e-6||Math.abs(dz)<1e-6)?1:Math.min(64,Math.max(1,(int)Math.ceil(len/.2)));for(int k=0;k<pieces;k++){double t=(double)k/pieces,T=(double)(k+1)/pieces;solid(Math.min(ax+dx*t,ax+dx*T)-r,Math.min(ay+dy*t,ay+dy*T)-r,Math.min(az+dz*t,az+dz*T)-r,Math.max(ax+dx*t,ax+dx*T)+r,Math.max(ay+dy*t,ay+dy*T)+r,Math.max(az+dz*t,az+dz*T)+r);}
        dx/=len;dy/=len;dz/=len;
        double ux=-dz,uy=0,uz=dx;double ul=Math.sqrt(ux*ux+uz*uz);if(ul<.01){ux=1;uz=0;ul=1;}ux/=ul;uz/=ul;
        double vx=dy*uz,vy=dz*ux-dx*uz,vz=-dy*ux;
        Point[] a=new Point[12],b=new Point[12];for(int i=0;i<12;i++){double angle=i*Math.PI/6,c=Math.cos(angle)*r,t=Math.sin(angle)*r;
            a[i]=p(ax+ux*c+vx*t,ay+uy*c+vy*t,az+uz*c+vz*t);b[i]=p(bx+ux*c+vx*t,by+uy*c+vy*t,bz+uz*c+vz*t);}
        for(int i=0;i<12;i++){int j=(i+1)%12;face(a[i],a[j],b[j],b[i],mat,color,.3f,(float)len);face(p(ax,ay,az),a[j],a[i],p(ax,ay,az),mat,color,1,1);face(p(bx,by,bz),b[i],b[j],p(bx,by,bz),mat,color,1,1);}
    }
    private void rail(double x,double y,double z,double X,double Y,double Z,double r){tube(x,y,z,X,Y,Z,r,"metal",0x9BA4A8);}
    private void frame(double w,double h,double z,double t,double d,int color){
        box(-w/2,0,z,w/2,t,z+d,"metal",color);box(-w/2,h-t,z,w/2,h,z+d,"metal",color);
        box(-w/2,t,z,-w/2+t,h-t,z+d,"metal",color);box(w/2-t,t,z,w/2,h-t,z+d,"metal",color);
    }
    private void panel(double w,double h,double z,String surface,int color){face(p(-w/2,0,z),p(-w/2,h,z),p(w/2,h,z),p(w/2,0,z),surface,color,1,1);}
    private int count(double length,double spacing,int max){return Math.max(1,Math.min(max,(int)Math.ceil(length/spacing)));}
    private void build(){
        double w=s.width(),h=s.height(),d=s.depth(),a=-w/2,c=w/2,z=-d/2,Z=d/2;int v=s.variant();double step=s.spacing();
        switch(s.kind()) {
            case VAULT -> {
                // Elliptic shell, 40 smooth segments; independent span/rise/length.
                double rise=h*(v==1?.67:v==2?.82:1),t=.12;
                for(int i=0;i<40;i++){double t0=i*Math.PI/40,t1=(i+1)*Math.PI/40;
                    double x0=Math.cos(t0)*w/2,y0=Math.sin(t0)*rise,x1=Math.cos(t1)*w/2,y1=Math.sin(t1)*rise;
                    double X0=Math.cos(t0)*(w/2+t),Y0=Math.sin(t0)*(rise+t),X1=Math.cos(t1)*(w/2+t),Y1=Math.sin(t1)*(rise+t);
                    solid(Math.min(Math.min(x0,x1),Math.min(X0,X1)),Math.min(y0,y1),z,Math.max(Math.max(x0,x1),Math.max(X0,X1)),Math.max(Y0,Y1),Z);
                    arch(p(x0,y0,z),p(x1,y1,z),p(x1,y1,Z),p(x0,y0,Z),v==3?"concrete":body(),s.color(),(float)(w*Math.PI/80),(float)d);
                    arch(p(X0,Y0,Z),p(X1,Y1,Z),p(X1,Y1,z),p(X0,Y0,z),body(),s.color(),(float)(w*Math.PI/80),(float)d);
                    arch(p(x0,y0,z),p(X0,Y0,z),p(X1,Y1,z),p(x1,y1,z),body(),s.color(),.2f,.2f);
                    arch(p(x1,y1,Z),p(X1,Y1,Z),p(X0,Y0,Z),p(x0,y0,Z),body(),s.color(),.2f,.2f);
                }
                if(v==3){int n=count(d,.65,16);for(int j=0;j<=n;j++)for(int i=0;i<24;i++){double q=i*Math.PI/24,Q=(i+1)*Math.PI/24;rail(Math.cos(q)*(w/2-.025),Math.sin(q)*(rise-.025),z+d*j/n,Math.cos(Q)*(w/2-.025),Math.sin(Q)*(rise-.025),z+d*j/n,.025);}}
            }
            case COLUMN -> {
                if(v==1){for(int strip=0;strip<24;strip++){double xx=a+w*strip/24,XX=a+w*(strip+1)/24,nearest=Math.min(Math.abs(xx),Math.abs(XX));if(xx<=0&&XX>=0)nearest=0;double radius=d/2*Math.sqrt(Math.max(0,1-4*nearest*nearest/(w*w)));solid(xx,0,-radius,XX,h,radius);}
                    int n=32;for(int i=0;i<n;i++){double q=i*Math.PI*2/n,Q=(i+1)*Math.PI*2/n;face(p(Math.cos(q)*w/2,h,Math.sin(q)*d/2),p(Math.cos(Q)*w/2,h,Math.sin(Q)*d/2),p(Math.cos(Q)*w/2,0,Math.sin(Q)*d/2),p(Math.cos(q)*w/2,0,Math.sin(q)*d/2),body(),s.color(),(float)(w*Math.PI/n),(float)h);}
                    for(double yy:new double[]{0,h})for(int i=0;i<n;i++){int A=yy==0?i:i+1,B=yy==0?i+1:i;face(p(0,yy,0),p(Math.cos(A*Math.PI*2/n)*w/2,yy,Math.sin(A*Math.PI*2/n)*d/2),p(Math.cos(B*Math.PI*2/n)*w/2,yy,Math.sin(B*Math.PI*2/n)*d/2),p(0,yy,0),body(),s.color(),1,1);}
                }else {b(a,0,z,c,h,Z);if(v==2){int n=count(w,step,80);for(int i=0;i<n;i++){double x=a+w*i/n;b(x,0,z-.035,x+w/n*.38,h,z);b(x,0,Z,x+w/n*.38,h,Z+.035);}}if(v==3)dark(a-.04,0,z-.04,c+.04,.18,Z+.04);}
                metal(a-.01,.02,z-.01,c+.01,.07,Z+.01);
            }
            case RIBBED_WALL -> {
                box(a,0,z,c,h,Z-.04,"concrete",0x555955);boolean horizontal=v==1;int n=count(horizontal?h:w,step,96);
                for(int i=0;i<n;i++){double t=(horizontal?h:w)*i/n,T=(horizontal?h:w)/n*.55;
                    if(horizontal)b(a,t,Z-.04,c,t+T,Z+.025);else b(a+t,0,Z-.04,a+t+T,h*(v==2&&i%2==0?.93:1),Z+.025);}
                if(v==3)frame(w,h,z-.025,.045,d+.05,0x7C8285);
            }
            case ACOUSTIC -> {
                b(a,0,z,c,h,Z);frame(w,h,z-.018,.022,d+.028,0x7B8285);
                if(v==0){face(p(a+.025,.025,Z+.002),p(a+.025,h-.025,Z+.002),p(c-.025,h-.025,Z+.002),p(c-.025,.025,Z+.002),"perforated",s.color(),(float)(w*4),(float)(h*4));}
                if(v==1){int n=count(w,step,70);for(int i=0;i<n;i++)dark(a+(i+.3)*w/n,.08,Z+.002,a+(i+.5)*w/n,h-.08,Z+.004);}
                if(v==2){int n=count(h,step,70);for(int i=0;i<n;i++)dark(a+.06,(i+.3)*h/n,Z+.002,c-.06,(i+.4)*h/n,Z+.004);}
            }
            case BAFFLE -> {
                boolean cross=v==1;int n=count(cross?w:d,step,64);for(int i=0;i<n;i++){double t=(cross?w:d)*i/n,T=(cross?w:d)/n*.32;
                    if(cross)b(a+t,0,z,a+t+T,h,Z);else b(a,v==2&&i%2==0?h*.38:0,z+t,c,h,z+t+T);}
                for(double f:new double[]{-.3,.3}){if(cross)dark(a,h,z+d*(f+.5),c,h+.025,z+d*(f+.5)+.035);else dark(w*f,h,z,w*f+.035,h+.025,Z);}
                if(v==2)for(double xx:new double[]{a,c-.03})metal(xx,-.02,z,xx+.03,h+.04,Z);
                if(v==3){metal(a,-.02,z,c,.015,z+.03);metal(a,-.02,Z-.03,c,.015,Z);}
            }
            case CORNICE -> {
                for(int i=0;i<3;i++)b(a,h*i/3,z+d*i/6,c,h*(i+1)/3,Z);
                if(v==1||v==2){double L=Math.max(.3,w*.4);b(v==1?a:c-d,0,z,v==1?a+d:c,h,Z+L);}
                if(v==3)dark(a,0,z-.01,c,h*.1,Z+.01);
            }
            case LIGHT_STRIP,PENDANT -> {
                b(a,0,z,c,h,Z);box(a+.025,-.006,z+.025,c-.025,.003,Z-.025,"glow",0xFFF2CC);
                dark(a-.01,0,z-.005,a+.025,h,Z+.005);dark(c-.025,0,z-.005,c+.01,h,Z+.005);
                if(s.kind()==DecorKind.PENDANT){for(double xx:new double[]{a*.65,c*.65})rail(xx,h,0,xx,h+s.suspension(),0,.008);if(v==1)box(a,0,Z+.1,c,h,Z+.1+d,"glow",0xFFF2CC);if(v==3){for(double zz:new double[]{z-d*2,Z+d*2}){b(a,0,zz,c,h,zz+d);box(a+.02,-.006,zz+.025,c-.02,.003,zz+d-.025,"glow",0xFFF2CC);}for(double xx:new double[]{a,c-.06})b(xx,0,z-d*2,xx+.06,h,Z+d*3);}
                    if(v==2){int n=count(w,.14,70);for(int i=0;i<n;i++)metal(a+i*w/n,-.02,z,a+i*w/n+.012,.015,Z);}}
                else if(v==1){b(c-d,0,Z,c,h,Z+Math.min(w,2));box(c-d+.025,-.006,Z,c-.025,.003,Z+Math.min(w,2)-.025,"glow",0xFFF2CC);}
                else if(v==2)dark(c-.06,-.015,z-.015,c+.025,h+.015,Z+.015);
                else if(v==3)metal(a,h,z,c,h+.08,z+.035);
            }
            case SCONCE -> {
                dark(a,0,z,c,h,z+.025);b(a+.02,.03,z+.025,c-.02,h-.03,Z);
                box(a+.035,.08,Z,c-.035,h-.08,Z+.005,"glow",0xFFF2CC);
                if(v==2)box(a-.05,.08,z-.005,a-.02,h-.08,z+.025,"glow",0xFFF2CC);
                if(v==3)metal(a-.025,h*.72,z,c+.025,h*.75,Z+.07);
                if(v==1)metal(a,h*.48,Z+.007,c,h*.52,Z+.025);
            }
            case BENCH -> {
                double seat=Math.min(h*.57,.5),dd=Math.max(d,.38);int slats=count(dd,.085,16);
                for(int i=0;i<slats;i++){double zz=-dd/2+i*dd/slats;b(a,seat,zz,c,seat+.045,zz+dd/slats*.76);}
                int legs=count(w,1.6,12);for(int i=0;i<=legs;i++){double xx=a+.16+(w-.32)*i/legs;metal(xx-.035,.04,-dd*.30,xx+.035,seat,dd*.30);metal(xx-.13,.015,-dd*.32,xx+.13,.045,dd*.32);}
                dark(a+.08,seat-.055,-dd*.28,c-.08,seat,dd*.28);
                if(v!=1){for(double xx:new double[]{a+.16,c-.16})rail(xx,seat,-dd*.32,xx,h-.03,-dd*.46,.018);
                    for(int i=0;i<3;i++)b(a,seat+.13+i*Math.max(.02,(h-seat-.16)/3),-dd*.47,c,seat+.19+i*Math.max(.02,(h-seat-.16)/3),-dd*.47+.04);}
                if(v==2)for(int i=0;i<3;i++)b(a,seat+.13+i*.07,dd*.45,c,seat+.18+i*.07,dd*.45+.04);
                if(v==3){int n=count(w,.65,24);for(int i=0;i<=n;i++){double xx=a+.10+(w-.20)*i/n;rail(xx,seat,0,xx,seat+.20,0,.015);rail(xx,seat+.20,-dd*.25,xx,seat+.20,dd*.3,.02);}}
            }
            case LEANING -> {
                for(double xx:new double[]{a+.12,c-.12}){rail(xx,.03,0,xx,h-.08,0,.025);metal(xx-.09,0,-.09,xx+.09,.03,.09);}
                if(v==1)tube(a,h-.1,0,c,h-.1,0,Math.min(.12,d*.32),body(),s.color());else b(a,h-.24,z,c,h,Z);
                if(v==2)b(a,h-.24,-Z-.14,c,h,-Z-.04);if(v==3)metal(a+.12,.12,-.02,c-.12,.16,.02);
            }
            case BIN -> {
                int n=v==0?3:v==1?2:1;dark(a+.02,0,z+.02,c-.02,.08,Z-.02);
                for(int i=0;i<n;i++){double l=a+i*w/n+.012,r=a+(i+1)*w/n-.012;
                    if(v==3){
                        for(double xx:new double[]{l+.02,r-.02}){rail(xx,.035,0,xx,h-.10,0,.018);metal(xx-.055,.012,-d*.35,xx+.055,.035,d*.35);}
                        metal(l,h*.75,z,r,h*.82,z+.025);metal(l,h*.75,Z-.025,r,h*.82,Z);metal(l,h*.75,z,l+.025,h*.82,Z);metal(r-.025,h*.75,z,r,h*.82,Z);
                        Point[] top={p(l+.03,h*.75,z+.03),p(r-.03,h*.75,z+.03),p(r-.03,h*.75,Z-.03),p(l+.03,h*.75,Z-.03)};
                        Point[] bottom={p(l+(r-l)*.16,.10,z+d*.22),p(r-(r-l)*.16,.10,z+d*.22),p(r-(r-l)*.16,.10,Z-d*.22),p(l+(r-l)*.16,.10,Z-d*.22)};
                        for(int q=0;q<4;q++){int Q=(q+1)%4;face(bottom[Q],bottom[q],top[q],top[Q],"glass",0xC6DBD8,1,1);}
                    }else b(l,.08,z,r,h,Z);
                    box(l+.025,h*.76,Z+.002,r-.025,h*.94,Z+.006,"metal",new int[]{0xD8B834,0x477FAC,0x717C72}[i%3]);
                    dark(l+(r-l)*.15,h*.80,Z+.007,r-(r-l)*.15,h*.90,Z+.010);
                    metal(l+.04,h*.18,Z+.004,r-.04,h*.2,Z+.008);
                }
            }
            case HELP_POINT,INTERCOM -> {
                boolean help=s.kind()==DecorKind.HELP_POINT;double low=help&&v!=1?h*.53:0;
                if(low>0){b(a*.60,0,z*.55,c*.60,low,Z*.55);metal(a-.06,0,z-.06,c+.06,.035,Z+.06);}
                b(a,low,z,c,h,Z);box(a+.016,low+.02,Z,c-.016,h-.02,Z+.007,"metal",0xF0F1ED);
                double ch=h-low;box(a+.025,h-ch*.20,Z+.008,c-.025,h-.02,Z+.012,"accent",s.accent());
                for(int i=0;i<5;i++)dark(a+w*.22,low+ch*.48+i*ch*.04,Z+.009,c-w*.22,low+ch*.49+i*ch*.04,Z+.014);
                tube(0,low+ch*.26,Z+.01,0,low+ch*.26,Z+.03,w*.095,"accent",s.accent());
                if(v==2||v==3)tube(w*.22,low+ch*.26,Z+.01,w*.22,low+ch*.26,Z+.03,w*.065,"metal",0xB63D3B);
                if(v==3)metal(a-.03,h,z,c+.03,h+.04,Z+.08);
            }
            case FIRE_CABINET -> {
                b(a,0,z,c,h,Z);frame(w,h,Z,.035,.025,0xB52B32);
                if(v!=1){box(a+.04,.045,Z+.002,c-.04,h-.045,Z+.004,"glass",0xA8C2C8);
                    if(v!=3)tube(0,.14,Z+.012,0,h*.63,Z+.012,Math.min(w*.20,.10),"metal",0xD63832);
                    metal(-w*.14,h*.66,Z+.005,w*.14,h*.70,Z+.035);rail(w*.16,h*.65,Z+.04,w*.17,h*.27,Z+.04,.012);
                }
                box(a+.04,h*.80,Z+.028,c-.04,h*.96,Z+.03,"fire",0xFFFFFF);metal(c-.08,h*.42,Z+.03,c-.05,h*.55,Z+.05);
                if(v==3){metal(-.012,.04,Z+.03,.012,h-.04,Z+.04);for(double xx:new double[]{-w*.24,w*.24})tube(xx,.14,Z+.016,xx,h*.63,Z+.016,Math.min(w*.15,.07),"metal",0xD63832);}
            }
            case CABINET -> {
                b(a,0,z,c,h,Z);dark(a+.03,0,z+.03,c-.03,.08,Z-.03);
                int doors=v==1?1:2;for(int i=0;i<doors;i++){double l=a+i*w/doors+.018,r=a+(i+1)*w/doors-.018;
                    box(l,.09,Z+.002,r,h-.018,Z+.012,body(),s.color());metal(r-.06,h*.47,Z+.015,r-.04,h*.55,Z+.035);
                    for(int j=0;j<(v==2?12:4);j++)dark(l+.04,h*.12+j*.025,Z+.013,r-.04,h*.12+j*.025+.008,Z+.016);}
                box(a+.07,h*.78,Z+.014,a+.18,h*.84,Z+.018,"hazard",0xFFFFFF);if(v==3)for(double xx:new double[]{a+.06,c-.10})metal(xx,h*.2,z-.04,xx+.04,h*.8,z);
            }
            case VENT -> {
                dark(a,0,z,c,h,Z);frame(w,h,Z,.035,.025,s.color());int n=count(v==1?w:h,Math.max(.045,step*.4),80);
                for(int i=0;i<n;i++){if(v==1)b(a+(i+.2)*w/n,.04,Z+.001,a+(i+.7)*w/n,h-.04,Z+.025);else b(a+.04,(i+.2)*h/n,Z+.001,c-.04,(i+.65)*h/n,Z+.025);}
                if(v==2){int m=count(w,.12,80);for(int i=0;i<m;i++)metal(a+(i+.4)*w/m,.035,Z+.028,a+(i+.5)*w/m,h-.035,Z+.04);}
            }
            case HANDRAIL,BALUSTRADE -> {
                boolean guard=s.kind()==DecorKind.BALUSTRADE;double rise=guard?0:v==1?h*.65:v==2?-h*.65:0;double end=h+rise;
                tube(a,h,0,c,end,0,Math.min(.08,Math.max(.012,d/2)),body(),s.color());int n=count(w,1.25,16);
                for(int i=0;i<=n;i++){double xx=a+w*i/n,yy=h+rise*i/n;
                    if(guard){rail(xx,.025,0,xx,yy-.025,0,.022);metal(xx-.07,0,-.07,xx+.07,.025,.07);}
                    else {rail(xx,yy,0,xx,yy-.12,.09,.014);metal(xx-.035,yy-.17,.08,xx+.035,yy-.1,.10);}
                }
                if(guard){for(int i=0;i<n;i++){double l=a+i*w/n+.03,r=a+(i+1)*w/n-.03;
                    if(v==0)box(l,.13,-.008,r,h-.075,.008,"glass",0xA5C5CB);
                    if(v==1){int m=count(r-l,.13,20);for(int j=0;j<m;j++)rail(l+(j+.5)*(r-l)/m,.12,0,l+(j+.5)*(r-l)/m,h-.08,0,.009);}
                    if(v==2)for(int j=1;j<6;j++)rail(l,h*j/6,0,r,h*j/6,0,.004);
                    if(v==3)b(l,.12,-.015,r,h-.08,.015);
                }}else if(v==3)rail(c,h,0,c,h,Math.min(w,2),.028);
            }
            case WAYPOINT -> {
                b(a,0,z,c,.035,Z);double yy=.06+v*.04;b(-w*.12,.035,-d*.12,w*.12,yy,d*.12);box(-w*.35,yy,-d*.35,w*.35,yy+.02,d*.35,"accent",s.accent());
            }
            case JOURNEY_KIOSK -> {
                double low=v==1?0:h*.32,top=v==3?h*.72:h;
                if(v!=1){b(a*.55,0,z*.6,c*.55,low,Z*.6);metal(a-.08,0,z-.08,c+.08,.04,Z+.08);}
                b(a,low,z,c,top,Z);box(a+.045,low+.08,Z+.002,c-.045,top-.08,Z+.012,"accent",0x142C42);
                box(a+.08,low+(top-low)*.48,Z+.013,c-.08,top-.13,Z+.016,"glow",0x61D4C4);
                box(a+.08,low+.12,Z+.013,c-.08,low+(top-low)*.38,Z+.016,"metal",0xCBD9DF);
                if(v==2){box(a+.045,low+.08,z-.012,c-.045,top-.08,z-.002,"accent",0x142C42);}
                if(v==3)metal(a,top,z,c,top+.035,Z+.08);
            }
            case POSTER -> {
                double low=v==2?h*.30:0;frame(w,h,Z,.028,.018,s.color());b(a,low,z,c,h,Z);
                face(p(a+.04,low+.04,Z+.021),p(a+.04,h-.04,Z+.021),p(c-.04,h-.04,Z+.021),p(c-.04,low+.04,Z+.021),"poster",0xFFFFFF,1,1);
                if(v==1)box(a+.025,h-.028,Z+.025,c-.025,h-.014,Z+.027,"glow",0xFFF2CC);
                if(v==2){metal(-.035,0,0,.035,low,.035);metal(-w*.3,0,-.18,w*.3,.025,.18);}
                if(v==3)face(p(c-.04,.04,z-.002),p(c-.04,h-.04,z-.002),p(a+.04,h-.04,z-.002),p(a+.04,.04,z-.002),"poster",0xFFFFFF,1,1);
            }
        }
    }
}
