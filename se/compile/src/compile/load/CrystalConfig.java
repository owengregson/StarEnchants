package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + apply mechanics of the physical CRYSTAL item and its EXTRACTOR
 * (docs/v3-directives.md §E), loaded from the top-level {@code items/crystal.yml}. Immutable; lives in the
 * {@link ItemsConfig} snapshot the runtime reads and {@code /se reload} swaps. A crystal is its own item
 * (distinct from the book/scroll/dust carrier economy): drag-applied to gear with a {@link #successChance}
 * roll (optionally {@link #consumeOnFail}), two crystals merge into a multi-crystal, and a crystal
 * EXTRACTOR pops a crystal back off gear as a whole item.
 *
 * <p>Per-item crystal SLOT capacity is intentionally NOT here — it is a cross-cutting knob that belongs
 * in the master {@code config.yml} {@code crystals:} section (§L); until that lands the runtime injects a
 * default. {@code {CRYSTAL}} in the name/lore/messages renders the component crystal display name(s).
 *
 * @param material          material token (resolved cross-version at use)
 * @param successChance     drag-apply success chance, clamped 0..100
 * @param extractorMaterial extractor material token (resolved cross-version at use)
 *
 * <p>The apply/merge/extract messages live in {@code lang.yml} ({@code crystal.apply-success} /
 * {@code crystal.apply-fail} / {@code crystal.no-slots} / {@code crystal.merge} / {@code crystal.extract-success})
 * — §L centralised them.
 */
public record CrystalConfig(
        String material,
        String name,
        List<String> lore,
        int successChance,
        boolean consumeOnFail,
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
        successChance = Math.max(0, Math.min(100, successChance));
        Objects.requireNonNull(soundApply, "soundApply");
        Objects.requireNonNull(soundRemove, "soundRemove");
        Objects.requireNonNull(extractorMaterial, "extractorMaterial");
        Objects.requireNonNull(extractorName, "extractorName");
        extractorLore = List.copyOf(extractorLore);
    }

    /** The built-in crystal item used when {@code items/crystal.yml} is absent or omits fields. */
    public static CrystalConfig defaults() {
        return new CrystalConfig(
                "AMETHYST_SHARD",
                "&d{CRYSTAL} Crystal",
                List.of("&7Drag onto gear to apply.", "&7Merge two crystals into a multi-crystal."),
                75,
                true,
                true,
                "block.amethyst_block.chime",
                "block.amethyst_cluster.break",
                "AMETHYST_CLUSTER",
                "&dCrystal Extractor",
                List.of("&7Drag onto crystal-bearing gear", "&7to extract its last crystal."));
    }
}
