package bootstrap.wire;

import engine.sink.CombatTag;
import engine.sink.DamageMarks;
import engine.sink.FallingBlockCasts;
import engine.sink.GuardianCasts;
import engine.sink.OwnerZones;
import engine.sink.ReducedPotions;
import engine.sink.StackingDots;
import engine.sink.TempEquip;
import engine.sink.TurretCasts;

/**
 * The structural quit-cleanup authority (ADR-0047): declares the {@link Wire.QuitSweep} slot the fold
 * materializes into an {@code EngineStoreListener} over {@code EngineStores} plus every module's declared
 * player stores. Also owns the onDisable stops for the cross-event effect-port sink statics.
 */
final class StoresModule {

    StoresModule(BootCore core) {
        // no construction — EngineStores is the engine spine's (BootCore); the sweep is fold-materialized.
    }

    FeatureModule module() {
        return FeatureModule.named("stores")
                .quitSweep()
                .stop("falling-block casts", FallingBlockCasts::clearAll) // in-flight falling-block impact bindings
                .stop("guardian casts", GuardianCasts::clearAll)          // summoned-guardian owner bindings (Blood Link)
                .stop("combat tags", CombatTag::clearAll)                 // supreme's out-of-combat fly
                .stop("damage marks", DamageMarks::clearAll)              // reaper's Mark of the Reaper
                .stop("owner zones", OwnerZones::clearAll)                // devil's Hell's Kitchen hellfire zones
                .stop("temp equips", TempEquip::clearAll)                 // spooky's pumpkin helmet
                .stop("decay ladders", StackingDots::clearAll)            // rot's per-victim STACKING_DOT stacks
                .stop("turret casts", TurretCasts::clearAll)              // live TURRET_RING emplacements + their shots
                .stop("potion reductions", ReducedPotions::clearAll)      // mortal coil's POTION_AMP_REDUCE claims
                .build();
    }
}
