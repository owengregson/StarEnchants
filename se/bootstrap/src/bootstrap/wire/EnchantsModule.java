package bootstrap.wire;

import feature.menu.EnchantMenu;
import feature.menu.EnchantsBrowserMenu;
import java.util.function.Supplier;

/**
 * Custom enchants (§K, ADR-0047): the apply GUI and the tier→enchant browser. No listeners — enchants ride the
 * combat/trigger spine; the {@code features.enchants} toggle is LIVE (per worn-resolve), declared for introspection.
 */
final class EnchantsModule {

    private final BootCore core;
    private final EnchantMenu applyMenu;
    private final EnchantsBrowserMenu enchantsBrowser;

    EnchantsModule(BootCore core) {
        this.core = core;
        // Enchant-icon names are styled by the enchant-book name template, so a menu name matches the book.
        Supplier<String> bookName = () -> core.items().config().enchantBookOrDefault().name();
        this.applyMenu = new EnchantMenu(core.content(), core.enchanter(),
                player -> core.worn().refresh(player, core.content().snapshot()), core.caps(),
                core.menusHolder()::config, bookName, core.messages(), core.hands(), core.vanillaEnchants());
        this.enchantsBrowser = new EnchantsBrowserMenu(core.content(), core.caps(), core.menusHolder()::config,
                bookName, core.vanillaEnchants());
    }

    FeatureModule module() {
        return FeatureModule.named("enchants")
                .toggle(Toggle.live("features.enchants", () -> core.master().config().features().enchants()))
                .menu(40, applyMenu)
                .menu(50, enchantsBrowser)
                .lang("enchant", "command.apply")
                .build();
    }
}
