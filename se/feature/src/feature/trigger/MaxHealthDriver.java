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
 * Maintains PASSIVE/HELD <em>worn max-health</em> bonuses ({@code HEALTH} + {@code @Self}) as one reconciled,
 * plugin-owned attribute modifier — the {@link PassiveEffectDriver} twin for hearts. Each refresh re-derives
 * the expected total from live {@link WornState} + live suppression and SETS the modifier to it (never adds),
 * so relogs, crashes, reloads and stacked sources can neither compound nor strand a bonus: whatever stale
 * modifier playerdata carries is replaced by identity on the next refresh. The executor's lifecycle path
 * skips {@code HEALTH}+SELF for exactly this reason (single-channel ownership).
 *
 * <p>Why a modifier and not the potion effect: two sources maintaining {@code HEALTH_BOOST} clobber each other
 * (amplifier-max wins — the Nature-crystal-under-Godly-Overload bug), and a potion can be milked/cleared. Only
 * Overload/Godly Overload stay on the potion pool by design (Grim strips it via {@code POTION_LOCK}).
 *
 * <p>Stateless (no ledger): the modifier's fixed identity IS the state. Folia-correct: {@link #refresh} runs on
 * the player's own region thread (driven by the equip listener, respawn, the periodic sweep, suppression hooks).
 */
public final class MaxHealthDriver {

    private final TriggerDispatch dispatch;
    private final ContentHolder content;
    private final WornStateStore worn;
    private final SuppressionStore suppression;
    private final LongSupplier nowTicks;
    private final int held;    // -1 if HELD is absent from the vocabulary
    private final int passive; // -1 if PASSIVE is absent from the vocabulary

    public MaxHealthDriver(TriggerDispatch dispatch, ContentHolder content, WornStateStore worn,
                           SuppressionStore suppression, LongSupplier nowTicks, int held, int passive) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.content = Objects.requireNonNull(content, "content");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.held = held;
        this.passive = passive;
    }

    /**
     * Reconcile {@code player}'s worn max-health modifier with their live worn state + suppression. Must run
     * on the player's own thread. Idempotent — the modifier is SET to the computed total ({@code 0} removes it).
     */
    public void refresh(Player player) {
        if (held < 0 && passive < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        WornState state = worn.get(player.getUniqueId());
        double expected = computeExpected(state, snapshot, suppression, player.getUniqueId(),
                nowTicks.getAsLong(), held, passive);
        dispatch.applyWornMaxHealth(player, expected);
    }

    /**
     * The summed {@code HEALTH}+{@code @Self} amounts a player's worn PASSIVE and HELD abilities currently
     * grant, excluding suppressed abilities (the gate-5 scopes, KIND included). Additive across sources —
     * a Nature crystal and a Santa mask stack, unlike the amplifier-max potion pool. Pure (no Bukkit) —
     * unit-testable. A stale worn state (different snapshot generation) yields 0; the next equip/sweep
     * re-derives against the live one.
     */
    static double computeExpected(WornState state, Snapshot snapshot, SuppressionStore suppression,
                                  UUID player, long now, int held, int passive) {
        if (state == null || state.gen() != snapshot.generation()) {
            return 0.0;
        }
        Ability[] abilities = snapshot.abilities();
        return sum(state, held, abilities, suppression, player, now)
                + sum(state, passive, abilities, suppression, player, now);
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
                continue; // a DISABLE'd passive grants nothing; the window's refresh restores it after
            }
            for (CompiledEffect effect : ability.effects()) {
                if ("HEALTH".equals(effect.head()) && "SELF".equals(effect.target().head())) {
                    total += effect.args().dbl("amount");
                }
            }
        }
        return total;
    }
}
