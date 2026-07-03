package bootstrap.wire;

import feature.book.UnopenedBookListener;
import feature.book.UnopenedBookService;
import feature.menu.EnchanterMenu;
import item.codec.ItemKeys;
import item.codec.UnopenedBookCodec;

/**
 * Unopened/randomized books (§I, ADR-0047): a sealed book that opens into a random enchant of its tier. Layers
 * on the carrier economy (it mints carrier books), so it takes the carriers module.
 */
final class BooksModule {

    private final BootCore core;
    final UnopenedBookService unopenedBooks;

    BooksModule(BootCore core, CarriersModule carriers) {
        this.core = core;
        UnopenedBookCodec unopenedCodec = new UnopenedBookCodec(ItemKeys.of().unopened(), core.store());
        this.unopenedBooks = new UnopenedBookService(unopenedCodec, carriers.carriers, core.content(),
                () -> core.items().config().unopenedBookOrDefault(), core.rolls(), core.messages());
    }

    FeatureModule module() {
        return FeatureModule.named("books")
                // position above the scrolls block, NOT scroll-gated today.
                .events(new UnopenedBookListener(unopenedBooks, core.messages(), core.hands()))
                .menu(100, new EnchanterMenu(core.content(), unopenedBooks, core.caps(), core.messages(),
                        core.menusHolder()::config, core.vanillaEnchants()))
                .pluginItem(unopenedBooks::isUnopened)
                .lang("command.give.unopened")
                .build();
    }
}
