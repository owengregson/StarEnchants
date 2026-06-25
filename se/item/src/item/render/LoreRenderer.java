package item.render;

import item.codec.CombatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * line-building ({@link #lines}) is pure and unit-testable with no server or {@code Library}; the
 * wiring passes {@code Library::displayNameOf} (which covers enchants AND crystals) as that
 * function. Only {@link #apply} touches Bukkit, through the legacy {@code setLore(List<String>)}
 * that is stable across the whole 1.17.1 → 26.1.x range.
 */
public final class LoreRenderer {

    private final Supplier<LoreStyle> style;
    private final Function<String, String> displayNameOf;
    private final Function<String, String> enchantColorOf;
    private final SetLore setLore;

    /**
     * The authored lore of a set's members, looked up from state at render time so a worn set piece
     * keeps its flavour lore even after it is enchanted (lore is rebuilt from scratch on every change).
     * The wiring passes a {@code Library}-backed lookup; tests/fixtures use {@link #NONE}.
     */
    public interface SetLore {
        /** The lore shared by every armour piece of {@code setKey} (empty if none / unknown). */
        List<String> armor(String setKey);

        /** The weapon's own lore for {@code setKey} (empty if none / unknown). */
        List<String> weapon(String setKey);

        /** A lookup that renders no member lore (the generic "(Set)" marker still shows). */
        SetLore NONE = new SetLore() {
            @Override public List<String> armor(String setKey) {
                return List.of();
            }

            @Override public List<String> weapon(String setKey) {
                return List.of();
            }
        };
    }

    /** Fixed-style renderer (tests, fixtures); the style never changes, no per-tier colour, no set lore. */
    public LoreRenderer(LoreStyle style, Function<String, String> displayNameOf) {
        this(() -> Objects.requireNonNull(style, "style"), displayNameOf, key -> null, SetLore.NONE);
    }

    /**
     * Fixed-style renderer with per-enchant tier colours (tests, fixtures). {@code enchantColorOf} maps
     * a base key to its tier's legacy {@code '&'}-code, or {@code null}/blank to fall back to the style's
     * universal {@link LoreStyle#enchantColor()}.
     */
    public LoreRenderer(LoreStyle style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf) {
        this(() -> Objects.requireNonNull(style, "style"), displayNameOf, enchantColorOf, SetLore.NONE);
    }

    /** Live-style renderer with no per-tier colour and no set-member lore (suites that mint neither). */
    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf) {
        this(style, displayNameOf, key -> null, SetLore.NONE);
    }

    /** Live-style renderer with set-member lore but a universal enchant colour (no per-tier colours). */
    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf, SetLore setLore) {
        this(style, displayNameOf, key -> null, setLore);
    }

    /**
     * Live-style renderer (the composition root): {@code style} is re-read on every render, so a
     * {@code /se reload} that swaps the master {@code config.yml}'s {@code lore:} section takes effect on
     * the next render with no re-wiring. {@code enchantColorOf} colours each enchant's name by its rarity
     * tier (base key -> legacy {@code '&'}-code, per ADR-0016 §2; {@code null}/blank → the style's universal
     * {@link LoreStyle#enchantColor()}). {@code setLore} renders each worn set member's authored lore.
     */
    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf, SetLore setLore) {
        this.style = Objects.requireNonNull(style, "style");
        this.displayNameOf = Objects.requireNonNull(displayNameOf, "displayNameOf");
        this.enchantColorOf = Objects.requireNonNull(enchantColorOf, "enchantColorOf");
        this.setLore = Objects.requireNonNull(setLore, "setLore");
    }

    /**
     * The lore lines for {@code state}, colour-translated and in stored order: one line per enchant
     * ({@code name level}, the name coloured by its rarity tier) then one per crystal. Empty when the
     * item carries no combat state.
     */
    public List<String> lines(CombatState state) {
        LoreStyle style = this.style.get(); // resolve the live style once per render (reload-swappable)
        List<String> out = new ArrayList<>(state.enchants().size() + state.crystals().size());
        for (Map.Entry<String, Integer> enchant : state.enchants().entrySet()) {
            String name = nameOr(enchant.getKey(), style);
            String level = style.roman() ? Numerals.roman(enchant.getValue()) : Integer.toString(enchant.getValue());
            String tierColor = enchantColorOf.apply(enchant.getKey());        // per-tier colour (ADR-0016 §2)
            String color = tierColor != null && !tierColor.isBlank() ? tierColor : style.enchantColor();
            out.add(Colors.translate(color + name + " " + style.levelColor() + level));
        }
        for (String crystalEntry : state.crystals()) {
            // One line per crystal slot; a multi-crystal entry ("a+b", §E) lists both component names.
            List<String> components = item.codec.CrystalItemData.componentsOf(crystalEntry);
            StringBuilder label = new StringBuilder();
            for (String component : components) {
                if (label.length() > 0) {
                    label.append(" + ");
                }
                label.append(nameOr(component, style));
            }
            out.add(Colors.translate(style.crystalColor() + label));
        }
        if (!state.heroic().isZero()) {
            out.add(Colors.translate("&6&lHEROIC")); // the §F "heroic piece" marker, rendered from state
        }
        if (state.setKey() != null) {
            // An armour set member: its set's SHARED armour lore, then the set marker (§6.6) — all from state.
            for (String line : setLore.armor(state.setKey())) {
                out.add(Colors.translate(line));
            }
            out.add(Colors.translate("&b" + nameOr(state.setKey(), style)
                    + (state.omni() ? " &d(Omni Set)" : " &7(Set)")));
        }
        if (state.setWeaponKey() != null) {
            // A set WEAPON member: its own lore, then a weapon marker naming its set (§6.6) — all from state.
            for (String line : setLore.weapon(state.setWeaponKey())) {
                out.add(Colors.translate(line));
            }
            out.add(Colors.translate("&b" + nameOr(state.setWeaponKey(), style) + " &7(Set Weapon)"));
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

    private String nameOr(String key, LoreStyle style) {
        String display = displayNameOf.apply(key);
        return display != null ? display : style.unknownLabel();
    }
}
