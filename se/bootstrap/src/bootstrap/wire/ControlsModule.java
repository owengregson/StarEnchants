package bootstrap.wire;

import engine.sink.TempBlockLedger;
import feature.combat.FoodWindowListener;
import feature.combat.HeadTrophyListener;
import feature.combat.HellfireFloorListener;
import feature.combat.ImmuneListener;
import feature.combat.KeepOnDeathListener;
import feature.combat.MentalComboBridge;
import feature.combat.MentalKnockbackBridge;
import feature.combat.TempBlockGuardListener;
import feature.combat.TempEquipListener;
import feature.combat.TeleblockListener;
import feature.combat.TimedRevertListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import platform.sched.Scheduling;

/**
 * The exotic-effect ports + knockback control (ADR-0047): EQUIP_SWAP, the hellfire magma floor, KEEP_ON_DEATH,
 * TELEBLOCK/IMMUNE, and the capability-probed KNOCKBACK_CONTROL applier plus its Mental coordination bridge.
 * {@code EngineStoreListener} is NOT here — it moves to the stores module after IMMUNE (a proven-disjoint move).
 */
final class ControlsModule {

    private final BootCore core;

    ControlsModule(BootCore core) {
        this.core = core;
    }

    FeatureModule module() {
        return FeatureModule.named("controls")
                // EQUIP_SWAP (spooky's pumpkin helmet) — keep death/quit normal: restore the real piece.
                .events(new TempEquipListener())
                // Revert-on-quit for timed FLY/INVINCIBLE/MOVEMENT_SPEED + max-health drain, so a logout mid-window
                // can't strand the flag on disk (F07/F08); the Sink's expiry timer still covers the normal case.
                .events(new TimedRevertListener(core.sinkEnv().timedReverts()))
                // Temp blocks are cosmetic, unbreakable, and immovable (F25/F26): the guard cancels the break and
                // restores the captured original (no free drop, no deleted ground), refuses pistons/flow/fade/burn
                // on a tracked tile, excises it from explosions, and re-arms a Folia-dropped revert on chunk load.
                .events(new TempBlockGuardListener(core.sinkEnv().tempBlocks(), core.tick()::get))
                // F29 belt-and-braces: a loaded-chunk revert task that was retired (e.g. a dead region) leaves the
                // temp tile past its deadline; a periodic global sweep hops to each still-loaded tile's owning region
                // and force-reverts any that expired. Tiles in UNLOADED chunks are deliberately skipped and retained
                // — the in-memory entry is the only path back to the original; chunk-load re-arm reclaims them.
                .boot(() -> {
                    int period = core.master().config().engine().tempBlockSweepTicks();
                    if (period <= 0) {
                        return;
                    }
                    TempBlockLedger<BlockState> ledger = core.sinkEnv().tempBlocks();
                    Scheduling.repeatingGlobal(period, period, () -> ledger.forEachTile((world, x, y, z) -> {
                        World w = Bukkit.getWorld(world);
                        if (w != null && w.isChunkLoaded(x >> 4, z >> 4)) {
                            Scheduling.onRegion(new Location(w, x, y, z),
                                    () -> ledger.healIfExpired(world, x, y, z, core.tick().get()));
                        }
                    }));
                })
                // Magma floor (devil's Hell's Kitchen) scorches the scene, not the health: cancel HOT_FLOOR in a zone.
                .events(new HellfireFloorListener())
                // §C KEEP_ON_DEATH at NORMAL — earlier than HolyScrollListener (HIGH) — so a kept death spends no scroll.
                .events(new KeepOnDeathListener(core.stores().keepOnDeath(), core.tick()::get))
                // HEAD_TROPHY at MONITOR — after every keep/save decision above, so the head is added to the
                // drops that actually survive and a kept death spends no trophy.
                .events(new HeadTrophyListener(core.stores().headTrophies(), core.bindings().texturedHeads(),
                        core.hands()))
                // Cosmic Enchants exotic-effect ports: TELEBLOCK cancels teleport, IMMUNE cancels damage while flagged.
                .events(new TeleblockListener(core.stores().teleblock(), core.tick()::get))
                .events(new ImmuneListener(core.stores().immune(), core.tick()::get, core.hands()))
                // MODIFY_FOOD's window modes: the one hunger bridge, reading FoodLevelChangeEvent — where a
                // meal's nutrition and every exhaustion drain land, on a later tick than the activation.
                .events(new FoodWindowListener(core.stores().foodWindows(), core.tick()::get))
                // POTION_LOCK guard: modern cancels EntityPotionEffectEvent for a locked type (genuine denial, so a
                // passive re-applier cannot make the buff flash back); 1.8 binding is inert (the Sink re-strip enforces).
                .events(core.bindings().potionLockGuard())
                // FREEZE guard (ADR-0065): cancel vanilla freeze self-damage inside a frozen window + join-reconcile
                // a crash-stranded freeze lock; 1.8 binding is inert. The stop runs every live window's teardown
                // (unlock + thaw + strip the slow) best-effort — the SwarmSpawns disable-sweep shape.
                .events(core.bindings().freezeDamageGuard())
                .stop("frozen windows", engine.sink.FrozenTargets::teardownAll)
                // §C KNOCKBACK_CONTROL: capability-probed onto modern-bukkit or legacy destroystokyo; inert on neither.
                .install("KNOCKBACK_CONTROL applier", () -> {
                    var path = core.bindings().registerKnockback(core.plugin(), core.stores().knockback(),
                            core.tick()::get);
                    core.plugin().getLogger().info("KNOCKBACK_CONTROL applier: " + path);
                })
                // §N (ADR-0026): Mental OWNS player knockback, so bind its event so KNOCKBACK_CONTROL composes on.
                .install("Mental knockback coordination", () -> {
                    var path = MentalKnockbackBridge.register(core.plugin(), core.stores().knockback(),
                            core.tick()::get, core.master().config().integrations().enabled("mental"));
                    core.plugin().getLogger().info("Mental knockback coordination: " + path);
                })
                // ADR-0069: park SE DoT ticks while Mental holds a combo on the victim; flush into the next hit /
                // combo end. All Mental reads stay inside the lambda (RegistryWiringTest constructs with mocks).
                .install("Mental combo-DoT sync", () -> {
                    var path = MentalComboBridge.register(core.plugin(), core.sinkEnv().dotPark(),
                            core.dispatch().dotRelease(), core.tick()::get,
                            core.master().config().integrations().enabled("mental")
                                    && core.master().config().integrations().enabled("mental-combo-sync"));
                    core.plugin().getLogger().info("Mental combo-DoT sync: " + path);
                })
                .build();
    }
}
