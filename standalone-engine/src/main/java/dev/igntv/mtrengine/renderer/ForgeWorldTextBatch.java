package dev.igntv.mtrengine.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import dev.igntv.mtrengine.renderer.WorldTextBatch;

/** Own builder avoids corrupting Minecraft's shared tessellator or addon buffers. */
public final class ForgeWorldTextBatch {
    private static final MultiBufferSource.BufferSource SOURCE = MultiBufferSource.immediate(new BufferBuilder(4096));
    private static boolean dirty;
    static { WorldTextBatch.register(ForgeWorldTextBatch::flush); }
    private ForgeWorldTextBatch() {}
    public static void flush() { if (dirty) { dirty = false; SOURCE.endBatch(); } }
    public static void draw(Component text, PoseStack pose, int x, int y, int color, boolean shadow, int light) {
        dirty = true;
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, shadow, pose.last().pose(), SOURCE, Font.DisplayMode.NORMAL, 0, light);
    }
    public static void draw(FormattedCharSequence text, PoseStack pose, int x, int y, int color, boolean shadow, int light) {
        dirty = true;
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, shadow, pose.last().pose(), SOURCE, Font.DisplayMode.NORMAL, 0, light);
    }
    public static void draw(String text, PoseStack pose, int x, int y, int color, boolean shadow, int light) {
        dirty = true;
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, shadow, pose.last().pose(), SOURCE, Font.DisplayMode.NORMAL, 0, light);
    }
}
