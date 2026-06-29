package item.render;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;

/**
 * The applied-scroll PROTECTED lore lines, rendered from an item's protection state (never parsed back, §4.2).
 * A white scroll guard contributes its line; a holy white scroll keep-marker contributes its own. They are
 * independent (an item may carry both), so both lines can appear. The caller appends these at the bottom of the
 * lore body — above any trak count line — and re-renders whenever the markers change, so a line shows while its
 * marker is present and vanishes when it is spent.
 */
public final class ProtectionLore {

    private ProtectionLore() {
    }

    /**
     * The protection lines for the given marker state: the white {@code protectedTemplate} when {@code guarded},
     * then the holy {@code holyTemplate} when {@code holy}; empty when neither. Each is colour-translated.
     */
    public static List<String> lines(boolean guarded, boolean holy, String protectedTemplate, String holyTemplate) {
        List<String> out = new ArrayList<>(2);
        if (guarded) {
            out.add(Colors.translate(protectedTemplate));
        }
        if (holy) {
            out.add(Colors.translate(holyTemplate));
        }
        return out;
    }

    /**
     * Whether {@code line} is a rendered protection line, matched on its VISIBLE content (colours stripped)
     * against the two templates — so it still matches after Bukkit's lore colour-code normalisation. The
     * templates carry no tokens, so their visible text is a stable identifier.
     */
    public static boolean isProtectionLine(String line, String protectedTemplate, String holyTemplate) {
        if (line == null) {
            return false;
        }
        String visible = ChatColor.stripColor(line);
        return visible.equals(ChatColor.stripColor(Colors.translate(protectedTemplate)))
                || visible.equals(ChatColor.stripColor(Colors.translate(holyTemplate)));
    }
}
