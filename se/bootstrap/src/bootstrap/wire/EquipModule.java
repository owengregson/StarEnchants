package bootstrap.wire;

import engine.stores.RepeatStore;
import feature.combat.EquipListener;
import feature.combat.RepairGuardService;
import feature.trigger.LifecycleDriver;
import feature.trigger.MaxHealthDriver;
import feature.trigger.PassiveEffectDriver;
import feature.trigger.RepeatingDriver;
import feature.trigger.SetEquipEffects;
import feature.trigger.SetMessageDriver;
import feature.trigger.TriggerDispatch;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import platform.text.Colors;

/**
 * The §B worn-state spine (ADR-0047): the equip listener plus the four lifecycle drivers it arms — REPEATING
 * tasks, HELD/PASSIVE buffs, maintained passive potions, and set-completion messages. The drivers are exposed
 * so the reload module can re-arm every online player after a content swap.
 */
final class EquipModule {

    /** §B passive-potion maintenance sweep period (ticks): the safety-net re-derive cadence. */
    private static final long PASSIVE_SWEEP_TICKS = 40L;

    private final BootCore core;
    final RepeatingDriver passives;
    final LifecycleDriver lifecycle;
    final PassiveEffectDriver passiveEffects;
    final MaxHealthDriver maxHealth;
    private final SetMessageDriver setMessages;
    private final EquipListener equipListener;
    private final RepairGuardService repairGuard;

    EquipModule(BootCore core) {
        this.core = core;
        TriggerDispatch triggerDispatch = core.triggerDispatch();
        // §B REPEATING: one entity-owned repeating task per (player, ability), armed/torn-down by EquipListener.
        this.passives = new RepeatingDriver(triggerDispatch, core.content(),
                core.triggers().idOf("REPEATING").orElse(-1), new RepeatStore<TaskHandle>());
        // §B HELD/PASSIVE buffs that flip on/off at equip/unequip via EquipListener's worn-ability diff (ADR-0022).
        this.lifecycle = new LifecycleDriver(triggerDispatch, core.content(),
                core.triggers().idOf("HELD").orElse(-1), core.triggers().idOf("PASSIVE").orElse(-1));
        // §B maintained passive POTION buffs: permanent-while-worn + suppression-aware + self-healing.
        this.passiveEffects = new PassiveEffectDriver(triggerDispatch, core.content(), core.worn(),
                core.stores().suppression(), core.tick()::get,
                core.triggers().idOf("HELD").orElse(-1), core.triggers().idOf("PASSIVE").orElse(-1));
        // Worn HEALTH bonuses: one reconciled plugin-owned max-health modifier (the potion driver's twin) —
        // crash/relog-proof by identity, and immune to the HEALTH_BOOST amplifier clobber (1.8.0).
        this.maxHealth = new MaxHealthDriver(triggerDispatch, core.content(), core.worn(),
                core.stores().suppression(), core.tick()::get,
                core.triggers().idOf("HELD").orElse(-1), core.triggers().idOf("PASSIVE").orElse(-1));
        // §6.6 set equip/remove: the authored per-set message on a completion transition PLUS the universal
        // equip/unequip sound+particle (one config for all sets; the dust takes the set's own colour).
        this.setMessages = new SetMessageDriver(core.content(),
                (player, msg) -> { // split on \n (keep trailing empties) so a leading AND trailing blank line render
                    for (String line : Colors.translate(msg).split("\n", -1)) {
                        player.sendMessage(line);
                    }
                },
                () -> core.master().config().sets().messageUppercase(), // read live so a reload can flip it
                new SetEquipEffects(() -> core.master().config().sets(), core.particleFx(), core.sounds()));
        // The shared worn-state refresher (join/held/respawn/quit + hand-mutation feeders); the era armour-/hand-change
        // feeders drive its refresh. The EquipSource + ItemViewCache back the F09 hand-signature reconcile.
        this.equipListener = new EquipListener(core.worn(), core.content(), passives, lifecycle, passiveEffects,
                maxHealth, setMessages, core.bindings().equipSource(), core.itemViews(), core.tick()::get);
        this.repairGuard = new RepairGuardService(core.bindings().equipSource(), core.itemViews(),
                core.bindings().sinkFactory(), core.sinkEnv(), core.resolvers(), core.tick()::get);
        this.equipListener.onUnequip(repairGuard::onUnequip);
        this.equipListener.onRefresh(repairGuard::refresh);
    }

    /** The one worn-state refresher, for features whose state feeds the resolve (ADR-0052 pets). */
    java.util.function.Consumer<org.bukkit.entity.Player> refresher() {
        return equipListener::refresh;
    }

    /** Subscribe a hook to the END of every worn-state refresh (ADR-0053: the mask illusion re-derive). */
    void onRefresh(java.util.function.Consumer<org.bukkit.entity.Player> hook) {
        equipListener.onRefresh(hook);
    }

    FeatureModule module() {
        return FeatureModule.named("equip")
                .events(equipListener)
                .events(repairGuard)
                .events(core.bindings().armourChangeFeeder(equipListener))
                .events(core.bindings().handChangeFeeder(equipListener))
                // §B instant DISABLE: drop a suppressed player's passive buffs at once, restore at window end.
                .install("suppression-refresh hook", () -> core.stores().suppression().onSuppress(
                        (playerId, durationTicks) -> {
                            Player target = core.plugin().getServer().getPlayer(playerId);
                            if (target != null) {
                                Scheduling.onEntity(target, () -> {
                                    passiveEffects.refresh(target);
                                    maxHealth.refresh(target); // a suppressed HEALTH bonus drops with its potions
                                });
                                Scheduling.onEntityLater(target, durationTicks + 1L, () -> {
                                    passiveEffects.refresh(target);
                                    maxHealth.refresh(target);
                                });
                            }
                        }))
                // Arm players already online (a plugin /reload with players on); a fresh boot has none.
                .boot(() -> {
                    for (Player player : core.plugin().getServer().getOnlinePlayers()) {
                        Scheduling.onEntity(player, () -> {
                            var state = core.worn().refresh(player, core.content().snapshot());
                            passives.arm(player, state);
                            lifecycle.refresh(player, state);
                            passiveEffects.refresh(player);
                            maxHealth.refresh(player);
                            repairGuard.refresh(player);
                        });
                    }
                })
                // §B passive-potion maintenance sweep: the safety net (equip/respawn/suppression refresh instantly).
                // It also carries the F09 hand-signature reconcile — a drifted hand (a /clear'd or effect-swapped
                // item that fired no event) refreshes fully here, so we skip the redundant passive re-derive then.
                .boot(() -> Scheduling.repeatingGlobal(PASSIVE_SWEEP_TICKS, PASSIVE_SWEEP_TICKS, () -> {
                    for (Player player : core.plugin().getServer().getOnlinePlayers()) {
                        Scheduling.onEntity(player, () -> {
                            if (!equipListener.reconcile(player)) {
                                passiveEffects.refresh(player);
                                maxHealth.refresh(player); // suppression windows lapse between refreshes too
                            }
                        });
                    }
                }))
                .stop("REPEATING tasks", passives::disarmAll)
                .stop("HELD/PASSIVE buffs", lifecycle::clearAll)
                .stop("maintained passives", passiveEffects::clearAll)
                .lang("set")
                .build();
    }
}
