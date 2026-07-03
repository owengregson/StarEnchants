package bootstrap.wire;

import feature.combat.CombatListener;

/** Combat hot path (ADR-0047): the one listener that feeds attack/defense activations to the engine. */
final class CombatModule {

    private final BootCore core;

    CombatModule(BootCore core) {
        this.core = core;
    }

    FeatureModule module() {
        return FeatureModule.named("combat")
                .events(new CombatListener(core.dispatch()))
                .lang("combat")
                .build();
    }
}
