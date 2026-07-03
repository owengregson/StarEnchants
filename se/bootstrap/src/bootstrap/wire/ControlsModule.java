package bootstrap.wire;

import feature.combat.HellfireFloorListener;
import feature.combat.ImmuneListener;
import feature.combat.KeepOnDeathListener;
import feature.combat.MentalKnockbackBridge;
import feature.combat.TempEquipListener;
import feature.combat.TeleblockListener;

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
                // Magma floor (devil's Hell's Kitchen) scorches the scene, not the health: cancel HOT_FLOOR in a zone.
                .events(new HellfireFloorListener())
                // §C KEEP_ON_DEATH at NORMAL — earlier than HolyScrollListener (HIGH) — so a kept death spends no scroll.
                .events(new KeepOnDeathListener(core.stores().keepOnDeath(), core.tick()::get))
                // Cosmic Enchants exotic-effect ports: TELEBLOCK cancels teleport, IMMUNE cancels damage while flagged.
                .events(new TeleblockListener(core.stores().teleblock(), core.tick()::get))
                .events(new ImmuneListener(core.stores().immune(), core.tick()::get, core.hands()))
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
                .build();
    }
}
