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
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Sillon PANAM: railway information display (771 x 356 reference viewport).
 *
 * Independently implements the visual and motion vocabulary of arnoclr/panam
 * using MTR's existing display renderer and locally available arrival data.
 * No WebView, browser, JavaScript runtime, external API, JCM dependency or
 * copied font files. MTR's custom PIDS messages are the traffic-info source.
 *
 * Only state transitions are animated. Arrival snapshots (250 ms), clock
 * (1 s), configured messages (2 s), nearby platforms (40 ticks) and rendered
 * static surfaces are kept separate from per-frame animation transforms.
 * Weak entity keys ensure unloaded screens do not stay alive.
 */
public final class RenderPANAMPIDS extends BlockEntityRenderer<BlockPANAMPIDS.BlockEntity> {
    private static final Identifier PIXEL = new Identifier(Init.MOD_ID, "textures/block/white.png");
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF050505;
    private static final int NAVY = 0xFF0E0184;
    private static final int YELLOW = 0xFFFFBE00;
    private static final int GREY = 0xFF45494C;
    private static final int LIGHT_GREY = 0xFFF7F7F7;

    private static final float VIEW_WIDTH = 771F;
    private static final float VIEW_HEIGHT = 356F;
    private static final float WORLD_WIDTH = 2F;
    private static final float WORLD_HEIGHT = WORLD_WIDTH * VIEW_HEIGHT / VIEW_WIDTH;
    private static final long DEPARTURE_TRANSITION_MS = 1500;
    private static final long NUMBER_ZOOM_MS = 2000;
    private static final long NOTICE_TRANSITION_MS = 1600;
    private static final long NOTICE_ROTATION_MS = 10000;

    private static final WeakHashMap<BlockPANAMPIDS.BlockEntity, PanelState> PANELS = new WeakHashMap<>();

    private static final class PanelState {
        private long sampledAt;
        private long messageSampledAt;
        private long clockSampledAt;
        private long platformTick = -1;
        private long networkRevision = -1;
        private LongArrayList nearbyPlatforms = new LongArrayList();
        private String clock = "--:--";
        private String routeLabel = "M";
        private String destination = "En attente";
        private int routeColor = 0xFFE1D000;
        private final String[] destinations = {"", "", ""};
        private final String[] times = {"--", "--", "--"};
        private final String[] previousTimes = {"--", "--", "--"};
        private final long[] arrivalAt = {0L, 0L, 0L};
        private final long[] numericChangeAt = {0L, 0L, 0L};
        private final long[] arrivalRoute = {-1L, -1L, -1L};
        private int count;
        private boolean mixedDestinations;
        private long departureChangeAt;
        private final ArrayList<String> notices = new ArrayList<>();
        private int currentNotice = -1;
        private long noticeChangeAt;
        private long noticeCycleAt;
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

        final long now = System.currentTimeMillis();
        PanelState state = PANELS.get(entity);
        if (state == null) {
            state = new PanelState();
            PANELS.put(entity, state);
        }
        if (now - state.sampledAt >= 250 || state.sampledAt == 0) {
            sampleArrivals(entity, pos, state, now);
        }
        if (now - state.messageSampledAt >= 2000 || state.messageSampledAt == 0) {
            sampleMessages(entity, state, now);
        }
        if (now - state.clockSampledAt >= 1000 || state.clockSampledAt == 0) {
            final LocalTime local = LocalTime.now();
            state.clock = twoDigits(local.getHour()) + ":" + twoDigits(local.getMinute());
            state.clockSampledAt = now;
        }
        rotateNotice(state, now);

        final Direction facing = IBlock.getStatePropertySafe(world, pos, DirectionHelper.FACING);
        final PanelState frame = state;
        final boolean hasNotice = !frame.notices.isEmpty();

