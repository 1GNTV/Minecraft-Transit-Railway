package org.mtr.mod.render.pixel;

/** Sprite-only reflow; glyphs retain their aspect ratio at every screen size. */
public final class PanamAdaptiveFrame {
    private PanamAdaptiveFrame(){}
    public static void draw(PanamSurface s,PanamFrame.Timeline t,long now,int hour,int minute,PanamFrame.Emitter out){
        PanamLayout l=s.layout;
        out.sprite(s.base,0,0,l.width,l.height,1,0,0,l.width,l.height);
        float clockScale=Math.min(1,Math.min(l.width/400f,l.height/260f));
        if(!l.compact){
            float cw=0;int[] chars={hour/10,hour%10,10,minute/10,minute%10};
            for(int c:chars)cw+=s.clockAdvance[c]*clockScale;
            float x=l.width-l.pad-cw,y=l.pad;
            for(int c:chars){PanamSurface.Sprite p=s.clock[c];
                out.sprite(p,x+(p.originX-4)*clockScale,y+p.originY*clockScale,p.w*clockScale,p.h*clockScale,c==10?PanamAnimation.colon(now):1,0,0,l.width,l.height);
                x+=s.clockAdvance[c]*clockScale;
            }
        }
        if(s.spec.suspended)return;
        int count=Math.min(s.spec.mixed?3:2,Math.min(t.current.length,s.spec.count));
        if(l.compact)count=Math.min(count,1);
        float top=l.bodyY+l.pad+(l.header==0&&!l.compact?48*clockScale:0);
        float available=Math.max(1,l.departH-(top-l.bodyY)-l.pad);
        float transition=PanamAnimation.departure(now-t.changedAt);
        for(int i=0;i<count;i++){
            boolean rows=s.spec.mixed;
            float cellW=rows?l.departW:l.departW/count,cellH=rows?available/count:available;
            float x=rows?0:i*cellW,y=rows?top+i*cellH:top;
            float nx=x+cellW*.5f,ny=y+cellH*.22f,scale=Math.min(cellW/280f,cellH/245f);
            if(rows&&i<s.rows.length){
                // White destination rows and independent ETA cells, fitted uniformly.
                PanamSurface.Sprite row=s.rows[i];float rowScale=Math.min((l.departW-2*l.pad)/496,cellH/84);
                out.sprite(row,l.pad,y,row.w*rowScale,row.h*rowScale,1,l.pad,l.bodyY,l.departW-2*l.pad,l.departH);
                nx=l.pad+450*rowScale;ny=y+6*rowScale;scale=.26f*rowScale;
            }
            PanamFrame.Departure train=t.current[i];
            float alpha=transition;
            for(PanamFrame.Departure old:t.previous)if(old.id.equals(train.id)){alpha=1;break;}
            PanamFrame.waiting(s,t.minutes[i],nx,ny,scale*PanamAnimation.zoom(now-t.zoomAt[i]),alpha,now,train.at,out,x,y,cellW,cellH);
        }
        if(l.compact||s.notices.length==0)return;
        int index=Math.floorMod(t.noticeIndex,s.notices.length);
        float iconScale=Math.min(l.iconHeight()/59,l.newsTextWidth()/154);
        PanamSurface.Sprite icon=s.noticeIcons[index],notice=s.notices[index];
        float progress=PanamAnimation.noticeText(now-t.noticeAt);
        out.sprite(icon,l.newsX+l.pad,l.newsY+l.pad,154*iconScale,59*iconScale,1,l.newsX,l.newsY,l.newsW,l.newsH);
        out.sprite(notice,l.newsX+l.pad+(1-progress)*Math.min(40,l.newsW*.1f),l.newsY+l.pad+l.iconHeight(),notice.w,notice.h,.5f+.5f*progress,l.newsX+l.pad,l.newsY+l.pad+l.iconHeight(),l.newsTextWidth(),l.newsTextHeight());
    }
}
