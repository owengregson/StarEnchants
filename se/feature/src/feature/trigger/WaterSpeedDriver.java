package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.stores.SuppressionStore;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;

/**
 * Maintains worn {@code WATER_SPEED} bonuses (ADR-0060) as one reconciled, plugin-owned
 * {@code water_movement_efficiency} modifier — the {@link MaxHealthDriver} twin. Each refresh re-derives
 * the expected total from live {@link WornState} + live suppression and SETS the modifier to it (never
 * adds), so relogs, crashes and stacked sources can neither compound nor strand a boost. Subscribed to the
 * end-of-refresh hook by the pets module; a suppression window applied mid-refresh lands on the next full
 * refresh (hotbar change / join / respawn) — an accepted lag for a non-combat mobility stat.
 *
 * <p>Stateless (the modifier's fixed identity IS the state). Folia-correct: {@link #refresh} runs on the
 * player's own region thread (the refresh-hook contract).
 */
public final class WaterSpeedDriver {

    private final TriggerDispatch dispatch;
    private final ContentHolder content;
    private final WornStateStore worn;
    private final SuppressionStore suppression;
    private final LongSupplier nowTicks;
    private final int held;    // -1 if HELD is absent from the vocabulary
    private final int passive; // -1 if PASSIVE is absent from the vocabulary

    public WaterSpeedDriver(TriggerDispatch dispatch, ContentHolder content, WornStateStore worn,
                            SuppressionStore suppression, LongSupplier nowTicks, int held, int passive) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.content = Objects.requireNonNull(content, "content");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.held = held;
        this.passive = passive;
    }

    /** Reconcile {@code player}'s worn water-speed modifier with their live worn state + suppression.
     *  Must run on the player's own thread. Idempotent — SET to the computed total ({@code 0} removes it). */
    public void refresh(Player player) {
        if (held < 0 && passive < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        WornState state = worn.get(player.getUniqueId());
        double expected = computeExpected(state, snapshot, suppression, player.getUniqueId(),
                nowTicks.getAsLong(), held, passive);
        dispatch.applyWornWaterSpeed(player, expected);
    }

    /**
     * The summed {@code WATER_SPEED} efficiencies a player's worn PASSIVE and HELD abilities currently
     * grant, excluding suppressed abilities, clamped to the attribute's [0,1] domain. Pure (no Bukkit).
     * A stale worn state (different snapshot generation) yields 0.
     */
    static double computeExpected(WornState state, Snapshot snapshot, SuppressionStore suppression,
                                  UUID player, long now, int held, int passive) {
        if (state == null || state.gen() != snapshot.generation()) {
            return 0.0;
        }
        Ability[] abilities = snapshot.abilities();
        double total = sum(state, held, abilities, suppression, player, now)
                + sum(state, passive, abilities, suppression, player, now);
        return Math.min(1.0, total);
    }

    private static double sum(WornState state, int trigger, Ability[] abilities,
                              SuppressionStore suppression, UUID player, long now) {
        if (trigger < 0) {
            return 0.0;
        }
        double total = 0.0;
        for (int abilityId : state.byTrigger(trigger)) {
            if (abilityId < 0 || abilityId >= abilities.length) {
                continue;
            }
            Ability ability = abilities[abilityId];
            if (suppression.suppressesAny(ability, player, now)) {
                continue; // a DISABLE'd passive grants nothing; the window's end restores it
            }
            for (CompiledEffect effect : ability.effects()) {
                if ("WATER_SPEED".equals(effect.head())) {
                    total += effect.args().dbl("efficiency");
                }
            }
        }
        return total;
    }
}
