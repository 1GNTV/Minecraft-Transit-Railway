package org.mtr.mod.screen;

/** Shared responsive geometry, also exercised at 320x240, 480x270, 640x360 and 960x540. */
public record SillonSignageEditorLayout(int width,int height,Rect editor,Rect preview,int pagerY,int footerY) {
    public record Rect(int x,int y,int w,int h) {
        public int right(){return x+w;}public int bottom(){return y+h;}
        public boolean intersects(Rect r){return x<r.right()&&right()>r.x&&y<r.bottom()&&bottom()>r.y;}
    }
    public static SillonSignageEditorLayout of(int w,int h) {
        int footer=h-28,pager=footer-26;
        if(w>=720) {
            int ew=Math.min(480,(w-40)/2);
            return new SillonSignageEditorLayout(w,h,new Rect(12,78,ew,pager-78),
                new Rect(ew+28,78,w-ew-40,pager-78),pager,footer);
        }
        int ph=Math.max(30,Math.min(72,(h-180)/2)),top=68;
        return new SillonSignageEditorLayout(w,h,new Rect(12,top+ph+10,w-24,pager-top-ph-10),
            new Rect(12,top,w-24,ph),pager,footer);
    }
    public int formRows(int toolbar) {return Math.max(1,(editor.h-toolbar)/38);}
}
