package compile.load;

import java.util.List;
import java.util.Objects;

/** Internal grouping of the SCROLL family (§I), which share item-data machinery; each member is authored in its own {@code items/} file. */
public record ScrollsConfig(Black black, Randomizer randomizer, Transmog transmog, Holy holy, Nametag nametag,
                            Godly godly) {

    public ScrollsConfig {
        Objects.requireNonNull(black, "black");
        Objects.requireNonNull(randomizer, "randomizer");
        Objects.requireNonNull(transmog, "transmog");
        Objects.requireNonNull(holy, "holy");
        Objects.requireNonNull(nametag, "nametag");
        Objects.requireNonNull(godly, "godly");
    }

    /**
     * Extracts one random enchant from gear into a book — the extraction ALWAYS succeeds (§I). What varies is
     * the drawn book's CONVERSION success rate: a value rolled in {@code [minConvert, maxConvert]} when the
     * scroll is minted (clamped to the global {@code books.max-success} ceiling) and stamped on the scroll so
     * its lore can show it. The book the scroll draws off the gear carries that success chance.
     */
    public record Black(String material, String name, List<String> lore, int minConvert, int maxConvert) {
        public Black {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            int lo = Math.max(0, Math.min(100, minConvert));
            int hi = Math.max(0, Math.min(100, maxConvert));
            minConvert = Math.min(lo, hi); // order the pair so [min, max] is always a valid range
            maxConvert = Math.max(lo, hi);
        }
    }

    /** Rerolls a book's success chance to a random value in {@code [minPercent, maxPercent]}. */
    public record Randomizer(String material, String name, List<String> lore, int minPercent, int maxPercent) {
        public Randomizer {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            int lo = Math.max(0, Math.min(100, minPercent));
            int hi = Math.max(0, Math.min(100, maxPercent));
            minPercent = Math.min(lo, hi);
            maxPercent = Math.max(lo, hi);
        }
    }

    /**
     * ORGANISES an item's enchant lore by rarity-tier weight (cosmetic — combat is order-independent) and
     * stamps the enchant count into the name via {@code nameSuffix}, whose {@code {COUNT}} placeholder is the
     * count. Re-applying replaces the prior count suffix rather than stacking it.
     */
    public record Transmog(String material, String name, List<String> lore, String nameSuffix) {
        public Transmog {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(nameSuffix, "nameSuffix");
            lore = List.copyOf(lore);
        }
    }

    /**
     * APPLIED to a piece of gear (§I): on a successful apply roll it stamps a one-shot keep-on-death marker on
     * that item (occupying the exclusive applied-slot); on the owner's death the marked item is kept and the
     * marker consumed. The apply rolls a success in {@code [minSuccess, maxSuccess]} — a failed roll spends the
     * scroll without protecting (it never destroys gear). {@code 100/100} (the default) always succeeds.
     */
    public record Holy(String material, String name, List<String> lore, int minSuccess, int maxSuccess,
                       String protectedLine) {
        public Holy {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(protectedLine, "protectedLine");
            lore = List.copyOf(lore);
            int lo = Math.max(0, Math.min(100, minSuccess));
            int hi = Math.max(0, Math.min(100, maxSuccess));
            minSuccess = Math.min(lo, hi);
            maxSuccess = Math.max(lo, hi);
        }
    }

    /** Renames gear via chat; rejected if {@code blacklist} entries (matched case-insensitively as substrings of the colour-stripped name) appear. */
    public record Nametag(String material, String name, List<String> lore, List<String> blacklist) {
        public Nametag {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            blacklist = List.copyOf(blacklist);
        }
    }

    /** Opens the enchant-reorder GUI (§K) on a clicked piece — a reusable tool, not a one-shot scroll (its own {@code GodlyTransmogCodec}, off the consume path). */
    public record Godly(String material, String name, List<String> lore) {
        public Godly {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
        }
    }

    public static ScrollsConfig defaults() {
        return new ScrollsConfig(
                new Black(
                        "INK_SAC",
                        "&8Black Scroll",
                        List.of("&7Drag onto enchanted gear to", "&7extract one enchant into a book."),
                        50,
                        100),
                new Randomizer(
                        "SUGAR",
                        "&eRandomizer Scroll",
                        List.of("&7Drag onto an enchant book to", "&7reroll its success chance."),
                        25,
                        100),
                new Transmog(
                        "PAPER",
                        "&c&lTransmog Scroll",
                        List.of("&eOrganizes enchants by &f&nrarity&r&e on your item and adds the &denchant &bcount &eto the name.",
                                "&7Drag n' Drop on an item to apply."),
                        "&r &d[&b&l&n{COUNT}&r&d]"),
                new Holy(
                        "TOTEM_OF_UNDYING",
                        "&fHoly White Scroll",
                        List.of("&7Drag onto an item to keep", "&7it when you die (one use)."),
                        100,
                        100,
                        "&e&l*&f&lHOLY&e&l* &f&lPROTECTED"),
                new Nametag(
                        "NAME_TAG",
                        "&bItem Nametag",
                        List.of("&7Drag onto gear, then type the", "&7new name in chat."),
                        List.of()),
                new Godly(
                        "NETHER_STAR",
                        "&5Godly Transmog",
                        List.of("&7Drag onto enchanted gear to", "&7reorder its enchants by hand.")));
    }
}
