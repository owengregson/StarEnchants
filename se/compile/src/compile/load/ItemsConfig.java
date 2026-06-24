package compile.load;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import schema.diag.Diagnostic;

/**
 * The compiled snapshot of the top-level {@code items/} config folder (docs/v3-directives.md §L) — the
 * configurable likeness of the interactable items (soul gem, and later scrolls/dust/orb/nametag), loaded
 * as a parallel immutable reference to the content {@link Library} and swapped in the SAME atomic
 * {@code /se reload} transaction. Pure (no Bukkit); readers always see a fully-built snapshot.
 *
 * <p>Currently holds the soul-gem config; future item types add fields here. The carried diagnostics let
 * {@code /se reload --dry-run} report items-config faults through the same path as content faults.
 *
 * @param soulGem     the soul-gem config, or empty if none is configured (the runtime falls back to {@link SoulGemConfig#defaults()})
 * @param crystal     the crystal-item config, or empty if none is configured (falls back to {@link CrystalConfig#defaults()})
 * @param heroic      the heroic upgrade config, or empty if none is configured (falls back to {@link HeroicConfig#defaults()})
 * @param slots       the slot expander/gem config, or empty if none is configured (falls back to {@link SlotConfig#defaults()})
 * @param scrolls     the scroll-family config, or empty if none is configured (falls back to {@link ScrollsConfig#defaults()})
 * @param unopenedBook the unopened/randomized book config, or empty if none (falls back to {@link UnopenedBookConfig#defaults()})
 * @param enchantBook the general enchant-book likeness, or empty if none (falls back to {@link EnchantBookConfig#defaults()})
 * @param dust        the success-dust config, or empty if none is configured (falls back to {@link DustConfig#defaults()})
 * @param whiteScroll the white-scroll (enchant-protect) config, or empty if none (falls back to {@link WhiteScrollConfig#defaults()})
 * @param diagnostics every diagnostic raised loading the folder
 */
public record ItemsConfig(Optional<SoulGemConfig> soulGem, Optional<CrystalConfig> crystal,
                          Optional<HeroicConfig> heroic, Optional<SlotConfig> slots,
                          Optional<ScrollsConfig> scrolls, Optional<UnopenedBookConfig> unopenedBook,
                          Optional<EnchantBookConfig> enchantBook, Optional<DustConfig> dust,
                          Optional<WhiteScrollConfig> whiteScroll,
                          List<Diagnostic> diagnostics) {

    public ItemsConfig {
        Objects.requireNonNull(soulGem, "soulGem");
        Objects.requireNonNull(crystal, "crystal");
        Objects.requireNonNull(heroic, "heroic");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(scrolls, "scrolls");
        Objects.requireNonNull(unopenedBook, "unopenedBook");
        Objects.requireNonNull(enchantBook, "enchantBook");
        Objects.requireNonNull(dust, "dust");
        Objects.requireNonNull(whiteScroll, "whiteScroll");
        diagnostics = List.copyOf(diagnostics);
    }

    /** An empty config (no item files present) — the runtime uses each item's built-in defaults. */
    public static ItemsConfig empty() {
        return new ItemsConfig(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of());
    }

    /** The soul-gem config, or its built-in default when none is configured. */
    public SoulGemConfig soulGemOrDefault() {
        return soulGem.orElseGet(SoulGemConfig::defaults);
    }

    /** The crystal-item config, or its built-in default when none is configured. */
    public CrystalConfig crystalOrDefault() {
        return crystal.orElseGet(CrystalConfig::defaults);
    }

    /** The heroic upgrade config, or its built-in default when none is configured. */
    public HeroicConfig heroicOrDefault() {
        return heroic.orElseGet(HeroicConfig::defaults);
    }

    /** The slot expander/gem config, or its built-in default when none is configured. */
    public SlotConfig slotsOrDefault() {
        return slots.orElseGet(SlotConfig::defaults);
    }

    /** The scroll-family config, or its built-in default when none is configured. */
    public ScrollsConfig scrollsOrDefault() {
        return scrolls.orElseGet(ScrollsConfig::defaults);
    }

    /** The unopened/randomized book config, or its built-in default when none is configured. */
    public UnopenedBookConfig unopenedBookOrDefault() {
        return unopenedBook.orElseGet(UnopenedBookConfig::defaults);
    }

    /** The general enchant-book likeness, or its built-in default when none is configured. */
    public EnchantBookConfig enchantBookOrDefault() {
        return enchantBook.orElseGet(EnchantBookConfig::defaults);
    }

    /** The success-dust config, or its built-in default when none is configured. */
    public DustConfig dustOrDefault() {
        return dust.orElseGet(DustConfig::defaults);
    }

    /** The white-scroll (enchant-protect) config, or its built-in default when none is configured. */
    public WhiteScrollConfig whiteScrollOrDefault() {
        return whiteScroll.orElseGet(WhiteScrollConfig::defaults);
    }

    /** Whether any blocking diagnostic was raised loading the folder. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }
}
