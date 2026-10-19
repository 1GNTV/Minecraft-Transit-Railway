package org.mtr.mod.client;

import org.mtr.mod.data.SillonSignageSpec;

/** A common physical pivot for both faces; thickness never becomes a one-block gap. */
public final class SillonSignageGeometry {
    private SillonSignageGeometry(){}
    public static boolean pylon(SillonSignageSpec s){return (s.style().equals("vertical")||s.style().equals("totem"))&&(s.mount().equals("wall")||s.mount().equals("standing"));}
    public static float bow(SillonSignageSpec s,float x){return pylon(s)?(float)(s.width()*.13*(1-Math.pow(2*x/s.width(),2))):0;}
    public static float pivotY(SillonSignageSpec s) {
        return switch(s.mount()) {
            case "standing"->s.height()/2+(pylon(s)?0:.8f);
            case "ceiling"->1-s.height()/2;
            case "hanging"->1-s.options().suspension()-s.height()/2;
            default->.5f;
        };
    }
    public static float frontZ(SillonSignageSpec s) {
        return "wall".equals(s.mount())?.48f:-s.options().thickness()/2;
    }
}
