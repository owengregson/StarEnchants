package compile.load;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The heroic upgrade item (§F; ADR-0021), loaded from {@code items/heroic.yml}. NOT set-bound — applies to any
 * armour or weapon; on failure it consumes the upgrade and never harms the gear.
 *
 * @param percentDamage    outgoing-damage fraction granted on success (e.g. {@code 0.10})
 * @param durability       item-damage-cancel probability granted on success ({@code 0..1})
 * @param materialUpgrades input → upgraded material, within-category (e.g. DIAMOND→NETHERITE)
 * @param reductionScope   {@code ENTITY} (PvP/entity only, default) or {@code ALL} (all damage causes)
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
        String reductionScope) {

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
    }

    public String upgradeFor(String input) {
        return input == null ? null : materialUpgrades.get(input.toUpperCase(Locale.ROOT));
    }

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
                0.25,
                Map.ofEntries(
                        Map.entry("DIAMOND_HELMET", "NETHERITE_HELMET"),
                        Map.entry("DIAMOND_CHESTPLATE", "NETHERITE_CHESTPLATE"),
                        Map.entry("DIAMOND_LEGGINGS", "NETHERITE_LEGGINGS"),
                        Map.entry("DIAMOND_BOOTS", "NETHERITE_BOOTS"),
                        Map.entry("DIAMOND_SWORD", "NETHERITE_SWORD"),
                        Map.entry("DIAMOND_AXE", "NETHERITE_AXE")),
                "ENTITY");
    }
}