        // All panel rectangles use one preexisting MTR texture / light render layer.
        MainRenderer.scheduleRender(PIXEL, false, QueuedRenderLayer.LIGHT, (g, offset) -> {
            transform(g, pos, facing, offset);
            g.createVertexConsumer(MoreRenderLayers.getLight(PIXEL, false));
            rect(g, -8, -8, 787, 372, GREY, light);
            rect(g, 0, 0, 771, 356, BLACK, light);

            // The original PANAM white header, route-coloured underline and
            // black/yellow departure zone. Header proportions follow 771:356.
            rect(g, 0, 0, 771, 99, WHITE, light);
            rect(g, 0, 96, 771, 11, frame.routeColor, light);
            circle(g, 49, 49, 34, NAVY, light);
            circle(g, 49, 49, 30, WHITE, light);
            circle(g, 110, 49, 28, frame.routeColor, light);

            if (frame.mixedDestinations) {
                for (int i = 0; i < frame.count; i++) {
                    final float y = 115 + i * 78;
                    rect(g, 10, y, 357, 70, WHITE, light);
                    rect(g, 295, y + 4, 65, 62, BLACK, light);
                }
            } else {
                rect(g, 191, 175, 4, 111, WHITE, light);
            }

            // PANAM hides the disruptions slider if there is no usable message.
            if (hasNotice) {
                rect(g, 385, 108, 386, 248, WHITE, light);
                rect(g, 385, 108, 386, 52, LIGHT_GREY, light);
                rect(g, 385, 108, 92, 52, WHITE, light);
                circle(g, 413, 133, 15, NAVY, light);
                circle(g, 413, 133, 12, WHITE, light);
                circle(g, 445, 133, 13, frame.routeColor, light);
            }

            // Bottom edge is always the live MTR route colour.
            rect(g, 0, 350, 771, 6, frame.routeColor, light);
            g.pop();
        });

