package compile.load;

/**
 * Maps a {@code &}/{@code §}-coded display string to the RGB of its FIRST colour code — used to tint the
 * set-equip dust cloud to a set's colour (e.g. {@code &4Supreme} → dark red). Recognises the 16 legacy codes
 * and a {@code &#RRGGBB} hex code; returns {@code null} when no colour code is present (the caller then keeps
 * the particle's configured colour). Pure (no Bukkit).
 */
public final class ChatColorRgb {

    private ChatColorRgb() {
    }

    /** The RGB {@code [r, g, b]} of the first colour code in {@code text}, or {@code null} if it carries none. */
    public static int[] of(String text) {
        if (text == null) {
            return null;
        }
        for (int i = 0; i + 1 < text.length(); i++) {
            char marker = text.charAt(i);
            if (marker != '&' && marker != '§') {
                continue;
            }
            char code = text.charAt(i + 1);
            if (code == '#' && i + 7 < text.length()) {
                int[] hex = parseHex(text.substring(i + 2, i + 8));
                if (hex != null) {
                    return hex;
                }
                continue;
            }
            int[] legacy = legacy(Character.toLowerCase(code));
            if (legacy != null) {
                return legacy;
            }
        }
        return null;
    }

    /**
     * Whether {@code text} carries 2+ DISTINCT colour codes — a rainbow name like KOTH's
     * {@code &cK&6.&eO&2.&bT&5.&dH}. The trigger for the rainbow equip-dust cloud (each mote a random pastel)
     * rather than a single set tint. Format codes (k-o, r) don't count; a repeated colour counts once.
     */
    public static boolean isMultiColor(String text) {
        if (text == null) {
            return false;
        }
        java.util.Set<String> colors = new java.util.HashSet<>();
        for (int i = 0; i + 1 < text.length(); i++) {
            char marker = text.charAt(i);
            if (marker != '&' && marker != '§') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == '#' && i + 7 < text.length() && parseHex(text.substring(i + 2, i + 8)) != null) {
                colors.add(text.substring(i + 2, i + 8));
            } else if (legacy(code) != null) {
                colors.add(String.valueOf(code));
            }
            if (colors.size() >= 2) {
                return true;
            }
        }
        return false;
    }

    private static int[] parseHex(String rrggbb) {
        try {
            int value = Integer.parseInt(rrggbb, 16);
            return new int[] {(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF};
        } catch (NumberFormatException notHex) {
            return null;
        }
    }

    /** The standard Minecraft RGB of a legacy colour code (0-9, a-f); {@code null} for a non-colour code. */
    private static int[] legacy(char code) {
        return switch (code) {
            case '0' -> new int[] {0, 0, 0};
            case '1' -> new int[] {0, 0, 170};
            case '2' -> new int[] {0, 170, 0};
            case '3' -> new int[] {0, 170, 170};
            case '4' -> new int[] {170, 0, 0};
            case '5' -> new int[] {170, 0, 170};
            case '6' -> new int[] {255, 170, 0};
            case '7' -> new int[] {170, 170, 170};
            case '8' -> new int[] {85, 85, 85};
            case '9' -> new int[] {85, 85, 255};
            case 'a' -> new int[] {85, 255, 85};
            case 'b' -> new int[] {85, 255, 255};
            case 'c' -> new int[] {255, 85, 85};
            case 'd' -> new int[] {255, 85, 255};
            case 'e' -> new int[] {255, 255, 85};
            case 'f' -> new int[] {255, 255, 255};
            default -> null; // a format code (k-o, r) or non-code char → not a colour
        };
    }
}
