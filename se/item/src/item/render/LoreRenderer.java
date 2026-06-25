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
 * Renders lore from {@link CombatState}, never the reverse (§4.2): a deterministic projection rebuilt from
 * scratch, never parsed back. An unknown stored key renders as {@code unknownLabel}, never crashing (§5.3).
 * The display lookup is injected (not Library) so {@link #lines} stays pure and server-free; only {@link #apply}
 * touches Bukkit.
 */
public final class LoreRenderer {

    private final Supplier<LoreStyle> style;
    private final Function<String, String> displayNameOf;
    private final Function<String, String> enchantColorOf;
    private final SetLore setLore;

    /**
     * Set members' authored lore, looked up from state at render time so a worn piece keeps its flavour lore
     * after it is enchanted (render-from-scratch). Wiring uses a {@code Library}-backed lookup; tests {@link #NONE}.
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

    public LoreRenderer(LoreStyle style, Function<String, String> displayNameOf) {
        this(() -> Objects.requireNonNull(style, "style"), displayNameOf, key -> null, SetLore.NONE);
    }

    public LoreRenderer(LoreStyle style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf) {
        this(() -> Objects.requireNonNull(style, "style"), displayNameOf, enchantColorOf, SetLore.NONE);
    }

    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf) {
        this(style, displayNameOf, key -> null, SetLore.NONE);
    }

    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf, SetLore setLore) {
        this(style, displayNameOf, key -> null, setLore);
    }

    /**
     * Canonical renderer: {@code style} is re-read per render so a {@code /se reload} takes effect next render;
     * {@code enchantColorOf} colours each enchant by rarity tier ({@code null}/blank → the style's default).
     */
    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf, SetLore setLore) {
        this.style = Objects.requireNonNull(style, "style");
        this.displayNameOf = Objects.requireNonNull(displayNameOf, "displayNameOf");
        this.enchantColorOf = Objects.requireNonNull(enchantColorOf, "enchantColorOf");
        this.setLore = Objects.requireNonNull(setLore, "setLore");
    }

    /** Lore lines in stored order: one per enchant ({@code name level}), then one per crystal. Empty if no state. */
    public List<String> lines(CombatState state) {
        LoreStyle style = this.style.get(); // live style, once per render (reload-swappable)
        List<String> out = new ArrayList<>(state.enchants().size() + state.crystals().size());
        for (Map.Entry<String, Integer> enchant : state.enchants().entrySet()) {
            String name = nameOr(enchant.getKey(), style);
            String level = style.roman() ? Numerals.roman(enchant.getValue()) : Integer.toString(enchant.getValue());
            String tierColor = enchantColorOf.apply(enchant.getKey());        // per-tier colour (ADR-0016 §2)
            String color = tierColor != null && !tierColor.isBlank() ? tierColor : style.enchantColor();
            out.add(Colors.translate(color + name + " " + style.levelColor() + level));
        }
        for (String crystalEntry : state.crystals()) {
            // A multi-crystal entry ("a+b", §E) lists both component names on one line.
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
            out.add(Colors.translate("&6&lHEROIC")); // §F heroic-piece marker
        }
        if (state.setKey() != null) {
            // Armour member: shared armour lore, then the set marker (§6.6).
            for (String line : setLore.armor(state.setKey())) {
                out.add(Colors.translate(line));
            }
            out.add(Colors.translate("&b" + nameOr(state.setKey(), style)
                    + (state.omni() ? " &d(Omni Set)" : " &7(Set)")));
        }
        if (state.setWeaponKey() != null) {
            // Weapon member: its own lore, then a weapon marker naming its set (§6.6).
            for (String line : setLore.weapon(state.setWeaponKey())) {
                out.add(Colors.translate(line));
            }
            out.add(Colors.translate("&b" + nameOr(state.setWeaponKey(), style) + " &7(Set Weapon)"));
        }
        return out;
    }

    /** Render onto {@code stack} in place; clears lore when state is empty. False if the item can't carry meta (air). */
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