        // The text queue is separate, retaining compatibility with Sillon's
        // batched text pipeline. Movement/zoom are matrix transforms only.
        MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (g, offset) -> {
            transform(g, pos, facing, offset);
            fitText(g, "M", 36, 35, 26, NAVY, 29);
            fitText(g, frame.routeLabel, 92, 36, 28, BLACK, 35);
            fitText(g, frame.destination, 151, 36, 6.2F, NAVY, 590);

            if (frame.count == 0) {
                fitText(g, "Temps d'attente indisponibles.", 25, 193, 4.7F, WHITE, 680);
            } else if (frame.mixedDestinations) {
                drawMultipleDestinations(g, frame, now);
            } else {
                fitText(g, "1er métro", 16, 123, 3.5F, WHITE, 165);
                if (frame.count > 1) {
                    fitText(g, "2e métro", 206, 123, 3.5F, WHITE, 165);
                }
                animatedMinutes(g, frame, 0, 64, 196, 15.4F, YELLOW, 125, now);
                if (frame.count > 1) {
                    animatedMinutes(g, frame, 1, 258, 196, 15.4F, YELLOW, 115, now);
                }
            }

            // The colon pulses every 1.4 s as in PANAM; avoid recreating clock strings.
            final boolean dimColon = now % 1400 >= 700;
            final String clockValue = dimColon ? frame.clock.replace(':', ' ') : frame.clock;
            fitText(g, clockValue, 667, 15, 4.2F, YELLOW, 96);

            if (hasNotice) {
                drawNotice(g, frame, now);
            }
            g.pop();
        });
    }

    private static void drawMultipleDestinations(GraphicsHolder g, PanelState state, long now) {
        final float slide = slideProgress(now - state.departureChangeAt, DEPARTURE_TRANSITION_MS);
        final float entryX = state.departureChangeAt == 0 ? 0 : (1 - slide) * 95;
        for (int i = 0; i < state.count; i++) {
            final float y = 123 + i * 78;
            fitText(g, state.destinations[i], 21 + entryX, y + 14, 4.0F, NAVY, 259 - entryX);
            animatedMinutes(g, state, i, 309, y + 14, 6.0F, YELLOW, 43, now);
        }
    }

    private static void animatedMinutes(GraphicsHolder g, PanelState state, int index,
                                        float x, float y, float size, int color,
                                        float width, long now) {
        if (index >= state.count) return;
        final String value = state.times[index];
        if ("...".equals(value)) {
            // Three successive 1.4 s dot pulses for imminent arrivals.
            for (int dot = 0; dot < 3; dot++) {
                final long phase = (now + 1400 - dot * 300) % 1400;
                final float scale = phase < 280 ? 0.7F : 1F;
                g.push();
                g.translate(x + dot * 27, y + 58, -0.002);
                g.scale(scale, scale, 1);
                fitText(g, ".", 0, 0, 14.0F, color, 30);
                g.pop();
            }
            return;
        }
        final long elapsed = now - state.numericChangeAt[index];
        final float progress = slideProgress(elapsed, NUMBER_ZOOM_MS);
        final float zoom = 1F + 0.4F * (1F - easeOut(progress));
        g.push();
        g.translate(x, y, -0.002);
        g.scale(zoom, zoom, 1);
        fitText(g, value, 0, 0, size, color, width / zoom);
        g.pop();
    }

    private static void drawNotice(GraphicsHolder g, PanelState state, long now) {
        final int selected = state.currentNotice;
        if (selected < 0 || selected >= state.notices.size()) return;
        final float progress = slideProgress(now - state.noticeChangeAt, NOTICE_TRANSITION_MS);
        final float slide = 1 - easeOut(progress);
        fitText(g, "M", 404 + slide * 18, 119, 4.5F, NAVY, 20);
        fitText(g, state.routeLabel, 431 + slide * 18, 120, 4.2F, BLACK, 42);
        fitText(g, "INFORMATION VOYAGEURS", 495 + slide * 20, 126, 3.4F, NAVY, 255);
        // MTR custom PIDS messages can be configured at the station using the
        // existing brush. We do not make up RT traffic alerts or query RATP.
        final String message = state.notices.get(selected);
        final int maxCharacters = 49;
        final ArrayList<String> wrapped = wrap(message, maxCharacters, 4);
        for (int i = 0; i < wrapped.size(); i++) {
            fitText(g, wrapped.get(i), 403 + 17 * slide, 178 + i * 35, 3.3F, NAVY, 349);
        }
        if (state.notices.size() > 1) {
            fitText(g, (selected + 1) + "/" + state.notices.size(), 700, 324, 2.4F, GREY, 54);
        }
    }

    private static ArrayList<String> wrap(String value, int max, int maxRows) {
        final ArrayList<String> out = new ArrayList<>(maxRows);
        final String[] words = value.split("\\s+");
        final StringBuilder row = new StringBuilder();
        for (String word : words) {
            if (row.length() > 0 && row.length() + 1 + word.length() > max) {
                out.add(row.toString());
                row.setLength(0);
                if (out.size() == maxRows) return out;
            }
            if (row.length() > 0) row.append(' ');
            row.append(word.length() > max ? word.substring(0, max) : word);
        }
        if (row.length() > 0 && out.size() < maxRows) out.add(row.toString());
        return out;
    }

    private static void sampleArrivals(BlockPANAMPIDS.BlockEntity entity, BlockPos pos,
                                       PanelState state, long now) {
        state.sampledAt = now;
        final LongCollection platforms;
        if (!entity.getPlatformIds().isEmpty()) {
            platforms = entity.getPlatformIds();
        } else {
            final long tick = (long) Math.floor(InitClient.getGameTick());
            final long revision = MinecraftClientData.getInstance().networkRevision;
            if (state.platformTick < 0 || tick < state.platformTick ||
                    tick - state.platformTick >= 40 || state.networkRevision != revision) {
                final LongArrayList next = new LongArrayList();
                InitClient.findClosePlatform(pos.down(4), 5, platform -> next.add(platform.getId()));
                state.nearbyPlatforms = next;
                state.platformTick = tick;
                state.networkRevision = revision;
            }
            platforms = state.nearbyPlatforms;
        }

        final ObjectArrayList<ArrivalResponse> arrivals =
                ArrivalsCacheClient.INSTANCE.requestArrivals(platforms);
        final int nextCount = Math.min(3, arrivals.size());
        boolean changed = nextCount != state.count;
        String firstDestination = null;
        boolean mixed = false;
        for (int i = 0; i < nextCount; i++) {
            final ArrivalResponse a = arrivals.get(i);
            final String destination = a.getDestination().split("\\|")[0];
            if (firstDestination == null) firstDestination = destination;
            else if (!firstDestination.equals(destination)) mixed = true;
            // Keep the same visual train if the server only corrected its ETA.
            final boolean sameTrain = state.arrivalRoute[i] == a.getRouteId() &&
                    state.destinations[i].equals(destination) &&
                    Math.abs(state.arrivalAt[i] - a.getArrival()) < 45000;
            if (!sameTrain) changed = true;
            state.arrivalRoute[i] = a.getRouteId();
            state.arrivalAt[i] = a.getArrival();
            state.destinations[i] = destination;
            final long seconds = (a.getArrival() - ArrivalsCacheClient.INSTANCE.getMillisOffset() - now) / 1000;
            final String time = seconds < 0 ? "..." : Long.toString(seconds / 60);
            if (!time.equals(state.times[i])) {
                state.previousTimes[i] = state.times[i];
                state.times[i] = time;
                state.numericChangeAt[i] = now;
            }
        }
        if (changed) state.departureChangeAt = now;
        state.count = nextCount;
        state.mixedDestinations = mixed;
        final ArrivalResponse first = nextCount == 0 ? null : arrivals.get(0);
        final SimplifiedRoute route = first == null ? null :
                MinecraftClientData.getInstance().simplifiedRouteIdMap.get(first.getRouteId());
        if (route != null) {
            state.routeColor = 0xFF000000 | (route.getColor() & 0xFFFFFF);
            final String[] names = route.getName().split("\\|");
            state.routeLabel = names.length == 0 ? "M" : names[0];
        }
        state.destination = firstDestination == null ? "En attente" : firstDestination;
    }

    private static void sampleMessages(BlockPANAMPIDS.BlockEntity entity, PanelState state, long now) {
        state.messageSampledAt = now;
        final ArrayList<String> next = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final String value = entity.getMessage(i);
            if (value == null || value.trim().isEmpty()) continue;
            for (String part : value.split("\\|")) {
                final String trimmed = part.trim();
                if (!trimmed.isEmpty() && !next.contains(trimmed)) {
                    next.add(trimmed.length() > 195 ? trimmed.substring(0, 195) : trimmed);
                    if (next.size() == 8) break;
                }
            }
            if (next.size() == 8) break;
        }
        if (!next.equals(state.notices)) {
            state.notices.clear();
            state.notices.addAll(next);
            state.currentNotice = next.isEmpty() ? -1 : 0;
            state.noticeChangeAt = now;
            state.noticeCycleAt = now;
        }
    }

    private static void rotateNotice(PanelState state, long now) {
        if (state.notices.size() < 2) return;
        if (now - state.noticeCycleAt >= NOTICE_ROTATION_MS) {
            state.currentNotice = (state.currentNotice + 1) % state.notices.size();
            state.noticeChangeAt = now;
            state.noticeCycleAt = now;
        }
    }

    private static String twoDigits(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }

    private static float slideProgress(long elapsed, long duration) {
        return Math.max(0F, Math.min(1F, elapsed / (float) duration));
    }

    private static float easeOut(float x) {
        // Smooth near-terminal progression, no external tweening dependency.
        final float v = 1 - x;
        return 1 - v * v * v;
    }

    private static void circle(GraphicsHolder g, float cx, float cy, float radius,
                               int color, int light) {
        // One texture batch; slices give an anti-aliased-looking circular route badge
        // without a separate GL renderer or per-screen texture upload.
        for (int i = -12; i <= 12; i++) {
            final float dy = i * radius / 13F;
            final float dx = (float) Math.sqrt(radius * radius - dy * dy);
            rect(g, cx - dx, cy + dy, 2 * dx, 2.1F * radius / 13F, color, light);
        }
    }

    private static void transform(GraphicsHolder g, BlockPos pos, Direction facing, Vector3d offset) {
        g.push();
        g.translate(pos.getX() - offset.getXMapped() + 0.5,
                pos.getY() - offset.getYMapped() + 0.5,
                pos.getZ() - offset.getZMapped() + 0.5);
        g.rotateYDegrees(90 - facing.asRotation());
        g.rotateZDegrees(180);
        g.translate(-0.5, WORLD_HEIGHT / 2, -0.26);
        g.scale(WORLD_WIDTH / VIEW_WIDTH, WORLD_HEIGHT / VIEW_HEIGHT, 1);
    }

    private static void rect(GraphicsHolder g, float x, float y, float w, float h, int color, int light) {
        IDrawing.drawTexture(g, x, y, 0, x + w, y + h, 0, Direction.UP, color, light);
    }

    private static void fitText(GraphicsHolder g, String value, float x, float y,
                                float size, int color, float maxWidth) {
        if (value == null || value.isEmpty()) return;
        g.push();
        g.translate(x, y, -0.003);
        g.scale(size, size, 1);
        final int charactersWidth = GraphicsHolder.getTextWidth(value);
        if (charactersWidth > 0 && maxWidth > 0 && charactersWidth * size > maxWidth) {
            g.scale(maxWidth / (charactersWidth * size), 1, 1);
        }
        g.drawText(value, 0, 0, color, false, GraphicsHolder.getDefaultLight());
        g.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox2(BlockPANAMPIDS.BlockEntity entity) {
        return true;
    }
}
