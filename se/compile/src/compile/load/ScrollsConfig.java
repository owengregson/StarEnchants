package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of the SCROLL family (§I). Each member is authored in its OWN
 * top-level file (one physical item per file, matching every other {@code items/} entry); this record is
 * purely the internal grouping the scroll services consume, since the members share item-data machinery
 * ({@code ScrollCodec}). Immutable; lives in the {@link ItemsConfig} snapshot {@code /se reload} swaps.
 *
 * @param black      the black scroll (extract one enchant from gear into a book)
 * @param randomizer the randomizer scroll (reroll a book's success chance)
 * @param transmog   the transmog scroll (reorder an item's enchant lore + name suffix)
 * @param holy       the holy white scroll (survive a death once — keeps items/levels)
 * @param nametag    the item nametag (rename gear via chat)
 * @param godly      the physical godly-transmog tool (open the reorder GUI on a clicked piece)
 */
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
     * The black scroll: dragged onto enchanted gear, it extracts one (random) enchant into an enchant book
     * with a {@link #successChance} roll. On failure the scroll is spent and nothing is extracted.
     */
    public record Black(String material, String name, List<String> lore, int successChance) {
        public Black {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            successChance = Math.max(0, Math.min(100, successChance));
        }
    }

    /**
     * The randomizer scroll: dragged onto an enchant book, it rerolls the book's success chance to a random
     * value in {@code [minPercent, maxPercent]}.
     */
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
     * The transmog scroll: dragged onto enchanted gear, it reorders the item's enchant lore (cosmetic — the
     * combat behaviour is order-independent) and appends a configurable {@code nameSuffix} to the item name.
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
     * The holy / death scroll: held in the inventory (incl. off-hand), on a death with a {@link #saveChance}
     * roll it keeps the player's items + levels (consumed on the saved death). Respects an existing
     * keepInventory gamerule (then it is neither needed nor spent).
     */
    public record Holy(String material, String name, List<String> lore, int saveChance) {
        public Holy {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            saveChance = Math.max(0, Math.min(100, saveChance));
        }
    }

    /**
     * The item nametag: dragged onto gear, it prompts the player to type a new name in chat; the name is
     * rejected if it contains a blacklisted word. {@code blacklist} entries are matched case-insensitively
     * as substrings of the (colour-stripped) name.
     */
    public record Nametag(String material, String name, List<String> lore, List<String> blacklist) {
        public Nametag {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            blacklist = List.copyOf(blacklist);
        }
    }

    /**
     * The physical godly-transmog tool: dragged onto enchanted gear, it OPENS the deterministic
     * enchant-reorder GUI (§K) bound to that piece — a reusable tool, not a one-shot scroll. Its likeness
     * is configured here; its marker is the dedicated {@code GodlyTransmogCodec} (off the scroll consume path).
     */
    public record Godly(String material, String name, List<String> lore) {
        public Godly {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
        }
    }

    /** The built-in scroll likenesses used when {@code items/scrolls.yml} is absent or omits fields. */
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
