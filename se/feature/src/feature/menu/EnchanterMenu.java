package feature.menu;

import compile.load.ContentHolder;
import feature.book.UnopenedBookService;
import platform.lang.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;
import platform.item.Inventories;

/**
 * The Enchanter merchant GUI (§K): a fixed storefront in the Cosmic Enchants likeness — one tier-coloured
 * glass tile per rarity (five across the first shelf, the premium tiers centred on the second) selling a
 * random {@link UnopenedBookService unopened book} for that tier's EXP-level price, with the white and black
 * scroll tiles flanking the premium shelf. The EXP-priced tier defaults ({@link EnchanterOffers}) are a §L
 * placeholder for authored offers; Cosmic Enchants-style console-command / money-priced slots are a config
 * concern (§L), and Cosmic Enchants-style dust rarity-tinkering is out of scope (ADR-0019). Scroll tiles
 * appear only when the composition root wires their mints (they layer on the carrier/scroll modules).
 */
public final class EnchanterMenu extends FormMenu {

    public static final int WHITE_SCROLL_TILE = 20;
    public static final int BLACK_SCROLL_TILE = 24;

    /**
     * Book-tile placement, in tier-declaration order: the first shelf (11–15), then the premium shelf centred
     * between the scroll tiles (21–23), then outward from those shelves for packs with more tiers. A pack
     * with more tiers than cells shows the first sixteen (the signature pack ships eight).
     */
    private static final int[] BOOK_TILE_SLOTS = {11, 12, 13, 14, 15, 21, 22, 23, 19, 25, 10, 16, 18, 26, 9, 17};

    private final ContentHolder content;
    private final UnopenedBookService unopenedBooks;
    private final Messages messages;
    private final Supplier<ItemStack> whiteScrollMint;
    private final Supplier<ItemStack> blackScrollMint;

    /** Default-messages form (tests/fixtures). */
    public EnchanterMenu(ContentHolder content, UnopenedBookService unopenedBooks, Capabilities caps) {
        this(content, unopenedBooks, caps, Messages.defaults());
    }

    public EnchanterMenu(ContentHolder content, UnopenedBookService unopenedBooks, Capabilities caps,
                         Messages messages) {
        this(content, unopenedBooks, caps, messages, compile.load.MenusConfig::empty,
                item.mint.VanillaEnchants.NONE);
    }

    /** No-scroll form (the shop sells books only) — the scroll tiles need the carrier/scroll mints wired. */
    public EnchanterMenu(ContentHolder content, UnopenedBookService unopenedBooks, Capabilities caps,
                         Messages messages, Supplier<compile.load.MenusConfig> menus,
                         item.mint.VanillaEnchants vanilla) {
        this(content, unopenedBooks, caps, messages, menus, vanilla, null, null);
    }

    public EnchanterMenu(ContentHolder content, UnopenedBookService unopenedBooks, Capabilities caps,
                         Messages messages, Supplier<compile.load.MenusConfig> menus,
                         item.mint.VanillaEnchants vanilla,
                         Supplier<ItemStack> whiteScrollMint, Supplier<ItemStack> blackScrollMint) {
        super("enchanter", MenuLayout.form(4, "&b&lEnchanter &8• &7Books"), caps, menus, vanilla);
        this.content = Objects.requireNonNull(content, "content");
        this.unopenedBooks = Objects.requireNonNull(unopenedBooks, "unopenedBooks");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.whiteScrollMint = whiteScrollMint;
        this.blackScrollMint = blackScrollMint;
    }

    @Override
    public Set<Integer> inputSlots() {
        return Set.of(); // a storefront: every slot is locked, tiles act on click
    }

    @Override
    protected String infoTitle() {
        return "&b&lEnchanter";
    }

    @Override
    protected List<String> infoLore() {
        return List.of("&7Spend experience levels on a", "&7random enchant book of a rarity.");
    }

