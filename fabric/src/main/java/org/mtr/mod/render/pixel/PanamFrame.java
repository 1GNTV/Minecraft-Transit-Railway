package org.mtr.mod.render.pixel;

/** Small retained quad plan. The same emitter drives Minecraft and the offline pixel oracle. */
public final class PanamFrame {
    private static final String[] MINUTES=new String[1000];
    static { for(int i=0;i<MINUTES.length;i++)MINUTES[i]=Integer.toString(i); }
    public interface Emitter { void sprite(PanamSurface.Sprite s,float x,float y,float w,float h,float alpha,float clipX,float clipY,float clipW,float clipH); }
    public static final class Departure {
        public final String id, destination;
        public final long at;
        public final boolean realtime;
        public Departure(String id,String destination,long at,boolean realtime){this.id=id;this.destination=destination;this.at=at;this.realtime=realtime;}
    }
    public static final class Timeline {
        public Departure[] current = new Departure[0], previous = new Departure[0];
        public final int[] minutes={0,0,0};
        public final long[] zoomAt={-2000,-2000,-2000};
        public long changedAt=-1500, sampledAt=-5000, noticeAt;
        public int noticeIndex;
        public void update(Departure[] next,long now){
            boolean changed=next.length!=current.length;
            for(int i=0;!changed&&i<next.length;i++)changed=!next[i].id.equals(current[i].id);
            if(changed){previous=current;current=next;changedAt=now;sampledAt=-5000;}
            else current=next;
            if(now-sampledAt>=1000){
                for(int i=0;i<next.length;i++){int value=(int)((next[i].at-now)/60000);if(value!=minutes[i]){minutes[i]=value;zoomAt[i]=now;}}
                sampledAt=now;
            }
        }
        public void notices(int count,long now){
            if(noticeIndex>=count){noticeIndex=0;noticeAt=now;}
            if(count>1&&now-noticeAt>=10000){long steps=(now-noticeAt)/10000;noticeIndex=(int)((noticeIndex+steps)%count);noticeAt+=steps*10000;}
        }
    }
    private PanamFrame(){}
    public static void draw(PanamSurface surface,Timeline state,long now,int hour,int minute,Emitter out){
        PanamSurface.Spec spec=surface.spec; emit(out,surface.base,0,0,1024,466,1,0,0,1024,466);
        float clockWidth=surface.clockAdvance[hour/10]+surface.clockAdvance[hour%10]+surface.clockAdvance[10]+surface.clockAdvance[minute/10]+surface.clockAdvance[minute%10];
        float clockX=944-clockWidth/2;
        for(int i=0;i<5;i++){int c=i==0?hour/10:i==1?hour%10:i==2?10:i==3?minute/10:minute%10;PanamSurface.Sprite s=surface.clock[c];emit(out,s,clockX-4,18,s.w,s.h,c==10?PanamAnimation.colon(now):1,880,0,129,77);clockX+=surface.clockAdvance[c];}
        if(spec.suspended){
            double phase=Math.floorMod(now,5000)/5000.0;
            float a=(float)(phase<=.92?1:1-(phase-.92)/.08);
            float one=(float)Math.max(0,Math.min(1,(phase-.769)/.231)),two=(float)Math.max(0,Math.min(1,(phase-.8383)/.1617));
            emit(out,surface.strikeFirst,770,250,186,186,a,956-186*one,250,186*one,186*one);
            emit(out,surface.strikeSecond,770,250,186,186,a,770,250,186*two,186*two);
            return;
        }
        float transition=PanamAnimation.departure(now-state.changedAt);
        // Draw departed rows/numbers until the leave transition has completed.
        if(transition<1){
            for(int i=0;i<state.previous.length;i++){
                Departure old=state.previous[i];if(find(state.current,old.id)>=0)continue;
                int value=(int)((old.at-now)/60000);
                if(i<surface.leavingRows.length){
                    float y=PanamSurface.HEADER+8+i*84+transition*84;PanamSurface.Sprite row=surface.leavingRows[i];emit(out,row,8,y,496,76,1-transition,0,PanamSurface.HEADER,512,PanamSurface.BODY);
                    waiting(surface,value,458,y+3,.26f,1-transition,now,old.at,out,418,y+4,80,68);
                }else if(!spec.mixed)waiting(surface,value,i*256+128+transition*256,numberY(),1,Math.max(0,1-transition),now,old.at,out,0,HEADER(),512,PanamSurface.BODY);
            }
        }
        for(int i=0;i<Math.min(spec.count,state.current.length);i++){
            Departure train=state.current[i];int old=find(state.previous,train.id);float a=transition>=1||old>=0?1:Math.max(0,transition);
            if(spec.mixed){
                float target=PanamSurface.HEADER+8+i*84,from=old<0?target+84:PanamSurface.HEADER+8+old*84,y=target+(from-target)*(1-transition);
                PanamSurface.Sprite row=surface.rows[i];emit(out,row,8,y,496,76,a,0,PanamSurface.HEADER,512,PanamSurface.BODY);
                waiting(surface,state.minutes[i],458,y+3, .26f*PanamAnimation.zoom(now-state.zoomAt[i]),a,now,train.at,out,418,y+4,80,68);
            }else if(i<2){
                float center=128+i*256,from=old<0?center+256:128+old*256;
                waiting(surface,state.minutes[i],center+(from-center)*(1-transition),numberY(),PanamAnimation.zoom(now-state.zoomAt[i]),a,now,train.at,out,i*256,PanamSurface.HEADER,256,PanamSurface.BODY);
            }
        }
        int index=state.noticeIndex%Math.max(1,surface.notices.length);
        float iconProgress=PanamAnimation.noticeIcon(now-state.noticeAt),textProgress=PanamAnimation.noticeText(now-state.noticeAt);
        emit(out,surface.notices.length>0?surface.noticeIcons[index]:surface.infoIcon,532.48f+(1-iconProgress)*148,PanamSurface.HEADER+20,154,59,1,512,PanamSurface.HEADER+10,156,59);
        if(surface.notices.length>0){PanamSurface.Sprite s=surface.notices[index];emit(out,s,532.48f+(1-textProgress)*40.96f,PanamSurface.HEADER+84,s.w,s.h,.5f+.5f*textProgress,512,PanamSurface.HEADER+69,512,PanamSurface.BODY-70);}
    }
    private static float HEADER(){return PanamSurface.HEADER;}
    private static float numberY(){return PanamSurface.HEADER+(PanamSurface.BODY-235)/2+38;}
    private static int find(Departure[] trains,String id){for(int i=0;i<trains.length;i++)if(trains[i].id.equals(id))return i;return -1;}
    private static void waiting(PanamSurface s,int minutes,float cx,float y,float scale,float alpha,long now,long at,Emitter out,float clipX,float clipY,float clipW,float clipH){
        if(minutes<0 || minutes>99){for(int i=0;i<3;i++)emit(out,s.dot,cx-66*scale+i*55*scale,y+150*scale,37*scale,37*scale,alpha*PanamAnimation.dot(now,i),clipX,clipY,clipW,clipH);return;}
        String value=MINUTES[minutes];float advance=0;for(int i=0;i<value.length();i++)advance+=s.digitAdvance[value.charAt(i)-'0'];
        scale=Math.min(scale, (clipW-10)/Math.max(1,advance));float x=cx-advance*scale/2;
        float pivotY=y+100;float top=scale<.5f?y:pivotY-100*scale;
        for(int i=0;i<value.length();i++){int c=value.charAt(i)-'0';PanamSurface.Sprite glyph=s.digits[c];emit(out,glyph,x-4*scale,top,glyph.w*scale,glyph.h*scale,alpha*(at<now?PanamAnimation.number(now):1),clipX,clipY,clipW,clipH);x+=s.digitAdvance[c]*scale;}
    }
    private static void emit(Emitter out,PanamSurface.Sprite s,float x,float y,float w,float h,float alpha,float cx,float cy,float cw,float ch){out.sprite(s,x+s.originX*w/s.w,y+s.originY*h/s.h,w,h,Math.max(0,Math.min(1,alpha)),cx,cy,cw,ch);}
}
