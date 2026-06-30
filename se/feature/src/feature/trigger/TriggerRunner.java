package feature.trigger;

import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import engine.pipeline.Activation;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
import engine.sink.SinkReadback;
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
 * The "run one trigger pass for one actor into a {@link SinkReadback}" primitive shared by every dispatcher
 * (§3.3), combat and non-combat alike. Reads the actor's PRE-RESOLVED {@link WornState} (the safe
 * cross-region read, §3.4), contributes the passive heroic percent to the fold (§F), and arms the soul gate
 * from the active gem. The CALLER owns the sink lifecycle, since read-back application differs per event.
 */
public final class TriggerRunner {

    private final AbilityExecutor executor;
    private final WornStateStore worn;
    private final Function<Player, Optional<SoulBinding>> soulBinder;
    private final LongSupplier nowTicks;
    private final FactPopulator factPopulator;

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
     * Run {@code actor}'s {@code triggerId} abilities into {@code sink}. {@code attackSide} selects which heroic
     * percent contributes (outgoing vs reduction, §F); harmless on a non-damage event (the fold is never read).
     * {@code stableKeys} MUST be the same snapshot's key index as {@code abilities}.
     */
    public void run(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                    Player actor, ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys) {
        run(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys, true);
    }

    /**
     * As {@link #run} but {@code applyHeroic} false runs the abilities WITHOUT adding the worn heroic percent
     * to the sink — the environmental path (FALL/FIRE) only softens non-entity damage under
     * {@code reduction-scope == ALL}; the entity/PvP path always passes true (§F).
     */
    public void run(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                    Player actor, ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                    boolean applyHeroic) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != generation) {
            return; // unresolved or stale across a reload — contribute nothing
        }
        runResolved(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys,
                wornState, wornState.byTrigger(triggerId), applyHeroic);
    }

    /**
     * Contribute ONLY the worn heroic reduction to {@code sink} (no trigger abilities) — environmental damage
     * with no trigger, softened under {@code reduction-scope: ALL} (§F). No-op until the WornState is resolved.
     */
    public void contributeHeroicReduction(int generation, Player actor, SinkReadback sink) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState != null && wornState.gen() == generation) {
            sink.addHeroicReduction(wornState.heroic().percentReduction());
            sink.addFlatReduction(wornState.heroic().flatReduction()); // §F diamond armour delta, under reduction-scope ALL
        }
    }

    /**
     * Run an EXPLICIT candidate id list (the §B REPEATING driver supplies one ability id from its timer).
     * Caller chooses the candidates rather than {@code byTrigger(triggerId)} and is responsible they fire on
     * {@code triggerId} (gate 3 still enforces it).
     */
    public void runCandidates(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                              Player actor, ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                              int[] candidates) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != generation) {
            return; // gone or stale — a repeating task no-ops until re-armed
        }
        runResolved(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys,
                wornState, candidates, true);
    }

    private void runResolved(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                             Player actor, ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                             WornState wornState, int[] candidates, boolean applyHeroic) {
        if (applyHeroic) {
            if (attackSide) {
                sink.addHeroicOutgoing(wornState.heroic().percentDamage()); // §F multiplicative stage
                sink.addFlatDamage(wornState.heroic().flatDamage());        // §F diamond base-attack delta (gold→diamond)
            } else {
                sink.addHeroicReduction(wornState.heroic().percentReduction());
                sink.addFlatReduction(wornState.heroic().flatReduction());  // §F diamond armour delta
            }
        }
        if (candidates.length == 0) {
            return;
        }
        long now = nowTicks.getAsLong();
        Activation.Builder builder = Activation.builder(actor.getUniqueId(), worldId, triggerId, now)
                // nextDouble() * 100, NOT nextDouble(100.0): the bounded overload resolves through the JDK-17
                // RandomGenerator interface, which JvmDowngrader cannot stub for the optional Java-8 (1.8) jar
                // (it emits a MissingStubError throw). The no-arg nextDouble() is ancient Random API — same
                // uniform [0,100) result, untouched by the downgrade. Verified live by the legacy smoke combat check.
                .chanceRoll(() -> ThreadLocalRandom.current().nextDouble() * 100.0)
                .facts(factPopulator.populate(context, now)) // gate-7 condition facts, read on the firing thread
                .location(context.location()) // captured on the firing thread → safe for the gate-2 guard
                // Cooldown buckets: the other combat party's kind (player vs mob) routes the cooldown, so an
                // ability proc'd on a mob and on a player run on two independent cooldowns (gates 6 + 11).
                .targetBucket(context.victim() instanceof Player ? 1 : 0);
        soulBinder.apply(actor).ifPresent(binding -> builder.soulMode(binding.marker()));
        executor.run(abilities, candidates, builder.build(), context, sink, stableKeys);
    }

    /** The interned world id for {@code world} (−1 if named in no blacklist; {@code Ability} never blocks on −1). */
    public static int worldId(Snapshot snapshot, World world) {
        return world == null ? -1 : snapshot.interners().worlds().idOf(world.getName());
    }
}
