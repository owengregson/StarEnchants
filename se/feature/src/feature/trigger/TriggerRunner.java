package feature.trigger;

import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import engine.pipeline.Activation;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
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
 * {@link WornState} (the safe cross-region read, §3.4), contributes the actor's passive heroic percent
 * stat to the fold (§F), walks the trigger's candidate abilities through the {@link AbilityExecutor} into
 * the caller's sink, and arms the soul gate from the actor's active gem. The caller owns the sink's
 * lifecycle (fold-onto-event / cancel / flush), since how a sink's read-backs apply differs per
 * event (a combat hit folds damage; a block-break only cancels + flushes).
 */
public final class TriggerRunner {

    private final AbilityExecutor executor;
    private final WornStateStore worn;
    private final Function<Player, Optional<SoulBinding>> soulBinder;
    private final LongSupplier nowTicks;
    private final FactPopulator factPopulator;

    /** A runner populating conditions from the built-in variable vocabulary (the production default). */
    public TriggerRunner(AbilityExecutor executor, WornStateStore worn,
                         Function<Player, Optional<SoulBinding>> soulBinder, LongSupplier nowTicks) {
        this(executor, worn, soulBinder, nowTicks, FactPopulator.builtin());
    }

    /** A runner with an explicit {@link FactPopulator} (whose vocabulary must pair with the compiler's resolver). */
    public TriggerRunner(AbilityExecutor executor, WornStateStore worn,
                         Function<Player, Optional<SoulBinding>> soulBinder, LongSupplier nowTicks,
                         FactPopulator factPopulator) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.soulBinder = Objects.requireNonNull(soulBinder, "soulBinder");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.factPopulator = Objects.requireNonNull(factPopulator, "factPopulator");
    }

    /**
     * Run {@code actor}'s {@code triggerId} abilities into {@code sink}. {@code attackSide} selects
     * which passive heroic percent stat contributes (outgoing damage vs reduction, §F); on a non-damage
     * event the fold is simply never read, so the contribution is a harmless no-op there. {@code stableKeys} is
     * the SAME snapshot's key index as {@code abilities} (pass {@code snapshot.stableKeys()}), used by
     * the executor to name an activated ability for the {@code ActivationListener}.
     */
    public void run(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                    Player actor, ActivationContext context, DispatchSink sink, StableKeyIndex stableKeys) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != generation) {
            return; // not resolved yet (or stale across a reload) — this actor contributes nothing
        }
        runResolved(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys,
                wornState, wornState.byTrigger(triggerId));
    }

    /**
     * Run an EXPLICIT candidate id list for {@code actor} (the §B REPEATING driver supplies a single
     * ability id from its timer). Same WornState gen-check + heroic fold + activation build as {@link #run},
     * but the caller chooses the candidates rather than {@code byTrigger(triggerId)} — the caller is
     * responsible that they fire on {@code triggerId} (gate 3 still enforces it).
     */
    public void runCandidates(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                              Player actor, ActivationContext context, DispatchSink sink, StableKeyIndex stableKeys,
                              int[] candidates) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != generation) {
            return; // gone or stale across a reload — a repeating task no-ops until re-armed
        }
        runResolved(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys,
                wornState, candidates);
    }

    private void runResolved(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                             Player actor, ActivationContext context, DispatchSink sink, StableKeyIndex stableKeys,
                             WornState wornState, int[] candidates) {
        if (attackSide) {
            sink.addHeroicOutgoing(wornState.heroic().percentDamage()); // §F multiplicative stage
        } else {
            sink.addHeroicReduction(wornState.heroic().percentReduction());
        }
        if (candidates.length == 0) {
            return;
        }
        long now = nowTicks.getAsLong();
        Activation.Builder builder = Activation.builder(actor.getUniqueId(), worldId, triggerId, now)
                .chanceRoll(() -> ThreadLocalRandom.current().nextDouble(100.0))
                .facts(factPopulator.populate(context, now)) // gate-7 condition facts, read on the firing thread
                .location(context.location()); // captured on the firing thread → safe for the gate-2 guard
        soulBinder.apply(actor).ifPresent(binding -> builder.soulMode(binding.gemId(), binding.balance()));
        executor.run(abilities, candidates, builder.build(), context, sink, stableKeys);
    }

    /** The interned world id for {@code world} (−1 if named in no blacklist; {@code Ability} never blocks on −1). */
    public static int worldId(Snapshot snapshot, World world) {
        return world == null ? -1 : snapshot.interners().worlds().idOf(world.getName());
    }
}
