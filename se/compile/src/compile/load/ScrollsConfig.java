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

    /** Extracts one random enchant from gear into a book on a {@code successChance} roll; spent (extracting nothing) on failure. */
    public record Black(String material, String name, List<String> lore, int successChance) {
        public Black {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            successChance = Math.max(0, Math.min(100, successChance));
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

    /** Reorders an item's enchant lore (cosmetic — combat is order-independent) and appends {@code nameSuffix} to the name. */
    public record Transmog(String material, String name, List<String> lore, String nameSuffix) {
        public Transmog {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(nameSuffix, "nameSuffix");
            lore = List.copyOf(lore);
        }
    }

    /**
     * Held in inventory (incl. off-hand); on death, a {@code saveChance} roll keeps items + levels, consuming the scroll.
     * Defers to an existing keepInventory gamerule, where it is neither needed nor spent.
     */
    public record Holy(String material, String name, List<String> lore, int saveChance) {
        public Holy {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            saveChance = Math.max(0, Math.min(100, saveChance));
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
                        80),
                new Randomizer(
                        "SUGAR",
                        "&eRandomizer Scroll",
                        List.of("&7Drag onto an enchant book to", "&7reroll its success chance."),
                        25,
                        100),
                new Transmog(
                        "PURPLE_DYE",
                        "&5Transmog Scroll",
                        List.of("&7Drag onto enchanted gear to", "&7reorder its enchant display."),
                        " &8(Transmogged)"),
                new Holy(
                        "TOTEM_OF_UNDYING",
                        "&fHoly White Scroll",
                        List.of("&7Keep your items if you die", "&7while carrying this (one use)."),
                        100),
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
