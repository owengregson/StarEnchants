package bootstrap.wire;

/**
 * The vanilla-mechanic guard slot (ADR-0047): declares the {@link Wire.PluginItemGuard} position, which the
 * fold materializes into a {@code VanillaGuardListener} over the OR of every module's plugin-item contribution
 * — a module cannot see the whole registry, the fold can. Custom items do ONLY their intended action.
 */
final class GuardModule {

    GuardModule(BootCore core) {
    }

    FeatureModule module() {
        return FeatureModule.named("guard")
                .pluginItemGuard()
                .build();
    }
}
