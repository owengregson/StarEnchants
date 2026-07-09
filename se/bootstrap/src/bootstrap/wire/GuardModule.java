package bootstrap.wire;

/**
 * The vanilla-mechanic guard slot (ADR-0047): declares the {@link Wire.PluginItemGuard} position, which the
 * fold materializes into a {@code VanillaGuardListener} over the OR of every module's plugin-item contribution
 * — a module cannot see the whole registry, the fold can. Custom items do ONLY their intended action.
 */
final class GuardModule {

    private final org.bukkit.event.Listener stationGuard;

    GuardModule(BootCore core) {
        // Vanilla-station guard (G04/G05/G06): the era-seam smithing/grindstone/anvil guard over set gear, built
        // once and registered right after the vanilla-mechanic guard so both live in the one guard module.
        this.stationGuard = core.bindings().stationGuard(core.stationGuardRules());
    }

    FeatureModule module() {
        return FeatureModule.named("guard")
                .pluginItemGuard()
                .events(stationGuard)
                .build();
    }
}
