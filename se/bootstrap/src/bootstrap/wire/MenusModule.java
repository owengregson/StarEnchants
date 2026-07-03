package bootstrap.wire;

import feature.menu.GodlyTransmogListener;
import feature.menu.MenuListener;
import feature.menu.MenuRegistry;
import feature.menu.MintCatalog;
import feature.menu.MintMenu;
import feature.menu.OperatorConsoleMenu;
import feature.menu.ReferenceBrowserMenu;
import feature.menu.UserHubMenu;
import feature.menu.UserMenuCommand;

/**
 * The GUI framework (§K, ADR-0047): the shared {@code MenuRegistry} plus the hub, operator console, mint menu
 * and reference browser; the click listener; the scroll-gated godly-transmog gesture; and the {@code /enchants}
 * command. The fold registers every module's menu contributions (this one's + the feature browsers) into the
 * registry, rank-ordered, so the {@code /se menu} completion order is preserved. Registered menus look siblings
 * up live, so the empty-at-construction registry is fine — the fold populates it before any menu opens.
 */
final class MenusModule {

    private final BootCore core;
    private final ScrollsModule scrolls;
    final MenuRegistry registry = new MenuRegistry();
    private final UserHubMenu userHub;
    private final OperatorConsoleMenu operatorConsole;
    private final MintMenu mintMenu;
    private final ReferenceBrowserMenu referenceBrowser;

    MenusModule(BootCore core, ReloadModule reload, CarriersModule carriers, CrystalsModule crystals,
                HeroicModule heroic, SlotsModule slots, BooksModule books, ScrollsModule scrolls,
                TraksModule traks) {
        this.core = core;
        this.scrolls = scrolls;
        this.userHub = new UserHubMenu(registry, core.caps(), core.menusHolder()::config, core.vanillaEnchants());
        this.operatorConsole = new OperatorConsoleMenu(registry, reload.reloader, core.messages(), core.caps(),
                core.menusHolder()::config, core.vanillaEnchants());
        // The operator "mint anything" catalogue (ADR-0030) — driven by the live tier list + trak kinds.
        MintCatalog mintCatalog = new MintCatalog(core.content(), core.soulService(), slots.slots, heroic.heroics,
                crystals.crystals, scrolls.scrolls, scrolls.holyScrolls, scrolls.nametags, carriers.carriers,
                traks.traks, books.unopenedBooks);
        this.mintMenu = new MintMenu(mintCatalog, core.caps(), core.messages(), core.menusHolder()::config,
                core.vanillaEnchants());
        this.referenceBrowser = new ReferenceBrowserMenu(core.caps(), core.menusHolder()::config,
                core.vanillaEnchants());
    }

    FeatureModule module() {
        FeatureModule.Builder b = FeatureModule.named("menus")
                .events(new MenuListener(core.hands()));
        // §I/§K physical godly-transmog gesture — scroll family, so it shares the features.scrolls() boot gate.
        if (core.master().config().features().scrolls()) {
            b.events(new GodlyTransmogListener(scrolls.scrolls, scrolls.transmogMenu, core.codec(),
                    core.messages(), core.sounds()));
        }
        return b.command(DynCommand.always("enchants",
                        () -> new UserMenuCommand("enchants", registry, core.messages()),
                        "could not register /enchants (use /se menu hub instead)"))
                .menu(10, userHub)
                .menu(20, operatorConsole)
                .menu(30, mintMenu)
                .menu(80, referenceBrowser)
                .lang("command")
                .build();
    }
}
