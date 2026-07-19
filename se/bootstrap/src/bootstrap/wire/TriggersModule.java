package bootstrap.wire;

import feature.combat.FallingBlockListener;
import feature.combat.GuardianHurtListener;
import feature.trigger.CommandTriggerCommand;
import feature.trigger.PlacedBlockTracker;
import feature.trigger.TriggerListeners;

/**
 * Non-combat triggers (ADR-0047): the MINE/KILL/FALL/FIRE/INTERACT listener family, the era item-damage source,
 * the landing-FALLING_BLOCK IMPACT feeder, and the dynamic COMMAND-trigger command-map registration.
 */
final class TriggersModule {

    private final BootCore core;

    TriggersModule(BootCore core) {
        this.core = core;
    }

    FeatureModule module() {
        // §B COMMAND trigger: the dynamic name is read once at wire time (a name change needs a restart).
        String name = core.master().config().commandTrigger().name();
        // §F33 one placement tracker feeds both the MINE gate and its own place/break/piston bookkeeping.
        PlacedBlockTracker placed = new PlacedBlockTracker();
        return FeatureModule.named("triggers")
                .events(new TriggerListeners(core.triggerDispatch(),
                        () -> "ALL".equalsIgnoreCase(core.items().config().heroicOrDefault().reductionScope()),
                        core.hands(), placed,
                        () -> core.master().config().mining().placedBlockGuard())) // §F reduction-scope + §F33 guard
                .events(placed)
                // ITEM_DAMAGE source (§4): modern PlayerItemDamageEvent listener; on 1.8 an inert listener.
                .events(core.bindings().itemDamageSource(core.triggerDispatch()))
                // A landing FALLING_BLOCK fires the IMPACT trigger on whoever it hit (druid Terrablender grass rain).
                .events(new FallingBlockListener(core.triggerDispatch()))
                // A hit on a summoned guardian fires GUARDIAN_HURT on its owner (ADR-0049 Blood Link).
                .events(new GuardianHurtListener(core.triggerDispatch()))
                // A GuardianCasts-owned summon never re-acquires its own summoner as a target (ADR-0071
                // amendments) — registered here with its sibling reader, not in reforges: every family's
                // GUARD/SPAWN_ENTITY summons feed the same registry.
                .events(new feature.combat.SummonTargetGuardListener())
                .command(new DynCommand(name,
                        () -> core.master().config().commandTrigger().enabled(),
                        () -> {
                            var ct = core.master().config().commandTrigger();
                            return new CommandTriggerCommand(ct.name(), ct.description(), core.triggerDispatch(),
                                    core.messages().format("command.not-a-player"));
                        },
                        "could not register the command-trigger '/" + name
                                + "' (COMMAND enchants will not be fireable by command)",
                        "command-trigger registered: /" + name))
                .lang("command")
                .build();
    }
}
