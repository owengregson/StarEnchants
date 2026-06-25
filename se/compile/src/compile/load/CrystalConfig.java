package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The CRYSTAL item and its EXTRACTOR (§E), loaded from {@code items/crystal.yml}.
 *
 * <p>Per-item crystal SLOT capacity is intentionally NOT here — it is a cross-cutting knob in {@code config.yml}'s
 * {@code crystals:} section (§L); until that lands the runtime injects a default. {@code {CRYSTAL}} renders the component name(s).
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
