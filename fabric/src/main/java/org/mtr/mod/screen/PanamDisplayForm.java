package org.mtr.mod.screen;

import org.mtr.mod.data.PanamScreenSettings;
import java.math.BigDecimal;

/** Numeric editing rules shared by the brush widgets; no Minecraft dependencies. */
final class PanamDisplayForm {
    static final String[] LABELS = {
        "Décalage X", "Hauteur Y", "Décalage Z", "Orientation Y (°)", "Inclinaison X (°)", "Roulis Z (°)",
        "Largeur (blocs)", "Hauteur (blocs)", "Zoom (1 = 100 %)", "Pixels en largeur", "Pixels en hauteur"
    };
    static final String[] HELP = {
        "X : déplacement latéral depuis le bloc, de -16 à 16 blocs.",
        "Y : déplacement vertical depuis le bloc, de -16 à 16 blocs.",
        "Z : déplacement en profondeur depuis le bloc, de -16 à 16 blocs.",
        "Y : tourner l'écran à gauche ou à droite, de -180 à 180°.",
        "X : incliner l'écran vers le haut ou le bas, de -180 à 180°.",
        "Z : pencher l'écran sur le côté, de -180 à 180°.",
        "Largeur dans le monde : de 0,25 à 16 blocs.",
        "Hauteur dans le monde : de 0,25 à 16 blocs.",
        "Zoom : de 0,5 à 3. Augmenter agrandit le contenu.",
        "Résolution : de 128 à 2048 pixels, nombre entier.",
        "Résolution : de 128 à 2048 pixels, nombre entier."
    };
    static final int[][] GROUPS = {{6, 7, 8, 9, 10}, {0, 1, 2, 3, 4, 5}};
    private static final float[] MIN = {-16,-16,-16,-180,-180,-180,.25f,.25f,.5f,128,128};
    private static final float[] MAX = {16,16,16,180,180,180,16,16,3,2048,2048};
    private static final float[] STEP = {.0625f,.0625f,.0625f,5,5,5,.125f,.125f,.1f,128,128};

    static String format(float value) {
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }
    static String[] values(PanamScreenSettings s) {
        float[] values = {s.x,s.y,s.z,s.yaw,s.pitch,s.roll,s.width,s.height,s.zoom,s.resolutionWidth,s.resolutionHeight};
        String[] text = new String[values.length];
        for(int i=0;i<values.length;i++)text[i]=format(values[i]);
        return text;
    }
    static float parse(String text) {
        return Float.parseFloat(text.trim().replace(',', '.'));
    }
    static boolean valid(int index,String text) {
        try {
            float value=parse(text);
            return Float.isFinite(value)&&value>=MIN[index]&&value<=MAX[index]&&(index<9||value==(int)value);
        } catch(NumberFormatException e) { return false; }
    }
    static String adjust(int index,String text,int direction) {
        float value=valid(index,text)?parse(text):parse(values(PanamScreenSettings.DEFAULT)[index]);
        // Decimal arithmetic keeps repeated +/- zoom steps free of float noise.
        float next=new BigDecimal(Float.toString(value)).add(new BigDecimal(Float.toString(STEP[index])).multiply(BigDecimal.valueOf(direction))).floatValue();
        return format(Math.max(MIN[index],Math.min(MAX[index],next)));
    }
    static int invalid(String[] text) {
        for(int i=0;i<11;i++)if(!valid(i,text[i]))return i;
        return -1;
    }
    static PanamScreenSettings settings(String[] text) {
        if(invalid(text)>=0)throw new IllegalArgumentException("Invalid PANAM display settings");
        return new PanamScreenSettings(parse(text[0]),parse(text[1]),parse(text[2]),parse(text[3]),parse(text[4]),parse(text[5]),parse(text[6]),parse(text[7]),(int)parse(text[9]),(int)parse(text[10]),parse(text[8]));
    }
}
