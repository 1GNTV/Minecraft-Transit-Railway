package org.mtr.mod.render;
/** Projection-aware conservative selection; unknown projection/camera proximity keeps full detail. */
public final class ScreenErrorBudget {
    private ScreenErrorBudget() {}
    public static boolean permits(double error,double distanceSquared,double radius,double projectionY,int height,boolean perspective,double pixels){
        if(!Double.isFinite(error+distanceSquared+radius+projectionY+pixels) || error<=0 || distanceSquared<0 || height<=0 || pixels<=0)return false;
        double scale=Math.abs(projectionY)*height*.5;
        if(scale<=0)return false;
        if(perspective){double near=Math.sqrt(distanceSquared)-radius-error;if(near<=0)return false;scale/=near;}
        return error*scale<=pixels;
    }
}
