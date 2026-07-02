package item.render;

import item.codec.CombatState;
import item.codec.HeroicStat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import platform.text.Colors;

/**
 * The one place lore COMPOSITION happens (ADR-0040): a deterministic, single-pass projection of item state
 * into the full ordered lore list. Every section — enchant body, set lore, orb slots line, crystal line(s),
 * heroic line, applied-scroll PROTECTED lines, trak count lines — is rendered here in a fixed order (the
 * order IS the contract), each from {@link CombatState} + the injected {@link LoreRenderer.Config} templates,
 * never parsed back from text (§4.2).
 *
 * <p>Pure and server-free: {@link #compose} takes the material kind and the already-computed protection + trak
 * lines (both rendered from marker/counter state by the caller) as plain values, so the whole composition is
 * unit-testable with hand-built state. {@link LoreRenderer} is the thin Bukkit shell that feeds it, writes the
 * result onto an item, and migrates pre-composer lore once via {@link LegacyLoreShim}.
 */
public final class LoreComposer {

    private final LoreRenderer.Config config;

    public LoreComposer(LoreRenderer.Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * The body sections that need only {@link CombatState}: one line per enchant ({@code name level}), the
     * set-member lore, the orb slots line, then one line per socketed crystal. Emitted in that order; the
     * heroic + protection + trak sections are appended by {@link #compose} (they need the material / marker
     * state the pure body does not see).
     */
    public List<String> body(CombatState state) {
        LoreStyle style = config.style().get(); // live style, once per render (reload-swappable)
        List<String> out = new ArrayList<>(state.enchants().size() + state.crystals().size());
        for (Map.Entry<String, Integer> enchant : state.enchants().entrySet()) {
            String name = nameOr(enchant.getKey(), style);
            String level = style.roman() ? Numerals.roman(enchant.getValue()) : Integer.toString(enchant.getValue());
            String tierColor = config.enchantColorOf().apply(enchant.getKey());   // per-tier colour (ADR-0016 §2)
            String color = tierColor != null && !tierColor.isBlank() ? tierColor : style.enchantColor();
            // A BLANK level-color makes the level numeral inherit the name's (tier) colour (§L config option);
            // otherwise the numeral uses its own fixed colour.
            String levelColor = style.levelColor().isBlank() ? color : style.levelColor();
            out.add(Colors.translate(color + name + " " + levelColor + level));
        }
        if (state.setKey() != null) {
            // Armour member: the set's shared armour lore (§6.6), word-wrapped to the universal item-wrap width
            // via wrapAll (NOT a per-line wrap loop) so the author's blank separator lines survive.
            for (String wrapped : TextWrap.wrapAll(config.setLore().armor(state.setKey()), item.mint.ItemFactory.itemWrapWidth())) {
                out.add(Colors.translate(wrapped));
            }
        }
        if (state.setWeaponKey() != null) {
            for (String wrapped : TextWrap.wrapAll(config.setLore().weapon(state.setWeaponKey()), item.mint.ItemFactory.itemWrapWidth())) {
                out.add(Colors.translate(wrapped));
            }
        }
        // §H orb slot-expander feedback: shown only once an orb has ADDED slots, below the enchant/set lines.
        if (state.added() > 0) {
            String template = config.slotsLine().get();
            if (template != null && !template.isBlank()) {
                int total = config.baseSlots().getAsInt() + state.added();
                out.add(Colors.translate(template
                        .replace("{TOTAL}", Integer.toString(total))
                        .replace("{ADDED}", Integer.toString(state.added()))));
            }
        }
        // §E crystal line(s): LAST in the body — below the orb slots line — so on gear they sit above only the
        // heroic + protection + trak lines compose() appends (ADR-0034 §5). One line per socketed entry.
        String crystalTemplate = config.crystalLine().get();
        String multiTemplate = config.crystalLineMulti().get();
        for (String crystalEntry : state.crystals()) {
            List<String> components = item.codec.CrystalItemData.componentsOf(crystalEntry);
            // A merged (2+ component) entry renders from the "Multi Crystal" template (ADR-0035); a single uses the plain one.
            String template = components.size() > 1 && multiTemplate != null && !multiTemplate.isBlank()
                    ? multiTemplate : crystalTemplate;
            if (template != null && !template.isBlank()) {
                out.add(Colors.translate(CrystalNames.render(template, components, key -> nameOr(key, style))));
            } else {
                // Fallback (no template wired, e.g. tests): the legacy crystal-colour + names joined by " + ".
                StringBuilder label = new StringBuilder();
                for (String component : components) {
                    if (label.length() > 0) {
                        label.append(" + ");
                    }
                    label.append(nameOr(component, style));
                }
                out.add(Colors.translate(style.crystalColor() + label));
            }
        }
        return out;
    }

    /**
     * The full ordered lore for a piece of gear, every section from state (ADR-0040): {@link #body} + the heroic
     * line ({@code kind} fills its {@code {TYPE}}) + {@code protection} (the applied-scroll PROTECTED lines) +
     * {@code traks} (the applied-trak count lines). {@code protection} and {@code traks} are rendered from marker
     * state by the caller — never parsed back from text (§4.2) — so re-theming a template can no longer break a
     * classifier. Pure: no Bukkit, so byte-for-byte testable against hand-built inputs.
     */
    public List<String> compose(CombatState state, String kind, List<String> protection, List<String> traks) {
        List<String> lore = body(state);
        String heroic = heroicBodyLine(state.heroic(), kind, config.heroicLine().get());
        if (heroic != null) {
            lore.add(heroic);
        }
        lore.addAll(protection); // applied-scroll PROTECTED lines, from marker state (§4.2)
        lore.addAll(traks);      // applied-trak count lines, from marker + counter state (§I)
        return lore;
    }

    /**
     * The on-item HEROIC body line for {@code stat}, or {@code null} when the item is not heroic. A non-zero
     * {@code percentDamage} marks a WEAPON (rendered as {@code +N% DMG} outgoing); otherwise ARMOR
     * ({@code -N% DMG} incoming reduction). {@code kind} fills {@code {TYPE}} (e.g. {@code SWORD}, {@code BOOTS}).
     * A blank {@code template} falls back to the plain {@code &6&lHEROIC} marker. Pure — unit-tested.
     */
    public static String heroicBodyLine(HeroicStat stat, String kind, String template) {
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

    private String nameOr(String key, LoreStyle style) {
        String display = config.displayNameOf().apply(key);
        return display != null ? display : style.unknownLabel();
    }
}
