package feature.menu;

import compile.load.ContentHolder;
import compile.load.TierRegistry;
import feature.book.UnopenedBookService;
import item.lang.Messages;
import item.mint.ItemFactory;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The Enchanter merchant GUI (docs/v3-directives.md §K): a click-to-buy shop. Each slot is an offer — buy a
 * random ("mystery") enchant book of a rarity tier for an EXP-level price; clicking charges the price and
 * gives the unopened book (overflow drops at the feet). A display-only menu on the shared framework (no item
 * input), so it needs no interactive-slot handling.
 *
 * <p><strong>In-scope economy only.</strong> This is the modernized StarEnchants Enchanter — buy an
 * {@link UnopenedBookService unopened book} per tier. EE's separate console-command / money-priced slots are
 * a config concern (§L, which will replace these EXP-priced tier defaults with authored offers); EE's dust
 * rarity-tinkering is out of scope (ADR-0019). Default pricing comes from {@link EnchanterOffers}.
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
        super("enchanter", MenuLayout.paged("&3Enchanter"), caps);
        this.content = Objects.requireNonNull(content, "content");
        this.unopenedBooks = Objects.requireNonNull(unopenedBooks, "unopenedBooks");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected List<EnchanterOffers.Offer> items(MenuHolder holder) {
        return EnchanterOffers.defaults(content.library().tiers());
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
        player.setLevel(player.getLevel() - offer.costLevels()); // charge EXP levels (in-thread; safe)
        MenuItems.giveOrDrop(player, unopenedBooks.mint(offer.tier()));
        messages.send(player, "menu.enchanter.bought", "TIER", offer.tier());
    }

    private String tierColor(String tier) {
        TierRegistry.Tier t = content.library().tiers().tier(tier);
        return t != null && !t.color().isBlank() ? t.color() : "&7";
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
