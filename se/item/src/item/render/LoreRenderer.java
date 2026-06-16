package item.render;

import compile.load.EnchantDef;
import item.codec.CombatState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Renders an item's lore from its {@link CombatState} (docs/architecture.md §4.2) — and never the
 * other way around. Lore is a pure, deterministic projection of the stored state: identity lives
 * in the PDC record, so the rendered text is rebuilt from scratch on every state change and is
 * never parsed back to recover state. A stored stable key absent from the catalog renders as the
 * style's {@code unknownLabel} and is skipped, never crashing the render (§5.3).
 *
 * <p>The display-name lookup is injected as a {@code base key -> display} function, so the
 * line-building ({@link #lines}) is pure and unit-testable with no server or {@code Library};
 * {@link #displayNames(List)} builds that function from a loaded catalog for the wiring. Only
 * {@link #apply} touches Bukkit, through the legacy {@code setLore(List<String>)} that is stable
 * across the whole 1.17.1 → 26.1.x range.
 */
public final class LoreRenderer {

    private final LoreStyle style;
    private final Function<String, String> displayNameOf;

    public LoreRenderer(LoreStyle style, Function<String, String> displayNameOf) {
        this.style = Objects.requireNonNull(style, "style");
        this.displayNameOf = Objects.requireNonNull(displayNameOf, "displayNameOf");
    }

    /**
     * The lore lines for {@code state}, colour-translated and in stored order: one line per enchant
     * ({@code name level}) then one per crystal. Empty when the item carries no combat state.
     */
    public List<String> lines(CombatState state) {
        List<String> out = new ArrayList<>(state.enchants().size() + state.crystals().size());
        for (Map.Entry<String, Integer> enchant : state.enchants().entrySet()) {
            String name = nameOr(enchant.getKey());
            String level = style.roman() ? Numerals.roman(enchant.getValue()) : Integer.toString(enchant.getValue());
            out.add(Colors.translate(style.enchantColor() + name + " " + style.levelColor() + level));
        }
        for (String crystal : state.crystals()) {
            out.add(Colors.translate(style.crystalColor() + nameOr(crystal)));
        }
        return out;
    }

    /**
     * Render {@code state} onto {@code stack}'s lore in place; clears the lore when the state is
     * empty. Returns whether the item could carry meta (false for e.g. an air stack).
     */
    @SuppressWarnings("deprecation") // setLore(List<String>): deprecated-not-removed across the whole range.
    public boolean apply(ItemStack stack, CombatState state) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        List<String> lore = lines(state);
        meta.setLore(lore.isEmpty() ? null : lore);
        stack.setItemMeta(meta);
        return true;
    }

    private String nameOr(String key) {
        String display = displayNameOf.apply(key);
        return display != null ? display : style.unknownLabel();
    }

    /**
     * A {@code base key -> display name} lookup over a loaded catalog (returns {@code null} for a
     * key the catalog does not define, which the renderer shows as the unknown label).
     */
    public static Function<String, String> displayNames(List<EnchantDef> catalog) {
        Map<String, String> byKey = new HashMap<>();
        for (EnchantDef def : catalog) {
            byKey.put(def.key(), def.display());
        }
        return byKey::get;
    }
}
