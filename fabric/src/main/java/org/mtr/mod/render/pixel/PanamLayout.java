package org.mtr.mod.render.pixel;

/** Reflow inspired by arnoclr/panam Screen.vue: 0.4 header / 0.6 columns breakpoints. */
public final class PanamLayout {
    public final int width,height;
    public final float header,footer,bodyY,bodyH,departW,departH,newsX,newsY,newsW,newsH,pad,font;
    public final boolean columns,compact;
    public PanamLayout(PanamViewport v){
        width=v.width;height=v.height;
        columns=height<width*.6f;
        compact=height<90||width<90;
        pad=Math.max(1,Math.min(14,Math.min(width,height)*.025f));
        header=height>=width*.4f&&!compact?Math.min(110,height*.22f):0;
        footer=Math.min(22,height*.045f);
        bodyY=header;bodyH=height-header-footer;
        departW=columns?width*.5f:width;
        departH=columns?bodyH:bodyH*.42f;
        newsX=columns?departW:0;newsY=columns?bodyY:bodyY+departH;
        newsW=width-newsX;newsH=bodyH-(columns?0:departH);
        font=Math.max(8,Math.min(32,Math.min(newsW/10,(newsH-2*pad)/3.5f)));
    }
    public float newsTextWidth(){return Math.max(1,newsW-pad*2);}
    public float newsTextHeight(){return Math.max(1,newsH-pad*2-iconHeight());}
    public float iconHeight(){return Math.min(59,newsH*.22f);}
}
