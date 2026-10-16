package org.mtr.mod.render;
/** JVM rollback switches for the three experimental Sillon performance upgrades. */
public final class SillonMegaEngine {
    private SillonMegaEngine(){}
    private static boolean on(String key){return !"false".equalsIgnoreCase(System.getProperty(key,"true"));}
    public static final boolean ENABLED=on("mtr.mega");
    public static final boolean SCENE=ENABLED&&on("mtr.mega.scene");
    public static final boolean RAILS=ENABLED&&on("mtr.mega.rails");
    public static final boolean INSTANCES=ENABLED&&on("mtr.mega.instances");
    public static final boolean SHADER_REORDER=INSTANCES&&on("mtr.mega.shaderReorder");
    public static final int WINDOW=INSTANCES?Math.max(8,Math.min(64,Integer.getInteger("mtr.mega.instanceWindow",32))):24;
    public static final int GROUP_MIN=INSTANCES?Math.max(2,Math.min(4,Integer.getInteger("mtr.mega.instanceMin",3))):4;
    public static final int CAPACITY=INSTANCES?Math.max(8,Math.min(16,Integer.getInteger("mtr.mega.oculusCapacity",12))):8;
    public static final int SCENE_BUILDS=SCENE?Math.max(1,Math.min(4,Integer.getInteger("mtr.mega.sceneBuilds",2))):1;
}
