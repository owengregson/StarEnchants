package bootstrap.wire;

import compile.load.MasterConfig;
import feature.bless.BlessCommand;
import feature.bless.BlessGate;
import feature.bless.CleanseService;
import org.bukkit.potion.PotionEffectType;
import platform.sched.Scheduling;

/**
 * {@code /bless}, the player-facing debuff cleanse (ADR-0072). BOOT-gated on {@code features.bless} — the
 * command is registered on the command map at boot, so like {@code command-trigger} the toggle takes effect on
 * the next start rather than a {@code /se reload}; the cost/cooldown knobs it reads ARE live.
 *
 * <p>Depends on {@link EquipModule} for the passive-potion authority: the cleanse must not strip a
 * permanent-while-worn grant, and that driver is the only thing that knows which effects those are.
 */
final class BlessModule {

    /** Bound the cooldown map for players who never return — the engine offline-sweep shape, in the small. */
    private static final long COOLDOWN_SWEEP_TICKS = 6000L; // 5 min

    private final BootCore core;
    final CleanseService cleanse;
    final BlessGate gate;
    final BlessCommand command;

    BlessModule(BootCore core, EquipModule equip) {
        this.core = core;
        // A live potion type → its interned handle, so the driver's owned set (which is keyed by handle) can be
        // asked about an effect the player is actually carrying. An unresolvable type is simply not a passive.
        CleanseService.PassivePotions passives = (player, type) -> {
            int handle = handleOf(type);
            return handle >= 0 && equip.passiveEffects.maintains(player.getUniqueId(), handle);
        };
        this.cleanse = new CleanseService(passives, core.sinkEnv().dotPark());
        this.gate = new BlessGate(
                () -> {
                    MasterConfig.BlessSection cfg = core.master().config().bless();
                    return new BlessGate.Settings(cfg.cooldownSeconds(), cfg.cost());
                },
                core.economy());
        this.command = new BlessCommand("bless", cleanse, gate, core.messages(), System::currentTimeMillis);
    }

    /** The interned handle for a live potion type, or {@code -1} when this version does not resolve it. */
    @SuppressWarnings("deprecation") // getName(): the one name accessor stable across 1.8.9 → 26.x (PotionCategories).
    private int handleOf(PotionEffectType type) {
        return type == null ? -1 : core.resolvers().potionEffect(type.getName()).orElse(-1);
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
