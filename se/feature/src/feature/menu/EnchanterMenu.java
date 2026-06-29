package feature.menu;

import compile.load.ContentHolder;
import feature.book.UnopenedBookService;
import item.lang.Messages;
import item.mint.ItemFactory;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The Enchanter merchant GUI (§K): click an offer to buy a random {@link UnopenedBookService unopened book}
 * of a rarity tier for an EXP-level price. The EXP-priced tier defaults ({@link EnchanterOffers}) are a §L
 * placeholder for authored offers; Cosmic Enchants-style console-command / money-priced slots are a config
 * concern (§L), and Cosmic Enchants-style dust rarity-tinkering is out of scope (ADR-0019).
 */
public final class EnchanterMenu extends PagedMenu<EnchanterOffers.Offer> {

    private final ContentHolder content;
    private final UnopenedBookService unopenedBooks;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public EnchanterMenu(ContentHolder content, UnopenedBookService unopenedBooks, Capabilities caps) {
        this(content, unopenedBooks, caps, Messages.defaults());
    }

    public EnchanterMenu(ContentHolder content, UnopenedBookService unopenedBooks, Capabilities caps,
                         Messages messages) {
        this(content, unopenedBooks, caps, messages, compile.load.MenusConfig::empty);
    }

    public EnchanterMenu(ContentHolder content, UnopenedBookService unopenedBooks, Capabilities caps,
                         Messages messages, java.util.function.Supplier<compile.load.MenusConfig> menus) {
        super("enchanter", MenuLayout.paged("&b&lEnchanter &8• &7Mystery Books"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
        this.unopenedBooks = Objects.requireNonNull(unopenedBooks, "unopenedBooks");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected List<EnchanterOffers.Offer> items(MenuHolder holder) {
        return EnchanterOffers.defaults(content.library().tiers());
    }

    @Override
    protected String infoTitle(MenuHolder holder) {
        return "&b&lEnchanter";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        return List.of("&7Spend experience levels on a", "&7random enchant book of a rarity.");
    }

    @Override
    protected ItemStack icon(MenuHolder holder, EnchanterOffers.Offer offer) {
        return ItemFactory.build(material("ENCHANTED_BOOK", "BOOK", "PAPER"),
                tierColor(offer.tier()) + capitalize(offer.tier()) + " Mystery Book",
                List.of("&7A random " + offer.tier() + " enchant book.",
                        "&8cost: &a" + offer.costLevels() + " XP level" + (offer.costLevels() == 1 ? "" : "s"),
                        "&eClick to buy."));
    }

    @Override
    protected void onSelect(MenuClick click, EnchanterOffers.Offer offer) {
        Player player = click.player();
        if (player.getLevel() < offer.costLevels()) {
            messages.send(player, "menu.enchanter.too-poor", "COST", offer.costLevels());
            return;
        }
        player.setLevel(player.getLevel() - offer.costLevels()); // safe: the clicker's own region thread
        MenuItems.giveOrDrop(player, unopenedBooks.mint(offer.tier()));
        messages.send(player, "menu.enchanter.bought", "TIER", offer.tier());
    }

    private String tierColor(String tier) {
        return MenuText.tierColor(content.library().tiers(), tier);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
