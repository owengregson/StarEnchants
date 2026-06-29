package feature.menu;

import compile.load.ContentHolder;
import compile.load.TierRegistry;
import feature.book.UnopenedBookService;
import feature.carrier.CarrierService;
import feature.crystal.CrystalService;
import feature.heroic.HeroicService;
import feature.scroll.HolyScrollService;
import feature.scroll.NametagService;
import feature.scroll.ScrollService;
import feature.slot.SlotService;
import feature.soul.SoulService;
import feature.trak.TrakService;
import item.codec.TrakCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * The data-driven source of the operator {@link MintMenu}'s tiles (ADR-0030): one {@link Entry} per mintable
 * item, each a thunk that mints a fresh stack. Kept as suppliers (not pre-built stacks) so every open mints
 * against the live config, and so a gated subsystem whose mint throws is simply skipped at render rather than
 * breaking the menu. Driving the trak gems off {@link TrakCodec.Kind} and the unopened books off the live tier
 * list means a new trak kind or rarity tier is a structurally-present row, not a forgotten one.
 */
public final class MintCatalog {

    /** One mintable item: the label is for the give-confirmation message; the thunk mints a fresh stack. */
    public record Entry(String label, Supplier<ItemStack> mint) {
        public Entry {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(mint, "mint");
        }
    }

    private final ContentHolder content;
    private final SoulService souls;
    private final SlotService slots;
    private final HeroicService heroics;
    private final CrystalService crystals;
    private final ScrollService scrolls;
    private final HolyScrollService holyScrolls;
    private final NametagService nametags;
    private final CarrierService carriers;
    private final TrakService traks;
    private final UnopenedBookService unopenedBooks;

    public MintCatalog(ContentHolder content, SoulService souls, SlotService slots, HeroicService heroics,
                       CrystalService crystals, ScrollService scrolls, HolyScrollService holyScrolls,
                       NametagService nametags, CarrierService carriers, TrakService traks,
                       UnopenedBookService unopenedBooks) {
        this.content = Objects.requireNonNull(content, "content");
        this.souls = souls;
        this.slots = slots;
        this.heroics = heroics;
        this.crystals = crystals;
        this.scrolls = scrolls;
        this.holyScrolls = holyScrolls;
        this.nametags = nametags;
        this.carriers = carriers;
        this.traks = traks;
        this.unopenedBooks = unopenedBooks;
    }

    /** Every mintable item, in a curated order (core economy → traks → one unopened book per rarity tier). */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry("soul gem", souls::mintGem));
        out.add(new Entry("slot expander", slots::mintOrb));
        out.add(new Entry("heroic upgrade", heroics::mint));
        out.add(new Entry("crystal extractor", crystals::mintExtractor));
        out.add(new Entry("black scroll", scrolls::mintBlack));
        out.add(new Entry("randomizer scroll", scrolls::mintRandomizer));
        out.add(new Entry("transmog scroll", scrolls::mintTransmog));
        out.add(new Entry("godly transmog", scrolls::mintGodlyTransmog));
        out.add(new Entry("holy white scroll", holyScrolls::mint));
        out.add(new Entry("item nametag", nametags::mint));
        out.add(new Entry("success dust", carriers::mintDust));
        out.add(new Entry("white scroll", carriers::mintWhiteScroll));
        for (TrakCodec.Kind kind : TrakCodec.Kind.values()) {
            out.add(new Entry(kind.name().toLowerCase(java.util.Locale.ROOT) + "trak gem", () -> traks.mint(kind)));
        }
        for (TierRegistry.Tier tier : content.library().tiers().tiers()) {
            String name = tier.name();
            out.add(new Entry(name + " unopened book", () -> unopenedBooks.mint(name)));
        }
        return out;
    }
}
