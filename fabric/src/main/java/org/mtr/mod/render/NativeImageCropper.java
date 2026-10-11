package org.mtr.mod.render;

import org.mtr.mapping.holder.NativeImage;
import org.mtr.mapping.holder.NativeImageFormat;

/** CPU-only crop; transfers ownership of unchanged images and frees replaced sources. */
public final class NativeImageCropper {
    private NativeImageCropper() {}
    public static NativeImage crop(NativeImage source, int maximum) {
        if (source == null || source.getWidth() <= maximum && source.getHeight() <= maximum) return source;
        NativeImage cropped = null;
        try {
            cropped = new NativeImage(NativeImageFormat.getAbgrMapped(), Math.min(maximum, source.getWidth()), Math.min(maximum, source.getHeight()), false);
            for (int x = 0; x < cropped.getWidth(); x++) {
                for (int y = 0; y < cropped.getHeight(); y++) cropped.setPixelColor(x, y, source.getColor(x, y));
            }
            return cropped;
        } catch (RuntimeException | Error error) { if (cropped != null) cropped.close(); throw error; }
        finally { source.close(); }
    }
}
