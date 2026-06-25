package compile.load;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import schema.diag.Diagnostic;

/**
 * Compiled snapshot of the {@code items/} folder (§L), swapped in the SAME atomic {@code /se reload}
 * transaction as the content {@link Library}. Each entry is empty when its file is absent (use {@code xOrDefault()});
 * carried diagnostics flow through {@code /se reload --dry-run} like content faults.
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

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }
}
