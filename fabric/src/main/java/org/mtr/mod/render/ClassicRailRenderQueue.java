package org.mtr.mod.render;

import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.client.IDrawing;
import org.mtr.mapping.render.tool.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-render-pass opaque classic rail material dispatch. The original two
 * double-sided textured quads and live light values are left intact. Across
 * separate tracks, compatible rail pages share one queued callback per material
 * rather than allocating one callback for each 256-quad page.
 *
 * The experimental persistent GPU cache takes precedence when requested.
 */
public final class ClassicRailRenderQueue {
    private static final int MAX_PIECES_PER_PASS = 131072;
    public static final boolean ENABLED = SillonMegaEngine.CLASSIC &&
        !"false".equalsIgnoreCase(System.getProperty("mtr.sillon.classicDispatch", "true"));
    private static final Map<Key, List<Page>> QUEUED = new LinkedHashMap<>();
    private static int pieces;

    private record Key(Identifier texture, int color, double yOffset,
                       double smallOffset, float u1, float v1, float u2, float v2) {}
    private record Page(double[] coordinates, int[] lights, float[] stripes) {}
    private ClassicRailRenderQueue() {}

    public static void beginPass() { QUEUED.clear(); pieces = 0; }

    public static boolean append(Identifier texture, int color,
                                 double yOffset, double smallOffset,
                                 float u1, float v1, float u2, float v2,
                                 double[] coords, int[] lights, float[] stripes) {
        if (!ENABLED || (ClassicRailGpuCache.ENABLED && Utilities.canUseCustomShader())
            || lights.length == 0 ||
            pieces + lights.length > MAX_PIECES_PER_PASS) return false;
        Key key = new Key(texture,color,yOffset,smallOffset,u1,v1,u2,v2);
        QUEUED.computeIfAbsent(key, unused -> new ArrayList<>())
              .add(new Page(coords,lights,stripes));
        pieces += lights.length;
        return true;
    }

    public static void flush() {
        for (var entry : QUEUED.entrySet()) {
            Key key = entry.getKey();
            List<Page> pages = List.copyOf(entry.getValue());
            final float uu1 = key.u1 < 0 ? 0 : key.u1;
            final float uu2 = key.u2 < 0 ? 1 : key.u2;
            MainRenderer.scheduleRender(key.texture, false, QueuedRenderLayer.EXTERIOR,
                (graphicsHolder,offset) -> {
                    for (Page page : pages) {
                        for (int i=0; i<page.lights.length; i++) {
                            int j = i * 10;
                            double[] p = page.coordinates;
                            double x1=p[j],z1=p[j+1],x2=p[j+2],z2=p[j+3];
                            double x3=p[j+4],z3=p[j+5],x4=p[j+6],z4=p[j+7];
                            double y1=p[j+8]+key.yOffset,y2=p[j+9]+key.yOffset;
                            float vv1=key.v1<0 ? .1875F+page.stripes[i] : key.v1;
                            float vv2=key.v2<0 ? .3125F+page.stripes[i] : key.v2;
                            IDrawing.drawTexture(graphicsHolder,x1,y1,z1,
                                x2,y1+key.smallOffset,z2,x3,y2,z3,x4,y2+key.smallOffset,z4,
                                offset,uu1,vv1,uu2,vv2,Direction.UP,key.color,page.lights[i]);
                            IDrawing.drawTexture(graphicsHolder,x2,y1+key.smallOffset,z2,
                                x1,y1,z1,x4,y2+key.smallOffset,z4,x3,y2,z3,
                                offset,uu1,vv1,uu2,vv2,Direction.UP,key.color,page.lights[i]);
                        }
                    }
                });
        }
        QUEUED.clear();
        pieces = 0;
    }
}
