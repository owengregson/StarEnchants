package bootstrap.wire;

import feature.book.UnopenedBookListener;
import feature.book.UnopenedBookService;
import feature.menu.EnchanterMenu;
import feature.menu.Mintable;
import item.codec.ItemKeys;
import item.codec.UnopenedBookCodec;
import java.util.List;

/**
 * Unopened/randomized books (§I, ADR-0047): a sealed book that opens into a random enchant of its tier. Layers
 * on the carrier economy (it mints carrier books), so it takes the carriers module.
 */
final class BooksModule {

    private final BootCore core;
    private final CarriersModule carriers;
    private final ScrollsModule scrolls;
    final UnopenedBookService unopenedBooks;
    final List<Mintable> mints;

    /** {@code scrolls} supplies the black-scroll mint for the Enchanter's scroll tiles; the white scroll
     *  rides the carrier economy. Constructed after the scrolls module for exactly this hand-off. */
    BooksModule(BootCore core, CarriersModule carriers, ScrollsModule scrolls) {
        this.core = core;
        this.carriers = carriers;
        this.scrolls = scrolls;
        UnopenedBookCodec unopenedCodec = new UnopenedBookCodec(ItemKeys.of().unopened(), core.store());
        this.unopenedBooks = new UnopenedBookService(unopenedCodec, carriers.carriers, core.content(),
                () -> core.items().config().unopenedBookOrDefault(), core.rolls(), core.messages());
        // The enchant `book` mint lives here: its random-tier path rolls an unopened book, so it needs BOTH the
        // carrier economy and the unopened-book service — this module sees both.
        this.mints = List.of(Mints.book(carriers.carriers, unopenedBooks, core.content()),
                Mints.unopened(unopenedBooks, core.content()));
    }

    FeatureModule module() {
        return FeatureModule.named("books")
                // position above the scrolls block, NOT scroll-gated today.
                .events(new UnopenedBookListener(unopenedBooks, core.messages(), core.hands()))
                .menu(100, new EnchanterMenu(core.content(), unopenedBooks, core.caps(), core.messages(),
                        core.menusHolder()::config, core.vanillaEnchants(),
                        carriers.carriers::mintWhiteScroll, scrolls.scrolls::mintBlack))
                .mints(mints)
                .pluginItem(unopenedBooks::isUnopened)
                .lang("command.give.book", "command.give.unopened")
                .build();
    }
}
