package item.render;

import item.codec.CombatState;
import item.codec.HeroicStat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Material;
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
    private final Function<ItemStack, List<String>> protectionLines;
    private final Predicate<String> trakLine; // identifies an applied-trak count line so a re-render PRESERVES it
    private final Supplier<String> countSuffix; // §I transmog name suffix template ({COUNT}); null/blank → no suffix
    private final IntSupplier baseSlots;        // §H base enchant slots, for the orb "Enchantment Slots" line total
    private final Supplier<String> slotsLine;   // §H orb slots-line template ({TOTAL}/{ADDED}); null/blank → no line
    private final Supplier<String> heroicLine;  // §F HEROIC line template ({TYPE}/{+/-}/{AMOUNT}); blank → plain marker

    /**
     * Set members' authored lore, looked up from state at render time so a worn piece keeps its flavour lore
     * after it is enchanted (render-from-scratch). Wiring uses a {@code Library}-backed lookup; tests {@link #NONE}.
     */
    public interface SetLore {
        /** The lore shared by every armour piece of {@code setKey} (empty if none / unknown). */
        List<String> armor(String setKey);

        /** The weapon's own lore for {@code setKey} (empty if none / unknown). */
        List<String> weapon(String setKey);

        /** A lookup that renders no member lore at all. */
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

    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf, SetLore setLore) {
        this(style, displayNameOf, enchantColorOf, setLore, stack -> List.of());
    }

    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf, SetLore setLore,
            Function<ItemStack, List<String>> protectionLines) {
        this(style, displayNameOf, enchantColorOf, setLore, protectionLines, line -> false);
    }

    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf, SetLore setLore,
            Function<ItemStack, List<String>> protectionLines, Predicate<String> trakLine) {
        this(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLine,
                () -> null, () -> 0, () -> null);
    }

    /**
     * Canonical renderer: {@code style} is re-read per render so a {@code /se reload} takes effect next render;
     * {@code enchantColorOf} colours each enchant by rarity tier ({@code null}/blank → the style's default);
     * {@code protectionLines} contributes the applied-scroll PROTECTED lines from an item's marker state (empty
     * for an unprotected item), appended at the bottom of the body — above any trak count line; {@code trakLine}
     * marks an applied-trak count line so {@link #apply} re-renders the body but PRESERVES the trak lines that a
     * separate system owns (instead of clobbering them on every enchant change).
     *
     * <p>{@code countSuffix} is the §I transmog enchant-count name suffix template ({@link EnchantCountSuffix});
     * when present, {@link #apply} stamps the CUSTOM-enchant count onto the display name (and strips it at zero),
     * so the count is a fixed part of the name on any enchanted item. {@code baseSlots}/{@code slotsLine} render
     * the §H "Enchantment Slots" line once an orb has added slots — placed in the body, so it sits below the
     * enchant/set lines but above the protection + trak lines {@link #apply} appends after.
     */
    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf, SetLore setLore,
            Function<ItemStack, List<String>> protectionLines, Predicate<String> trakLine,
            Supplier<String> countSuffix, IntSupplier baseSlots, Supplier<String> slotsLine) {
        this(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLine,
                countSuffix, baseSlots, slotsLine, () -> null); // legacy-marker heroic line
    }

    /**
     * Full renderer including the §F heroic line template ({@code heroicLine}; {@code {TYPE}}/{@code {+/-}}/
     * {@code {AMOUNT}}, blank → the plain {@code &6&lHEROIC} marker). The heroic line renders in {@link #apply}
     * after the body — below the orb slots line, above the protection + trak lines — since it needs the item's
     * material for {@code {TYPE}} which the pure {@link #lines} does not see.
     */
    public LoreRenderer(Supplier<LoreStyle> style, Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf, SetLore setLore,
            Function<ItemStack, List<String>> protectionLines, Predicate<String> trakLine,
            Supplier<String> countSuffix, IntSupplier baseSlots, Supplier<String> slotsLine,
            Supplier<String> heroicLine) {
        this.style = Objects.requireNonNull(style, "style");
        this.displayNameOf = Objects.requireNonNull(displayNameOf, "displayNameOf");
        this.enchantColorOf = Objects.requireNonNull(enchantColorOf, "enchantColorOf");
        this.setLore = Objects.requireNonNull(setLore, "setLore");
        this.protectionLines = Objects.requireNonNull(protectionLines, "protectionLines");
        this.trakLine = Objects.requireNonNull(trakLine, "trakLine");
        this.countSuffix = Objects.requireNonNull(countSuffix, "countSuffix");
        this.baseSlots = Objects.requireNonNull(baseSlots, "baseSlots");
        this.slotsLine = Objects.requireNonNull(slotsLine, "slotsLine");
        this.heroicLine = Objects.requireNonNull(heroicLine, "heroicLine");
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
            // A BLANK level-color makes the level numeral inherit the name's (tier) colour (§L config option);
            // otherwise the numeral uses its own fixed colour.
            String levelColor = style.levelColor().isBlank() ? color : style.levelColor();
            out.add(Colors.translate(color + name + " " + levelColor + level));
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
        // NB: the §F heroic line is NOT emitted here — it needs the item material for {TYPE}, so apply() adds it
        // after the body (below the orb slots line, above protection/trak). lines() stays pure + server-free.
        if (state.setKey() != null) {
            // Armour member: the set's shared armour lore (§6.6). No auto "(Set)" marker — the authored
            // lore carries the SET BONUS block that names the set.
            for (String line : setLore.armor(state.setKey())) {
                out.add(Colors.translate(line));
            }
        }
        if (state.setWeaponKey() != null) {
            // Weapon member: the set weapon's own authored lore (§6.6). No auto "(Set Weapon)" marker.
            for (String line : setLore.weapon(state.setWeaponKey())) {
                out.add(Colors.translate(line));
            }
        }
        // §H slot-expander feedback: shown only once an orb has ADDED slots. Emitted last in the body so it sits
        // below the enchant/set lines but (since apply() appends protection + traks AFTER lines()) above those.
        if (state.added() > 0) {
            String template = slotsLine.get();
            if (template != null && !template.isBlank()) {
                int total = baseSlots.getAsInt() + state.added();
                out.add(Colors.translate(template
                        .replace("{TOTAL}", Integer.toString(total))
                        .replace("{ADDED}", Integer.toString(state.added()))));
            }
        }
        return out;
    }

    /** Render onto {@code stack} in place; clears lore when state is empty. False if the item can't carry meta (air). */
    @SuppressWarnings("deprecation") // get/setLore(List<String>): deprecated-not-removed across the whole range.
    public boolean apply(ItemStack stack, CombatState state) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        // Preserve any applied-trak count lines: they are NOT part of CombatState (a separate system stamps
        // them), so re-rendering the combat body must keep them rather than wipe them on every enchant change.
        List<String> preservedTraks = new ArrayList<>();
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (trakLine.test(line)) {
                    preservedTraks.add(line);
                }
            }
        }
        List<String> lore = lines(state);
        // §F heroic line: rendered here (not in pure lines()) because {TYPE} needs the item material. Sits below
        // the body's orb slots line and above the protection + trak lines appended next.
        String heroic = heroicBodyLine(state.heroic(), kindOf(stack.getType()), heroicLine.get());
        if (heroic != null) {
            lore.add(heroic);
        }
        lore.addAll(protectionLines.apply(stack)); // applied-scroll PROTECTED lines, from marker state (§4.2)
        lore.addAll(preservedTraks);                // re-stack the trak lines below the body + protection
        meta.setLore(lore.isEmpty() ? null : lore);
        stampCountSuffix(stack, meta, state.enchants().size());
        stack.setItemMeta(meta);
        return true;
    }

    /**
     * §I stamp the CUSTOM-enchant count onto the display name (the transmog suffix) so it is a fixed part of the
     * name on any enchanted item — rebuilt from state here, so adding/removing an enchant refreshes it and a
     * drop to ZERO custom enchants strips it. Vanilla enchants never count (the count is {@code state.enchants}
     * only). An item with no custom name DERIVES a readable name from its material so its identity survives
     * ("Diamond Sword [3]"); at zero enchants a previously-derived name reverts to the bare vanilla item.
     */
    @SuppressWarnings("deprecation") // get/setDisplayName: the floor-stable item-meta path (matches #apply)
    private void stampCountSuffix(ItemStack stack, ItemMeta meta, int count) {
        String template = countSuffix.get();
        if (template == null || template.isBlank()) {
            return; // suffix feature off (the no-suffix test/fixture ctors)
        }
        String current = meta.hasDisplayName() ? meta.getDisplayName() : null;
        String readable = readableName(stack.getType());
        if (count > 0) {
            String base = current != null ? EnchantCountSuffix.strip(current, template) : readable;
            meta.setDisplayName(EnchantCountSuffix.nameFor(base, template, count));
        } else if (current != null) {
            String base = EnchantCountSuffix.strip(current, template);
            // revert a name we previously DERIVED back to the vanilla item; keep a genuine custom name.
            meta.setDisplayName(base.isEmpty() || base.equals(readable) ? null : base);
        }
    }

    /**
     * The on-item HEROIC body line for {@code stat}, or {@code null} when the item is not heroic. A non-zero
     * {@code percentDamage} marks a WEAPON (rendered as {@code +N% DMG} outgoing); otherwise ARMOR
     * ({@code -N% DMG} incoming reduction). {@code kind} fills {@code {TYPE}} (e.g. {@code SWORD}, {@code BOOTS}).
     * A blank {@code template} falls back to the plain {@code &6&lHEROIC} marker. Pure — unit-tested.
     */
    static String heroicBodyLine(HeroicStat stat, String kind, String template) {
        if (stat == null || stat.isZero()) {
            return null;
        }
        if (template == null || template.isBlank()) {
            return Colors.translate("&6&lHEROIC"); // legacy marker (fixtures / unconfigured)
        }
        boolean weapon = stat.percentDamage() > 0.0;
        double pct = weapon ? stat.percentDamage() : stat.percentReduction();
        int amount = (int) Math.round(pct * 100.0);
        return Colors.translate(template
                .replace("{TYPE}", kind == null ? "" : kind)
                .replace("{+/-}", weapon ? "+" : "-")
                .replace("{AMOUNT}", Integer.toString(amount)));
    }

    /** The gear kind label from a material — the last underscore segment ({@code DIAMOND_SWORD → "SWORD"}). */
    private static String kindOf(Material material) {
        String name = material.name();
        int underscore = name.lastIndexOf('_');
        return underscore >= 0 ? name.substring(underscore + 1) : name;
    }

    /** A readable display name derived from a material ({@code DIAMOND_SWORD → "Diamond Sword"}). */
    private static String readableName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private String nameOr(String key, LoreStyle style) {
        String display = displayNameOf.apply(key);
        return display != null ? display : style.unknownLabel();
    }
}
