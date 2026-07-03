package bootstrap.wire;

import feature.carrier.CarrierListener;
import feature.carrier.CarrierService;
import feature.menu.AdminBrowserMenu;
import feature.menu.AlchemistMenu;
import feature.menu.TinkererMenu;

/**
 * Carrier economy (ADR-0016, ADR-0047): enchant books / success dust / white scroll. {@code CarrierService} is
 * exposed because the book, scroll and unopened-book modules layer on it, and the mint/command surfaces draw on it.
 */
final class CarriersModule {

    private final BootCore core;
    final CarrierService carriers;

    CarriersModule(BootCore core) {
        this.core = core;
        this.carriers = new CarrierService(core.carrierCodec(), core.enchanter(), core.content(), core.rolls(),
                () -> core.items().config().enchantBookOrDefault(),   // §I enchant book
                () -> core.items().config().dustOrDefault(),          // §I success dust
                () -> core.items().config().whiteScrollOrDefault(),   // §I white scroll
                () -> core.master().config().lore().roman(),          // book level numeral style (lore.roman, live)
                () -> core.master().config().books().maxSuccess(),    // §I global success ceiling (books.max-success)
                core.appliedSlot(),                                   // §I white scroll occupies this
                core.recompose(),                                     // ADR-0040 recompose gear lore after a toggle
                core.itemGroups(),                                    // §I white-scroll applies-to gate
                core.messages());                                     // §I applies reject reads common.wrong-applies
    }

    FeatureModule module() {
        return FeatureModule.named("carriers")
                .events(new CarrierListener(carriers, core.carrierCodec(), core.particleFx(), core.messages(),
                        core.sounds()))
                .menu(110, new AlchemistMenu(carriers, core.caps(), core.messages(), core.menusHolder()::config,
                        core.vanillaEnchants()))
                .menu(120, new TinkererMenu(carriers, core.caps(), core.messages(), core.menusHolder()::config,
                        core.vanillaEnchants()))
                .menu(130, new AdminBrowserMenu(core.content(), carriers, core.caps(), core.messages(),
                        core.menusHolder()::config, core.vanillaEnchants()))
                .pluginItem(stack -> core.carrierCodec().read(stack) != null) // enchant books, magic dust, white scroll
                .lang("carrier", "command.give.book", "command.give.dust", "command.give.whitescroll")
                .build();
    }
}