    @Override
    protected void layoutControls(MenuHolder holder) {
        List<EnchanterOffers.Offer> offers = EnchanterOffers.defaults(content.library().tiers());
        for (int i = 0; i < offers.size() && i < BOOK_TILE_SLOTS.length; i++) {
            EnchanterOffers.Offer offer = offers.get(i);
            holder.set(BOOK_TILE_SLOTS[i], bookTile(offer), click -> buyBook(click, offer.tier()));
        }
        if (whiteScrollMint != null) {
            holder.set(WHITE_SCROLL_TILE, scrollTile("MAP", "&eWhite Scroll",
                    List.of("&fPrevents an item from being destroyed",
                            "&fdue to a failed Enchantment Book.", "&ePlace scroll on item to apply.")),
                    click -> buyScroll(click, "White Scroll", whiteScrollMint));
        }
        if (blackScrollMint != null) {
            holder.set(BLACK_SCROLL_TILE, scrollTile("INK_SAC", "&f&lBlack Scroll",
                    List.of("&7Removes a random enchantment", "&7from an item and converts",
                            "&7it into an enchantment book.", "&fPlace onto item to extract.")),
                    click -> buyScroll(click, "Black Scroll", blackScrollMint));
        }
    }

    private ItemStack bookTile(EnchanterOffers.Offer offer) {
        String color = tierColor(offer.tier());
        String tier = capitalize(offer.tier());
        return MenuIcons.tile(vanilla, paneFor(color), Material.BOOK,
                color + "&l" + tier + " Enchantment",
                List.of("&7Click to purchase a " + color + tier, "&7enchantment book.", "",
                        costLine(offer.costLevels())),
                "&eClick to buy.");
    }

    private ItemStack scrollTile(String materialToken, String name, List<String> body) {
        List<String> lore = new ArrayList<>(body);
        lore.add("");
        lore.add(costLine(scrollCost()));
        return MenuIcons.tile(vanilla, materialToken, Material.PAPER, name, lore, "&eClick to buy.");
    }

    private static String costLine(int levels) {
        return "&b&lCOST &f" + levels + " XP Level" + (levels == 1 ? "" : "s");
    }

    private void buyBook(MenuClick click, String tierKey) {
        Player player = click.player();
        // Re-read the tier's price from the LIVE snapshot on click — the tile captured at render is only a
        // tier key. A reload that raised the price (or removed the tier) between open and click must charge
        // the current cost, not the stale one the tile still shows.
        compile.load.TierRegistry.Tier tier = content.library().tiers().tier(tierKey);
        if (tier == null) {
            messages.send(player, "menu.enchanter.unavailable");
            return;
        }
        int cost = EnchanterOffers.priceFor(tier);
        if (player.getLevel() < cost) {
            messages.send(player, "menu.enchanter.too-poor", "COST", cost);
            return;
        }
        player.setLevel(player.getLevel() - cost); // safe: the clicker's own region thread
        Inventories.giveOrDrop(player, unopenedBooks.mint(tierKey));
        messages.send(player, "menu.enchanter.bought", "TIER", tierKey);
    }

    private void buyScroll(MenuClick click, String label, Supplier<ItemStack> mint) {
        Player player = click.player();
        int cost = scrollCost(); // re-read live, like the book price
        if (player.getLevel() < cost) {
            messages.send(player, "menu.enchanter.too-poor", "COST", cost);
            return;
        }
        player.setLevel(player.getLevel() - cost); // safe: the clicker's own region thread
        Inventories.giveOrDrop(player, mint.get());
        messages.send(player, "menu.enchanter.bought-item", "ITEM", label);
    }

    private int scrollCost() {
        return EnchanterOffers.scrollPriceLevels(content.library().tiers());
    }

    /** The tier-coloured pane a book tile renders as (legacy servers fall back to the plain book icon). */
    private static String paneFor(String colorCode) {
        char code = colorCode != null && colorCode.length() >= 2 ? Character.toLowerCase(colorCode.charAt(1)) : '7';
        return switch (code) {
            case 'f', '7' -> "WHITE_STAINED_GLASS_PANE";
            case 'a', '2' -> "LIME_STAINED_GLASS_PANE";
            case 'b', '3' -> "LIGHT_BLUE_STAINED_GLASS_PANE";
            case 'e' -> "YELLOW_STAINED_GLASS_PANE";
            case '6' -> "ORANGE_STAINED_GLASS_PANE";
            case 'd' -> "PINK_STAINED_GLASS_PANE";
            case 'c', '4' -> "RED_STAINED_GLASS_PANE";
            case '5' -> "PURPLE_STAINED_GLASS_PANE";
            case '9', '1' -> "BLUE_STAINED_GLASS_PANE";
            case '8', '0' -> "GRAY_STAINED_GLASS_PANE";
            default -> "GRAY_STAINED_GLASS_PANE";
        };
    }

    private String tierColor(String tier) {
        return content.library().tiers().colorOf(tier);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
