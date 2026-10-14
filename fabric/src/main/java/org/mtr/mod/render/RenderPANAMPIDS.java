package org.mtr.mod.render;

import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongCollection;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockPANAMPIDS;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.ArrivalsCacheClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.WeakHashMap;

/**
 * PANAM 771 x 356 reference layout, mapped to 2 x 0.924 Minecraft blocks.
 * Reads the same MTR arrivals and platform configuration as the normal PIDS.
 * No JCM runtime dependency, so JCM and MTR France can coexist unchanged.
 */
public final class RenderPANAMPIDS extends BlockEntityRenderer<BlockPANAMPIDS.BlockEntity> {
    private static final Identifier WHITE = new Identifier(Init.MOD_ID, "textures/block/white.png");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final WeakHashMap<BlockPANAMPIDS.BlockEntity, PlatformCache> CACHE = new WeakHashMap<>();
    private static final int WHITE_COLOR = 0xFFFFFFFF, BLACK = 0xFF050505;
    private static final int BLUE = 0xFF0E0184, YELLOW = 0xFFFFBE00;
    private static final float SCREEN_WIDTH = 2.0F;
    private static final float SCREEN_HEIGHT = SCREEN_WIDTH * 356F / 771F;

    private static final class PlatformCache {
        private final long tick;
        private final long revision;
        private final LongArrayList platforms;
        private PlatformCache(long tick, long revision, LongArrayList platforms) {
            this.tick = tick;
            this.revision = revision;
            this.platforms = platforms;
        }
    }

    public RenderPANAMPIDS(Argument dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(BlockPANAMPIDS.BlockEntity entity, float tickDelta, GraphicsHolder holder, int light, int overlay) {
        final World world = entity.getWorld2();
        if (world == null) return;
        final BlockPos pos = entity.getPos2();
        if (!entity.canStoreData.test(world, pos)) return;
        final Direction facing = IBlock.getStatePropertySafe(world, pos, DirectionHelper.FACING);
        final LongCollection platformIds = entity.getPlatformIds().isEmpty() ? autoPlatforms(entity, pos) : entity.getPlatformIds();
        final ObjectArrayList<ArrivalResponse> arrivals = ArrivalsCacheClient.INSTANCE.requestArrivals(platformIds);

        // Use the configured MTR route color, not a hard-coded line palette.
        final ArrivalResponse first = arrivals.isEmpty() ? null : arrivals.get(0);
        final SimplifiedRoute route = first == null ? null : MinecraftClientData.getInstance().simplifiedRouteIdMap.get(first.getRouteId());
        final int color = route == null ? 0xFFE1D000 : 0xFF000000 | (route.getColor() & 0xFFFFFF);
        final String routeNumber = first == null ? "M" : first.getRouteNumber().split("\\|")[0];
        final String direction = first == null ? "En attente" : first.getDestination().split("\\|")[0];
        final long now = System.currentTimeMillis() + ArrivalsCacheClient.INSTANCE.getMillisOffset();
        final String firstTime = arrivals.size() > 0 ? minutes(arrivals.get(0), now) : "--";
        final String secondTime = arrivals.size() > 1 ? minutes(arrivals.get(1), now) : "--";
        final String clock = LocalTime.now().format(CLOCK);

        // A textured rectangle layer for the physical frame and flat panel.
        MainRenderer.scheduleRender(WHITE, false, QueuedRenderLayer.LIGHT, (g, offset) -> {
            transform(g, pos, facing, offset);
            g.createVertexConsumer(MoreRenderLayers.getLight(WHITE, false));
            // Full frame, 2 blocks wide; front panel follows the 771:356 ratio.
            rect(g, -8, -8, 787, 372, 0xFF45494C, light);
            rect(g, 0, 0, 771, 356, BLACK, light);
            rect(g, 0, 0, 771, 100, WHITE_COLOR, light);
            rect(g, 0, 96, 771, 4, color, light);
            rect(g, 0, 332, 771, 24, color, light);
            rect(g, 385, 110, 375, 212, WHITE_COLOR, light);
            rect(g, 385, 110, 375, 54, 0xFFF7F7F7, light);
            rect(g, 195, 176, 4, 102, WHITE_COLOR, light);
            rect(g, 16, 16, 70, 70, color, light);
            rect(g, 410, 117, 38, 38, 0xFFE58DB6, light);
            g.pop();
        });

        // Text is a separate queue so changing arrival times does not rebuild
        // the panel's model geometry.
        MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (g, offset) -> {
            transform(g, pos, facing, offset);
            text(g, routeNumber, 35, 36, 5.5F, BLACK);
            text(g, direction, 114, 37, 5.0F, BLUE);
            text(g, "1er metro", 28, 125, 2.1F, WHITE_COLOR);
            text(g, "2e metro", 221, 125, 2.1F, WHITE_COLOR);
            text(g, firstTime, 69, 198, 12.0F, YELLOW);
            text(g, secondTime, 264, 198, 12.0F, YELLOW);
            text(g, "M", 395, 126, 3.1F, BLUE);
            text(g, "8", 421, 126, 3.1F, BLACK);
            text(g, "Infos trafic", 408, 192, 2.9F, BLACK);
            text(g, clock, 640, 12, 3.0F, YELLOW);
            g.pop();
        });
    }

    private static LongArrayList autoPlatforms(BlockPANAMPIDS.BlockEntity entity, BlockPos pos) {
        final long tick = (long) Math.floor(InitClient.getGameTick());
        final long revision = MinecraftClientData.getInstance().networkRevision;
        final PlatformCache cached = CACHE.get(entity);
        if (cached != null && tick >= cached.tick && tick - cached.tick < 40 && cached.revision == revision) return cached.platforms;
        final LongArrayList result = new LongArrayList();
        InitClient.findClosePlatform(pos.down(4), 5, platform -> result.add(platform.getId()));
        CACHE.put(entity, new PlatformCache(tick, revision, result));
        return result;
    }

    private static String minutes(ArrivalResponse arrival, long now) {
        final long seconds = (arrival.getArrival() - now) / 1000;
        return seconds <= 0 ? "0" : String.valueOf((seconds + 59) / 60);
    }

    private static void transform(GraphicsHolder g, BlockPos pos, Direction facing, Vector3d offset) {
        g.push();
        g.translate(pos.getX() - offset.getXMapped() + 0.5, pos.getY() - offset.getYMapped() + 0.5, pos.getZ() - offset.getZMapped() + 0.5);
        g.rotateYDegrees(90 - facing.asRotation());
        g.rotateZDegrees(180);
        g.translate(-0.5, SCREEN_HEIGHT / 2, 0.185);
        g.scale(SCREEN_WIDTH / 771F, SCREEN_HEIGHT / 356F, 1);
    }

    private static void rect(GraphicsHolder g, float x, float y, float w, float h, int color, int light) {
        IDrawing.drawTexture(g, x, y, 0, x + w, y + h, 0, Direction.UP, color, light);
    }

    private static void text(GraphicsHolder g, String value, float x, float y, float size, int color) {
        g.push();
        g.translate(x, y, -0.001);
        g.scale(size, size, 1);
        g.drawText(value, 0, 0, color, false, GraphicsHolder.getDefaultLight());
        g.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox2(BlockPANAMPIDS.BlockEntity entity) {
        return true;
    }
}
