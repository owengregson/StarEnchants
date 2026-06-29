package imagegen.imports;

import item.render.Numerals;
import java.util.Locale;
import java.util.Set;

/**
 * Renders the gray enchant lines Minecraft itself draws under an item's name — the part the plugin's
 * {@link item.render.LoreRenderer} never produces, because vanilla enchants are stamped onto the real
 * ItemStack and rendered by the client, not from {@code CombatState}. Set armour/weapons carry vanilla
 * enchants ({@code PROTECTION}/{@code SHARPNESS}/{@code UNBREAKING}, §6.6), so a game-faithful preview must
 * draw them. Output is a §-coded line (§7, or §c for a curse) matching the renderer's already-§ output.
 *
 * <p>Names follow Minecraft's display rule: the token title-cased with small connecting words lowercased
 * ({@code BANE_OF_ARTHROPODS} → {@code Bane of Arthropods}); the Roman level reuses the same
 * {@link Numerals} the lore renderer uses. Enchants whose maximum level is one ({@code MENDING},
 * {@code SILK_TOUCH}, …) render with no numeral, exactly as the client shows them.
 */
final class VanillaEnchantLore {

    private VanillaEnchantLore() {
    }

    /** Connecting words Minecraft leaves lowercase mid-name (e.g. "Bane of Arthropods", "Luck of the Sea"). */
    private static final Set<String> SMALL_WORDS = Set.of("of", "the", "and");

    /** Enchants with a max level of one — the client shows these with no numeral, at any level. */
    private static final Set<String> MAX_ONE = Set.of(
            "MENDING", "SILK_TOUCH", "INFINITY", "FLAME", "PUNCH_ARROW_KNOCKBACK", "MULTISHOT", "CHANNELING",
            "AQUA_AFFINITY", "FROST_WALKER", "BINDING_CURSE", "VANISHING_CURSE");

    /** The gray (§7) — or for a curse, red (§c) — enchant line for {@code name} at {@code level}. */
    static String line(String name, int level) {
        String token = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        String color = isCurse(token) ? "§c" : "§7";
        String display = display(token);
        if (level <= 0 || MAX_ONE.contains(token)) {
            return color + display;
        }
        return color + display + " " + Numerals.roman(level);
    }

    private static boolean isCurse(String token) {
        return token.startsWith("CURSE_OF") || token.endsWith("_CURSE") || token.contains("CURSE");
    }

    /** Title-case an enum token to its Minecraft display name; small connecting words stay lowercase. */
    private static String display(String token) {
        String[] words = token.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            if (i > 0 && SMALL_WORDS.contains(word)) {
                out.append(word); // a mid-name connecting word stays lowercase
            } else {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }
}
