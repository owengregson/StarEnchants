package compile.load;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The heroic upgrade item (§F; ADR-0021), loaded from {@code items/heroic.yml}. NOT set-bound — applies to any
 * armour or weapon; on failure it consumes the upgrade and never harms the gear.
 *
 * @param percentDamage    outgoing-damage fraction granted to a WEAPON on success (e.g. {@code 0.10})
 * @param percentReduction incoming-damage reduction fraction granted to ARMOR on success (e.g. {@code 0.10})
 * @param durability       item-damage-cancel probability granted on success ({@code 0..1}); {@code 0.20} ≈ the
 *                         piece takes 80% of normal durability damage
 * @param materialUpgrades input → upgraded material, within-category (e.g. DIAMOND→GOLDEN for the display piece)
 * @param reductionScope   {@code ENTITY} (PvP/entity only, default) or {@code ALL} (all damage causes)
 * @param loreLine         the on-item HEROIC lore-line template ({@code {TYPE}} = the gear kind; the renderer
 *                         also recognises {@code {+/-}}/{@code {AMOUNT}} for an optional signed-percent readout);
 *                         blank → the plain {@code &6&lHEROIC} marker
 * @param destroyOnFail    whether a failed attempt destroys the targeted gear (like other consumables); the
 *                         upgrade itself is always consumed
 * @param diamondStats     whether, on success, the (display-swapped) piece is re-statted to diamond-equivalent
 *                         attack/armour/toughness + durability — so a gold display piece still functions as
 *                         diamond (best-effort cross-version; see the heroic gear-forge overlay)
 * @param vanillaStats     HOW diamond-equivalence is applied to ARMOUR when {@code diamondStats} is on. {@code true}
 *                         (default) writes REAL vanilla armour-point/toughness attribute modifiers and a custom max
 *                         durability where the platform supports it, so the values are correct on the HUD and read
 *                         by other combat plugins that recompute from vanilla armour/durability (e.g. Mental's 1.8
 *                         restore — ADR-0031); {@code false} keeps the plugin's own combat-maths approximation
 *                         (ADR-0021, invisible to vanilla attributes). Armour-only; the weapon outgoing stays
 *                         plugin-maths either way. On a platform without the modern attribute/durability API
 *                         (the 1.8 fork) this silently falls back to the plugin-maths approximation.
 */
public record HeroicConfig(
        String material,
        String name,
        List<String> lore,
        int successMin,
        int successMax,
        double percentDamage,
        double percentReduction,
        double durability,
        Map<String, String> materialUpgrades,
        String reductionScope,
        String loreLine,
        boolean destroyOnFail,
        boolean diamondStats,
        boolean vanillaStats) {

    public HeroicConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        materialUpgrades = Map.copyOf(materialUpgrades);
        int lo = Math.max(0, Math.min(100, successMin));
        int hi = Math.max(0, Math.min(100, successMax));
        successMin = Math.min(lo, hi);
        successMax = Math.max(lo, hi);
        reductionScope = reductionScope == null ? "ENTITY" : reductionScope.toUpperCase(Locale.ROOT);
        loreLine = loreLine == null ? "" : loreLine;
    }

    public String upgradeFor(String input) {
        return input == null ? null : materialUpgrades.get(input.toUpperCase(Locale.ROOT));
    }

    /** The shipped HEROIC lore-line template; {@code {TYPE}} is the gear kind. */
    public static final String DEFAULT_LORE_LINE =
            "&e&k|||&r &6&lHEROIC {TYPE} &r&e&k|||&r";

    public static HeroicConfig defaults() {
        return new HeroicConfig(
                "NETHERITE_SCRAP",
                "&6&lHeroic Upgrade",
                List.of("&7Drag onto armour or a weapon to attempt a heroic upgrade.",
                        "&7Small success chance — consumed on use."),
                5,
                15,
                0.10,
                0.10,
                0.20,
                Map.ofEntries(
                        Map.entry("DIAMOND_HELMET", "NETHERITE_HELMET"),
                        Map.entry("DIAMOND_CHESTPLATE", "NETHERITE_CHESTPLATE"),
                        Map.entry("DIAMOND_LEGGINGS", "NETHERITE_LEGGINGS"),
                        Map.entry("DIAMOND_BOOTS", "NETHERITE_BOOTS"),
                        Map.entry("DIAMOND_SWORD", "NETHERITE_SWORD"),
                        Map.entry("DIAMOND_AXE", "NETHERITE_AXE")),
                "ENTITY",
                DEFAULT_LORE_LINE,
                false,
                false,
                true);
    }
}
