package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The CRYSTAL item and its EXTRACTOR (§E, ADR-0032), loaded from {@code items/crystal.yml} — the ONE global
 * crystal likeness. A per-crystal file no longer carries its own material/name/lore; every minted crystal
 * (single or merged) takes this shared likeness and fills its tokens from the component crystal(s):
 *
 * <ul>
 *   <li>{@code {CRYSTAL}} — the component display name(s), styled, comma-joined (§E). A single is just its own
 *       display name; a merge lists each, each carrying its own colour.
 *   <li>{@code {DESCRIPTION}} — each component's authored description block, stacked with a blank line between
 *       blocks. A LINE-EXPANDING token (one template line becomes many), unlike the simple replacements.
 *   <li>{@code {KINDS}} — the item kinds the crystal applies to (the intersection for a merge).
 * </ul>
 *
 * <p>{@code loreWhileOnItem} is the single line shown on crystal-bearing GEAR (§E), rendered from the same
 * {@code {CRYSTAL}} token — for the cosmic pack it is identical to the item name ({@code Armor Crystal (Flame)}).
 * Application is unconditional (ADR-0032): there is NO success roll here, so a crystal always lands.
 *
 * <p>Per-item crystal SLOT capacity and the merge cap are cross-cutting knobs in {@code config.yml}'s
 * {@code crystals:} section (§L), not part of a single item's likeness.
 */
public record CrystalConfig(
        String material,
        String name,
        List<String> lore,
        String loreWhileOnItem,
        boolean sounds,
        String soundApply,
        String soundRemove,
        String extractorMaterial,
        String extractorName,
        List<String> extractorLore) {

    public CrystalConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        Objects.requireNonNull(loreWhileOnItem, "loreWhileOnItem");
        Objects.requireNonNull(soundApply, "soundApply");
        Objects.requireNonNull(soundRemove, "soundRemove");
        Objects.requireNonNull(extractorMaterial, "extractorMaterial");
        Objects.requireNonNull(extractorName, "extractorName");
        extractorLore = List.copyOf(extractorLore);
    }

    public static CrystalConfig defaults() {
        return new CrystalConfig(
                "AMETHYST_SHARD",
                "&d{CRYSTAL} &7Crystal",
                List.of(
                        "&7Imbues a piece of gear with crystal magic.",
                        "&7Merge with other crystals to combine their effects.",
                        "",
                        "{DESCRIPTION}",
                        "",
                        "&7Applies to: &f&n{KINDS}",
                        "&7Drag n' Drop on an item to apply."),
                "&d{CRYSTAL} &7Crystal",
                true,
                "block.amethyst_block.chime",
                "block.amethyst_cluster.break",
                "AMETHYST_CLUSTER",
                "&dCrystal Extractor",
                List.of("&7Drag onto a crystal-bearing item or a", "&7multi-crystal to pop its topmost crystal."));
    }
}
