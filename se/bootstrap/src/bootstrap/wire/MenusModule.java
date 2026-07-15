package bootstrap.wire;

import feature.menu.GodlyTransmogListener;
import feature.menu.MenuListener;
import feature.menu.MenuRegistry;
import feature.menu.MintCatalog;
import feature.menu.MintMenu;
import feature.menu.Mintable;
import feature.menu.OperatorConsoleMenu;
import feature.menu.ReferenceBrowserMenu;
import feature.menu.UserHubMenu;
import feature.menu.UserMenuCommand;
import java.util.List;

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

    MenusModule(BootCore core, ReloadModule reload, ScrollsModule scrolls, List<Mintable> mintables) {
        this.core = core;
        this.scrolls = scrolls;
        this.userHub = new UserHubMenu(registry, core.caps(), core.menusHolder()::config, core.vanillaEnchants());
        this.operatorConsole = new OperatorConsoleMenu(registry, reload.reloader, core.messages(), core.caps(),
                core.menusHolder()::config, core.vanillaEnchants());
        // The operator "mint anything" catalogue (ADR-0030, ADR-0047) — derived from the module-declared mintables.
        MintCatalog mintCatalog = new MintCatalog(mintables, core.content());
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
        // Player-facing menu shortcuts (ADR-0030): each opens one bench/browser directly, since /se is admin-gated
        // and a normal player otherwise has no way in. /enchants and /enchanter both open the enchanter bench;
        // /catalogue opens the read-only "enchants" Enchant Catalogue (its natural name is taken by the bench); the
        // rest share their menu name. Retiring the /enchants→hub launcher, /crystals + /catalogue keep the hub's
        // Crystals browser + Catalogue reachable. Registered dynamically like /splitsouls; the target resolves at
        // execute time, so a shortcut whose family is disabled reports gracefully rather than failing to register.
        b.command(menuCommand("enchants", "enchanter"))
                .command(menuCommand("enchanter", "enchanter"))
                .command(menuCommand("pets", "pets"))
                .command(menuCommand("masks", "masks"))
                .command(menuCommand("tinkerer", "tinkerer"))
                .command(menuCommand("alchemist", "alchemist"))
                .command(menuCommand("sets", "sets"))
                .command(menuCommand("crystals", "crystals"))
                .command(menuCommand("catalogue", "enchants"));
        return b.menu(10, userHub)
                .menu(20, operatorConsole)
                .menu(30, mintMenu)
                .menu(80, referenceBrowser)
                .lang("command")
                .build();
    }

    /** A {@code /label} shortcut that opens the registry menu {@code menuName} for a {@code starenchants.use} player. */
    private DynCommand menuCommand(String label, String menuName) {
        return DynCommand.always(label,
                () -> new UserMenuCommand(label, menuName, registry, core.messages()),
                "could not register /" + label + " (use /se menu " + menuName + " instead)");
    }
}
