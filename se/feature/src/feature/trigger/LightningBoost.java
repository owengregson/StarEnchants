package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.stores.SuppressionStore;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;

/**
 * The worn {@code LIGHTNING_MOD} channel (ADR-0063) — the {@link WaterSpeedDriver} twin, read on demand:
 * {@link #fn} resolves an actor's summed boost FRACTION from live {@link WornState} + live suppression at
 * the sink's bolt emit (nothing to reconcile between procs, so no driver). The store/snapshot reads are the
 * same cross-thread reads the combat path already performs.
 */
public final class LightningBoost {

    private LightningBoost() {
    }

    /** The {@code SinkEnv.lightningBoost} wiring: actor UUID → summed worn boost fraction (0.10 = +10%). */
    public static ToDoubleFunction<UUID> fn(ContentHolder content, WornStateStore worn,
                                            SuppressionStore suppression, LongSupplier nowTicks, int passive) {
        return id -> {
            Snapshot snapshot = content.snapshot();
            return compute(worn.get(id), snapshot, suppression, id, nowTicks.getAsLong(), passive);
        };
    }

    /**
     * The summed {@code LIGHTNING_MOD} percents a player's worn PASSIVE abilities grant, as a fraction,
     * excluding suppressed abilities. Pure (no Bukkit); an absent/stale worn state yields 0.
     */
    static double compute(WornState state, Snapshot snapshot, SuppressionStore suppression,
                          UUID player, long now, int passive) {
        if (passive < 0 || state == null || state.gen() != snapshot.generation()) {
            return 0.0;
        }
        Ability[] abilities = snapshot.abilities();
        double totalPercent = 0.0;
        for (int abilityId : state.byTrigger(passive)) {
            if (abilityId < 0 || abilityId >= abilities.length) {
                continue;
            }
            Ability ability = abilities[abilityId];
            if (suppression.suppressesAny(ability, player, now)) {
                continue; // a DISABLE'd source grants nothing; the window's end restores it
            }
            for (CompiledEffect effect : ability.effects()) {
                if ("LIGHTNING_MOD".equals(effect.head())) {
                    totalPercent += effect.args().dbl("amount");
                }
            }
        }
        return totalPercent / 100.0;
    }
}
