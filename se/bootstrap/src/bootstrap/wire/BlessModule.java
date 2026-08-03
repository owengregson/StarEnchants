package bootstrap.wire;

import compile.load.MasterConfig;
import feature.bless.BlessCommand;
import feature.bless.BlessEffect;
import feature.bless.BlessGate;
import platform.sched.Scheduling;

/**
 * {@code /bless}, the player-facing cleanse (ADR-0072). BOOT-gated on {@code features.bless} — the command is
 * registered on the command map at boot, so like {@code command-trigger} the toggle takes effect on the next
 * start rather than a {@code /se reload}; the cost/cooldown knobs it reads ARE live.
 *
 * <p>Deliberately thin. The command delegates the gameplay body to {@link BlessEffect}; this module supplies the
 * permanent-potion ownership bridge and cross-era sound player, then adds the permission and cost/cooldown policy
 * around it.
 */
final class BlessModule {

    /** Bound the cooldown map for players who never return — the engine offline-sweep shape, in the small. */
    private static final long COOLDOWN_SWEEP_TICKS = 6000L; // 5 min

    private final BootCore core;
    final BlessGate gate;
    final BlessCommand command;

    BlessModule(BootCore core) {
        this.core = core;
        this.gate = new BlessGate(
                () -> {
                    MasterConfig.BlessSection cfg = core.master().config().bless();
                    return new BlessGate.Settings(cfg.cooldownSeconds(), cfg.cost());
                },
                core.economy());
        BlessEffect effect = new BlessEffect(core.sinkEnv().permanentPotions(), core.sounds());
        this.command = new BlessCommand("bless", effect::apply, gate, core.messages(),
                System::currentTimeMillis);
    }

    FeatureModule module() {
        return FeatureModule.named("bless")
                .toggle(Toggle.boot("features.bless",
                        () -> core.master().config().features().bless(),
                        "bless feature disabled (config.yml features.bless) — /bless not registered"))
                .command(DynCommand.always("bless", () -> command, "could not register /bless"))
                // Bound the cooldown map for players who never return (the engine offline-sweep shape).
                .boot(() -> Scheduling.repeatingGlobal(COOLDOWN_SWEEP_TICKS, COOLDOWN_SWEEP_TICKS,
                        () -> gate.forgetElapsed(System.currentTimeMillis())))
                .stop("bless cooldowns", gate::clearAll)
                .lang("command.bless")
                .build();
    }
}
