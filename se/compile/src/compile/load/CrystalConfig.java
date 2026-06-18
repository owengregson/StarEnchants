package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + apply mechanics of the physical CRYSTAL item (docs/v3-directives.md §E),
 * loaded from the top-level {@code items/crystal.yml}. Immutable; lives in the {@link ItemsConfig}
 * snapshot the runtime reads and {@code /se reload} swaps. A crystal is its own item (distinct from the
 * book/scroll/dust carrier economy): drag-applied to gear with a {@link #successChance} roll (optionally
 * {@link #consumeOnFail}), and two crystals merge into a multi-crystal.
 *
 * <p>Per-item crystal SLOT capacity is intentionally NOT here — it is a cross-cutting knob that belongs
 * in the master {@code config.yml} {@code crystals:} section (§L); until that lands the runtime injects a
 * default. Particles and sounds are cosmetic follow-ups (as with {@link SoulGemConfig}). {@code {CRYSTAL}}
 * in the name/lore/messages renders the component crystal display name(s).
 *
 * @param material            the crystal item's material token (resolved cross-version at use)
 * @param name                its display name ({@code &} colours; {@code {CRYSTAL}} placeholder)
 * @param lore                its lore lines ({@code {CRYSTAL}} placeholder)
 * @param successChance       drag-apply success chance, 0..100
 * @param consumeOnFail       whether a failed apply still consumes the crystal
 *
 * <p>The apply/merge messages now live in {@code lang.yml} ({@code crystal.apply-success} /
 * {@code crystal.apply-fail} / {@code crystal.no-slots} / {@code crystal.merge}) — §L centralised them.
 */
public record CrystalConfig(
        String material,
        String name,
        List<String> lore,
        int successChance,
        boolean consumeOnFail) {

    public CrystalConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        successChance = Math.max(0, Math.min(100, successChance));
    }

    /** The built-in crystal item used when {@code items/crystal.yml} is absent or omits fields. */
    public static CrystalConfig defaults() {
        return new CrystalConfig(
                "AMETHYST_SHARD",
                "&d{CRYSTAL} Crystal",
                List.of("&7Drag onto gear to apply.", "&7Merge two crystals into a multi-crystal."),
                75,
                true);
    }
}
