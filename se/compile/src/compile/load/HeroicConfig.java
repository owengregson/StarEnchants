package compile.load;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The configurable heroic upgrade item + apply mechanics (docs/v3-directives.md §F; ADR-0021), loaded
 * from the top-level {@code items/heroic.yml}. Heroic is NOT set-bound — the upgrade applies to any
 * armour or weapon. On a (small, randomised) success it stamps the granted heroic percents onto the
 * piece, optionally swaps its material to a configured upgrade, and the "heroic piece" lore marker is
 * rendered from state; on failure it consumes the upgrade and never harms the gear.
 *
 * @param material         the upgrade item's material token (resolved cross-version at use)
 * @param name             the upgrade item's display name ({@code &} colours)
 * @param lore             the upgrade item's lore lines
 * @param successMin       low end of the per-attempt success-chance range, 0..100
 * @param successMax       high end of the per-attempt success-chance range, 0..100
 * @param percentDamage    heroic outgoing-damage fraction granted on success (e.g. {@code 0.10})
 * @param percentReduction heroic damage-reduction fraction granted on success
 * @param durability       heroic item-damage-cancel probability granted on success ({@code 0..1})
 * @param materialUpgrades input-material token → upgraded-material token (within-category, e.g. DIAMOND→NETHERITE)
 * @param reductionScope   {@code ENTITY} (PvP/entity only, default) or {@code ALL} (all damage causes)
 * @param messageSuccess   chat on a successful upgrade
 * @param messageFail      chat on a failed upgrade
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
        String messageSuccess,
        String messageFail) {

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

    /** The upgraded material token for {@code input}, or {@code null} if this material is not remapped. */
    public String upgradeFor(String input) {
        return input == null ? null : materialUpgrades.get(input.toUpperCase(Locale.ROOT));
    }

    /** The built-in heroic config used when {@code items/heroic.yml} is absent or omits fields. */
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
                "ENTITY",
                "&6Heroic upgrade succeeded! &7Your gear is now &6heroic&7.",
                "&cThe heroic upgrade failed — the upgrade was consumed.");
    }
}
