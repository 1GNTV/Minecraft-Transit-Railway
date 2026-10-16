package org.mtr.mod.render;

import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera-independent classic track geometry. Geometry is sampled once per rail
 * revision and grouped into 32-block spatial sectors. Visible pieces still use
 * the live light sampler and the original vertex-consumer renderer.
 * Render-thread only. Ghost/editor rails intentionally bypass this cache.
 */
public final class ClassicRailSceneCache {
    private static final int SECTOR_SHIFT = 5;
    private static final int MAX_SCENES = 512;
    private static final int MAX_PIECES = 65536;
    private static final int MAX_TRACK_PIECES = 8192;
    private static final Map<Key, Scene> SCENES = new LinkedHashMap<>(64, .75f, true);
    private static int storedPieces;
    private static boolean failed;

    private static final class Key {
        final RailMath math;
        final int width;
        Key(RailMath math, float width) { this.math = math; this.width = Float.floatToIntBits(width); }
        @Override public int hashCode() { return 31 * System.identityHashCode(math) + width; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && math == key.math && width == key.width;
        }
    }
    private static final class Piece {
        final double[] p;
        final BlockPos lightPos;
        Piece(double[] p) {
            this.p = p;
            lightPos = Init.newBlockPos(p[0], p[8] + .1, p[1]);
        }
    }
    private static final class Sector {
        final int x, z;
        final List<Piece> pieces = new ArrayList<>();
        Sector(int x, int z) { this.x = x; this.z = z; }
    }
    private static final class Scene {
        final List<Sector> sectors;
        final int pieceCount;
        Scene(List<Sector> sectors, int pieceCount) {
            this.sectors = sectors; this.pieceCount = pieceCount;
        }
    }
    @FunctionalInterface public interface Visitor {
        void draw(BlockPos lightPos, double x1, double z1, double x2, double z2,
                  double x3, double z3, double x4, double z4, double y1, double y2);
    }
    private ClassicRailSceneCache() {}

    /**
     * A negative result means callers MUST execute their previous visibility
     * renderer. Zero is a successful, empty visibility selection.
     */
    public static int submit(Rail rail, float width, double ex, double ey, double ez,
                             int radius, double nx, double ny, double nz, Visitor visitor) {
        if (!SillonMegaEngine.CLASSIC || failed || !Double.isFinite(ex + ey + ez + nx + ny + nz))
            return -1;
        try {
            Key key = new Key(rail.railMath, width);
            Scene scene = SCENES.get(key);
            if (scene == null) {
                Map<Long, Sector> sectors = new LinkedHashMap<>();
                final int[] count = {0};
                MinecraftClientData.getInstance().railGeometryCache.render(rail.railMath,
                    (x1,z1,x2,z2,x3,z3,x4,z4,y1,y2) -> {
                        if (count[0] >= MAX_TRACK_PIECES) { count[0]++; return; }
                        int sx = (int)Math.floor(x1 / (1 << SECTOR_SHIFT));
                        int sz = (int)Math.floor(z1 / (1 << SECTOR_SHIFT));
                        long sectorKey = (((long)sx) << 32) | (sz & 0xffffffffL);
                        Sector sector = sectors.computeIfAbsent(sectorKey, value -> new Sector(sx, sz));
                        sector.pieces.add(new Piece(new double[]{
                            x1,z1,x2,z2,x3,z3,x4,z4,y1,y2
                        }));
                        count[0]++;
                    }, .5, -width, width);
                if (count[0] > MAX_TRACK_PIECES) return -1;
                scene = new Scene(List.copyOf(sectors.values()), count[0]);
                SCENES.put(key, scene); storedPieces += count[0];
                var it = SCENES.entrySet().iterator();
                while (SCENES.size() > MAX_SCENES || storedPieces > MAX_PIECES) {
                    var entry = it.next();
                    storedPieces -= entry.getValue().pieceCount;
                    it.remove();
                }
            }
            int selected = 0;
            double radius2 = (double)radius * radius;
            for (Sector sector : scene.sectors) {
                double minX = (double)sector.x * (1 << SECTOR_SHIFT);
                double minZ = (double)sector.z * (1 << SECTOR_SHIFT);
                double nearX = Math.max(minX, Math.min(ex, minX + (1 << SECTOR_SHIFT)));
                double nearZ = Math.max(minZ, Math.min(ez, minZ + (1 << SECTOR_SHIFT)));
                if ((nearX-ex)*(nearX-ex)+(nearZ-ez)*(nearZ-ez) > radius2) continue;
                for (Piece piece : sector.pieces) {
                    double[] p = piece.p, dx = p[0]-ex, dz = p[1]-ez;
                    double squared = dx*dx+dz*dz;
                    if (squared <= radius2 && (squared < 1024 ||
                        dx*nx+(p[8]-ey)*ny+dz*nz > -0.00001)) {
                        visitor.draw(piece.lightPos,p[0],p[1],p[2],p[3],p[4],p[5],
                                     p[6],p[7],p[8],p[9]);
                        selected++;
                    }
                }
            }
            return selected;
        } catch (RuntimeException error) {
            failed = true;
            Init.LOGGER.error("Sillon classic rail scene cache disabled; legacy path restored", error);
            return -1;
        }
    }
    public static void clear() { SCENES.clear(); storedPieces = 0; failed = false; }
}
