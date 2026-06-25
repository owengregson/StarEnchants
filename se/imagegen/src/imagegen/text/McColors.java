package imagegen.text;

import java.util.Arrays;

/**
 * The 16 legacy colour codes → RGB, plus the text shadow Minecraft draws one pixel down-right under each glyph
 * (each channel at 25% — e.g. white {@code FFFFFF} → {@code 3F3F3F}). Codes match {@code item.render.Colors}.
 */
public final class McColors {

    private McColors() {
    }

    public static final int DEFAULT_RGB = 0xFFFFFF;

    private static final int[] RGB = new int[128];

    static {
        Arrays.fill(RGB, -1);
        put('0', 0x000000);
        put('1', 0x0000AA);
        put('2', 0x00AA00);
        put('3', 0x00AAAA);
        put('4', 0xAA0000);
        put('5', 0xAA00AA);
        put('6', 0xFFAA00);
        put('7', 0xAAAAAA);
        put('8', 0x555555);
        put('9', 0x5555FF);
        put('a', 0x55FF55);
        put('b', 0x55FFFF);
        put('c', 0xFF5555);
        put('d', 0xFF55FF);
        put('e', 0xFFFF55);
        put('f', 0xFFFFFF);
    }

    private static void put(char c, int rgb) {
        RGB[c] = rgb;
    }

    /** RGB for a legacy colour code char, or {@code -1} if it is not a colour code. */
    public static int rgb(char code) {
        char c = Character.toLowerCase(code);
        return c < 128 ? RGB[c] : -1;
    }

    /** The 25%-brightness shadow colour Minecraft renders under text of {@code rgb}. */
    public static int shadow(int rgb) {
        return (((rgb >> 16 & 0xFF) / 4) << 16) | (((rgb >> 8 & 0xFF) / 4) << 8) | ((rgb & 0xFF) / 4);
    }
}
