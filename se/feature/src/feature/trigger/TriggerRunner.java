package feature.trigger;

import compile.model.Ability;
import compile.model.Snapshot;
import engine.pipeline.Activation;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.sink.DispatchSink;
import feature.soul.SoulBinding;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The single "run one trigger pass for one actor into a {@link DispatchSink}" primitive shared by
 * every trigger dispatcher (docs/architecture.md §3.3) — combat ({@code CombatDispatch}) and the
 * non-combat event listeners ({@code TriggerDispatch}) alike. It reads the actor's PRE-RESOLVED
 * {@link WornState} (the safe cross-region read, §3.4), contributes the actor's passive heroic flat
 * stat to the fold, walks the trigger's candidate abilities through the {@link AbilityExecutor} into
 * the caller's sink, and arms the soul gate from the actor's active gem. The caller owns the sink's
 * lifecycle (fold-onto-event / cancel / flush), since how a sink's read-backs apply differs per
 * event (a combat hit folds damage; a block-break only cancels + flushes).
 */
public final class TriggerRunner {

    private final AbilityExecutor executor;
    private final WornStateStore worn;
    private final Function<Player, Optional<SoulBinding>> soulBinder;
    private final LongSupplier nowTicks;

    public TriggerRunner(AbilityExecutor executor, WornStateStore worn,
                         Function<Player, Optional<SoulBinding>> soulBinder, LongSupplier nowTicks) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.soulBinder = Objects.requireNonNull(soulBinder, "soulBinder");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    /**
     * Run {@code actor}'s {@code triggerId} abilities into {@code sink}. {@code attackSide} selects
     * which passive heroic flat stat contributes (damage vs reduction); on a non-damage event the
     * fold is simply never read, so the contribution is a harmless no-op there.
     */
    public void run(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                    Player actor, ActivationContext context, DispatchSink sink) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != generation) {
            return; // not resolved yet (or stale across a reload) — this actor contributes nothing
        }
        if (attackSide) {
            sink.addFlatDamage(wornState.heroic().flatDamage());
        } else {
            sink.addFlatReduction(wornState.heroic().flatReduction());
        }
        int[] candidates = wornState.byTrigger(triggerId);
        if (candidates.length == 0) {
            return;
        }
        Activation.Builder builder = Activation.builder(actor.getUniqueId(), worldId, triggerId, nowTicks.getAsLong())
                .chanceRoll(() -> ThreadLocalRandom.current().nextDouble(100.0));
        soulBinder.apply(actor).ifPresent(binding -> builder.soulMode(binding.gemId(), binding.balance()));
        executor.run(abilities, candidates, builder.build(), context, sink);
    }

    /** The interned world id for {@code world} (−1 if named in no blacklist; {@code Ability} never blocks on −1). */
    public static int worldId(Snapshot snapshot, World world) {
        return world == null ? -1 : snapshot.interners().worlds().idOf(world.getName());
    }
}
