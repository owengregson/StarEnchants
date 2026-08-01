package bootstrap.wire;

import feature.combat.FallingBlockListener;
import feature.combat.GuardianHurtListener;
import feature.combat.NutritionListener;
import feature.trigger.CommandTriggerCommand;
import feature.trigger.PlacedBlockTracker;
import feature.trigger.TriggerListeners;

/**
 * Non-combat triggers (ADR-0047): the MINE/KILL/FALL/FIRE/INTERACT listener family, the era item-damage source,
 * the landing-FALLING_BLOCK IMPACT feeder, and the dynamic COMMAND-trigger command-map registration.
 */
final class TriggersModule {

    private final BootCore core;
    final feature.combat.NatureWrathListener natureWrath;

    TriggersModule(BootCore core) {
        this.core = core;
        this.natureWrath = new feature.combat.NatureWrathListener(core.bindings().sinkFactory(), core.sinkEnv(),
                core.soulService(), core.protection(), core.resolvers());
    }

    FeatureModule module() {
        feature.combat.PhoenixListener phoenix = new feature.combat.PhoenixListener(
                core.bindings().sinkFactory(), core.sinkEnv(), core.soulService(), core.resolvers());
        feature.combat.DimensionalTravelerListener dimensionalTraveler =
                new feature.combat.DimensionalTravelerListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        phoenix, core.protection(), core.resolvers());
        feature.combat.CosmicMasteryListener cosmicMasteries =
                new feature.combat.CosmicMasteryListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.protection(), core.resolvers(), core.tick()::get);
        feature.combat.RotAndDecayListener rotAndDecay =
                new feature.combat.RotAndDecayListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.protection(), core.itemViews(), core.hands(), core.resolvers(), core.tick()::get);
        feature.combat.CosmicWeaponListener cosmicWeapons =
                new feature.combat.CosmicWeaponListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.protection(), core.soulService(), core.resolvers(), core.tick()::get);
        feature.combat.CosmicArmorSummonListener cosmicArmorSummons =
                new feature.combat.CosmicArmorSummonListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.protection(), core.resolvers(), core.tick()::get);
        feature.combat.CosmicImmortalListener cosmicImmortal =
                new feature.combat.CosmicImmortalListener(core.itemViews(), core.bindings().sinkFactory(),
                        core.sinkEnv(), core.soulService(), core.resolvers());
        feature.combat.CosmicDiminishListener cosmicDiminish =
                new feature.combat.CosmicDiminishListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.tick()::get);
        feature.combat.FeignDeathListener feignDeath =
                new feature.combat.FeignDeathListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.resolvers(), core.tick()::get);
        feature.combat.CosmicHexUnfocusListener hexUnfocus =
                new feature.combat.CosmicHexUnfocusListener(core.bindings().sinkFactory(),
                        core.sinkEnv(), core.resolvers());
        feature.combat.CosmicSelfDestructListener selfDestruct =
                new feature.combat.CosmicSelfDestructListener(core.bindings().sinkFactory(),
                        core.sinkEnv(), core.protection(), core.resolvers());
        feature.combat.HorrifyListener horrify =
                new feature.combat.HorrifyListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.protection(), core.resolvers(), core.tick()::get);
        feature.combat.MarkOfTheBeastListener markOfTheBeast =
                new feature.combat.MarkOfTheBeastListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.resolvers());
        feature.combat.DemonicGatewayListener demonicGateway =
                new feature.combat.DemonicGatewayListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.protection(), core.resolvers());
        feature.combat.HeadDropMarkListener headDrops =
                new feature.combat.HeadDropMarkListener(core.bindings().sinkFactory(), core.sinkEnv());
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
                .events(new feature.combat.GuardianProtectionListener())
                .events(cosmicArmorSummons)
                .events(new feature.combat.SpawnerOriginListener(core.plugin()))
                .events(new feature.combat.BleedCleanupListener())
                .events(new NutritionListener())
                .events(cosmicImmortal)
                .events(cosmicDiminish)
                .events(new feature.combat.ExpDropMarkListener())
                .events(headDrops)
                .events(new feature.combat.VirusDamageListener())
                .events(new feature.combat.SilenceDamageListener(core.sinkEnv()))
                .events(hexUnfocus)
                .events(selfDestruct)
                .events(phoenix)
                .events(new feature.combat.PlagueCarrierListener(core.bindings().sinkFactory(),
                        core.sinkEnv(), core.resolvers()))
                .events(horrify)
                .events(feignDeath)
                .events(new feature.combat.RocketEscapeListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.resolvers(), core.tick()::get))
                .events(natureWrath)
                .events(new feature.combat.ParadoxListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.soulService(), core.resolvers()))
                .events(new feature.combat.SoulSiphonListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.soulService()))
                .events(markOfTheBeast)
                .events(new feature.combat.TombstoneListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        core.protection(), core.resolvers()))
                .events(demonicGateway)
                .events(new feature.combat.DetonateListener(core.itemViews(), core.hands(),
                        core.bindings().sinkFactory(), core.sinkEnv(), core.protection(), core.resolvers()))
                .events(new feature.combat.MotherYijkiListener(core.bindings().sinkFactory(), core.sinkEnv(),
                        phoenix, core.protection(), core.itemViews(), core.hands(), core.resolvers()))
                .events(dimensionalTraveler)
                .events(new feature.combat.CosmicSetCombatListener(core.itemViews(), core.hands()))
                .events(new feature.combat.CosmicSetUtilityListener())
                .events(cosmicMasteries)
                .events(rotAndDecay)
                .events(cosmicWeapons)
                .events(new feature.combat.CosmicProjectileListener(core.itemViews(),
                        core.bindings().sinkFactory(), core.sinkEnv(), core.protection(), core.soulService(),
                        core.resolvers()))
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
                .boot(phoenix::start)
                .stop("dimensional traveler blocks", dimensionalTraveler::stop)
                .stop("cosmic mastery state", cosmicMasteries::stop)
                .stop("rot and decay state", rotAndDecay::stop)
                .stop("nature wrath state", natureWrath::stop)
                .stop("horrify state", horrify::stop)
                .stop("mark of the beast state", markOfTheBeast::stop)
                .stop("demonic gateway state", demonicGateway::stop)
                .stop("cosmic weapon state", cosmicWeapons::stop)
                .stop("cosmic armor summons", cosmicArmorSummons::stop)
                .stop("cosmic immortal state", cosmicImmortal::stop)
                .stop("cosmic diminish state", cosmicDiminish::stop)
                .stop("feign death state", feignDeath::stop)
                .stop("hex and unfocus state", hexUnfocus::stop)
                .stop("self destruct state", selfDestruct::stop)
                .stop("head drop marks", headDrops::stop)
                .stop("phoenix windows", phoenix::stop)
                .build();
    }
}
