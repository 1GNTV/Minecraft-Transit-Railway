package org.mtr.mod.data;

import java.util.Objects;

/** Server-sanitized screen geometry. World units are blocks; angles are degrees. */
public final class PanamScreenSettings {
    public static final PanamScreenSettings DEFAULT = new PanamScreenSettings(0,0,0,0,0,0,1.95f,.821875f,1024,512,1);
    public final float x,y,z,yaw,pitch,roll,width,height,zoom;
    public final int resolutionWidth,resolutionHeight;
    public PanamScreenSettings(float x,float y,float z,float yaw,float pitch,float roll,float width,float height,int resolutionWidth,int resolutionHeight,float zoom) {
        this.x=clamp(x,-16,16,0);this.y=clamp(y,-16,16,0);this.z=clamp(z,-16,16,0);
        this.yaw=angle(yaw);this.pitch=angle(pitch);this.roll=angle(roll);
        this.width=clamp(width,.25f,16,1.95f);this.height=clamp(height,.25f,16,.821875f);
        this.resolutionWidth=Math.max(128,Math.min(2048,resolutionWidth));
        this.resolutionHeight=Math.max(128,Math.min(2048,resolutionHeight));
        this.zoom=clamp(zoom,.5f,3,1);
    }
    private static float clamp(float v,float min,float max,float fallback){return Float.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
    private static float angle(float value){return Float.isFinite(value)?(value%360+540)%360-180:0;}
    @Override public boolean equals(Object o){
        if(!(o instanceof PanamScreenSettings))return false;
        PanamScreenSettings s=(PanamScreenSettings)o;
        return x==s.x&&y==s.y&&z==s.z&&yaw==s.yaw&&pitch==s.pitch&&roll==s.roll&&width==s.width&&height==s.height&&zoom==s.zoom&&resolutionWidth==s.resolutionWidth&&resolutionHeight==s.resolutionHeight;
    }
    @Override public int hashCode(){return Objects.hash(x,y,z,yaw,pitch,roll,width,height,zoom,resolutionWidth,resolutionHeight);}
}
