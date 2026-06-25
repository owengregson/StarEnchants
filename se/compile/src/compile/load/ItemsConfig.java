package compile.load;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import schema.diag.Diagnostic;

/**
 * The compiled snapshot of the top-level {@code items/} config folder (docs/v3-directives.md §L) — the
 * configurable likeness of the interactable items, loaded as a parallel immutable reference to the content
 * {@link Library} and swapped in the SAME atomic {@code /se reload} transaction. Pure (no Bukkit); readers
 * always see a fully-built snapshot. Each entry is empty when its file is absent, falling back to the named
 * {@code defaults()}; the carried diagnostics flow through {@code /se reload --dry-run} like content faults.
 *
 * @param soulGem     soul-gem config; falls back to {@link SoulGemConfig#defaults()}
 * @param crystal     crystal-item config; falls back to {@link CrystalConfig#defaults()}
 * @param heroic      heroic upgrade config; falls back to {@link HeroicConfig#defaults()}
 * @param slots       slot expander/gem config; falls back to {@link SlotConfig#defaults()}
 * @param scrolls     scroll-family config; falls back to {@link ScrollsConfig#defaults()}
 * @param unopenedBook unopened/randomized book config; falls back to {@link UnopenedBookConfig#defaults()}
 * @param enchantBook general enchant-book likeness; falls back to {@link EnchantBookConfig#defaults()}
 * @param dust        success-dust config; falls back to {@link DustConfig#defaults()}
 * @param whiteScroll white-scroll (enchant-protect) config; falls back to {@link WhiteScrollConfig#defaults()}
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

    /** An empty config (no item files present) — every accessor falls back to its {@code defaults()}. */
    public static ItemsConfig empty() {
        return new ItemsConfig(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of());
    }

    public SoulGemConfig soulGemOrDefault() {
        return soulGem.orElseGet(SoulGemConfig::defaults);
    }

    public CrystalConfig crystalOrDefault() {
        return crystal.orElseGet(CrystalConfig::defaults);
    }

    public HeroicConfig heroicOrDefault() {
        return heroic.orElseGet(HeroicConfig::defaults);
    }

    public SlotConfig slotsOrDefault() {
        return slots.orElseGet(SlotConfig::defaults);
    }

    public ScrollsConfig scrollsOrDefault() {
        return scrolls.orElseGet(ScrollsConfig::defaults);
    }

    public UnopenedBookConfig unopenedBookOrDefault() {
        return unopenedBook.orElseGet(UnopenedBookConfig::defaults);
    }

    public EnchantBookConfig enchantBookOrDefault() {
        return enchantBook.orElseGet(EnchantBookConfig::defaults);
    }

    public DustConfig dustOrDefault() {
        return dust.orElseGet(DustConfig::defaults);
    }

    public WhiteScrollConfig whiteScrollOrDefault() {
        return whiteScroll.orElseGet(WhiteScrollConfig::defaults);
    }

    /** Whether any blocking diagnostic was raised loading the folder. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }
}
