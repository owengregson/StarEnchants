package item.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Updates ONLY the applied-scroll PROTECTED lines on an item, in place, leaving the rest of the lore untouched
 * (§I). The scroll appliers use this instead of a full re-render from {@link item.codec.CombatState}: a soul gem
 * (or any economy item) has AUTHORED lore that is not derivable from combat state, and gear may carry trak count
 * lines a separate system owns — re-rendering the body from state would wipe both. Here the head (the enchant
 * body OR the authored economy lore) and the trak lines are preserved; the protection line(s) are re-inserted
 * above any trak lines so they keep their position.
 */
public final class ProtectionLoreRefresh {

    private ProtectionLoreRefresh() {
    }

    /**
     * Re-render the protection lines on {@code gear} from {@code protectionLines} (already computed from the
     * item's marker state), dropping any prior protection line ({@code isProtectionLine}) and keeping everything
     * else — with trak lines ({@code isTrakLine}) re-stacked below the protection lines.
     */
    @SuppressWarnings("deprecation") // get/setLore(List<String>): the floor-stable item-meta path
    public static void refresh(ItemStack gear, List<String> protectionLines,
                               Predicate<String> isProtectionLine, Predicate<String> isTrakLine) {
        if (gear == null) {
            return;
        }
        ItemMeta meta = gear.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> existing = meta.hasLore() ? meta.getLore() : List.of();
        List<String> out = compose(existing, protectionLines, isProtectionLine, isTrakLine);
        meta.setLore(out.isEmpty() ? null : out);
        gear.setItemMeta(meta);
    }

    /**
     * The pure composition (no server): drop the old protection lines from {@code existing}, keep the head
     * (body / authored economy lore) and the trak lines, and rebuild as head + new protection + traks.
     */
    public static List<String> compose(List<String> existing, List<String> protectionLines,
                                       Predicate<String> isProtectionLine, Predicate<String> isTrakLine) {
        List<String> head = new ArrayList<>();
        List<String> traks = new ArrayList<>();
        for (String line : existing) {
            if (isProtectionLine.test(line)) {
                continue; // drop the stale protection line(s); the current set is re-added below
            }
            if (isTrakLine.test(line)) {
                traks.add(line); // preserved, re-stacked under the protection lines
            } else {
                head.add(line); // the enchant body OR an economy item's authored lore — never touched
            }
        }
        List<String> out = new ArrayList<>(head.size() + protectionLines.size() + traks.size());
        out.addAll(head);
        out.addAll(protectionLines);
        out.addAll(traks);
        return out;
    }
}
