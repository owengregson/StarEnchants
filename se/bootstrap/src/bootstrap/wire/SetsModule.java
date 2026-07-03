package bootstrap.wire;

import feature.menu.SetsBrowserMenu;

/**
 * Armor sets (§6.6, ADR-0047): the sets→pieces→mint browser. Sets ride the worn-resolve completion path; the
 * {@code features.sets} toggle is LIVE (per worn-resolve), declared for introspection.
 */
final class SetsModule {

    private final BootCore core;
    private final SetsBrowserMenu setsBrowser;

    SetsModule(BootCore core) {
        this.core = core;
        this.setsBrowser = new SetsBrowserMenu(core.content(), core.enchanter(), core.caps(), core.messages(),
                core.menusHolder()::config, core.vanillaEnchants());
    }

    FeatureModule module() {
        return FeatureModule.named("sets")
                .toggle(Toggle.live("features.sets", () -> core.master().config().features().sets()))
                .menu(60, setsBrowser)
                .lang("command.give.set")
                .build();
    }
}
