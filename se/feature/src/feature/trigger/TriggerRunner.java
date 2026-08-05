package feature.trigger;

import compile.model.Ability;
import compile.model.FactMask;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import engine.interact.ReboundPlan;
import engine.pipeline.Activation;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
import engine.run.UseAttempt;
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

    /** A runner with an explicit {@link FactPopulator} (whose vocabulary must pair with the compiler's resolver). */
    public TriggerRunner(AbilityExecutor executor, WornStateStore worn,
                         Function<Player, Optional<SoulBinding>> soulBinder, LongSupplier nowTicks,
                         FactPopulator factPopulator) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.soulBinder = Objects.requireNonNull(soulBinder, "soulBinder");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.factPopulator = Objects.requireNonNull(factPopulator, "factPopulator");
        // The composition root owns every RNG the pipeline draws from: the same class that supplies the chance
        // roll supplies rand()'s draw, so an expression is never seeded by a hidden ThreadLocalRandom in the engine.
        this.factPopulator.randomSource(() -> ThreadLocalRandom.current().nextDouble());
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
        run(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys,
                applyHeroic, null);
    }

    /**
     * As {@link #run} but carrying the VICTIM's PROC_REBOUND arbiter, which gate 9 consults per ability
     * (ReboundGate). Attack side ONLY — on the defence walk the plan would claim the victim's own abilities.
     */
    public void run(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                    Player actor, ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                    boolean applyHeroic, ReboundPlan rebound) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != generation) {
            return; // unresolved or stale across a reload — contribute nothing
        }
        runResolved(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys,
                wornState, wornState.byTrigger(triggerId), applyHeroic, rebound);
    }

    /**
     * Contribute ONLY the worn heroic reduction to {@code sink} (no trigger abilities) — environmental damage
     * with no trigger, softened under {@code reduction-scope: ALL} (§F). No-op until the WornState is resolved.
     */
    public void contributeHeroicReduction(int generation, Player actor, SinkReadback sink) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState != null && wornState.gen() == generation) {
            // Heroic-tagged adders (ADR-0053): folded identically unless the hit set ignoreHeroic (§F, ADR-0037).
            sink.addHeroicReduction(wornState.heroic().percentReduction());
            sink.addHeroicFlatReduction(wornState.heroic().flatReduction()); // §F diamond armour delta, under reduction-scope ALL
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
                wornState, candidates, true, null);
    }

    /**
     * Run {@code triggerId}'s worn abilities RESTRICTED to those declaring the interned cooldown-scope GROUP
     * {@code scopeGroup} — IMPACT source scoping (ADR-0074). The identity is the authored {@code group:} because
     * it is the only one an ARM and its PAYLOAD share: they are two separate authored bonuses, so a per-ability
     * id would match neither.
     *
     * <p>A sibling of {@link #runCandidates} rather than a caller of it: the candidate list is derived FROM the
     * worn state, and routing through that method would take a second lookup whose staleness check could
     * disagree with this one's. Nothing else differs — same gates, same order, no new gate.
     */
    public void runGrouped(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                           Player actor, ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                           int scopeGroup) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != generation) {
            return; // unresolved or stale across a reload — contribute nothing
        }
        int[] candidates = wornState.byTrigger(triggerId);
        if (scopeGroup >= 0) {
            candidates = withGroup(abilities, candidates, scopeGroup);
        }
        runResolved(abilities, generation, worldId, triggerId, attackSide, actor, context, sink, stableKeys,
                wornState, candidates, true, null);
    }

    /** The subset of {@code candidates} whose ability declares {@code scopeGroup}; the shared empty array for none. */
    static int[] withGroup(Ability[] abilities, int[] candidates, int scopeGroup) {
        int kept = 0;
        for (int id : candidates) {
            if (abilities[id].cdScopeGroup() == scopeGroup) {
                kept++;
            }
        }
        if (kept == candidates.length) {
            return candidates; // every candidate matched — hand back the worn state's own array, allocate nothing
        }
        if (kept == 0) {
            return NO_CANDIDATES;
        }
        int[] filtered = new int[kept];
        int at = 0;
        for (int id : candidates) {
            if (abilities[id].cdScopeGroup() == scopeGroup) {
                filtered[at++] = id;
            }
        }
        return filtered;
    }

    private static final int[] NO_CANDIDATES = new int[0];

    /**
     * The COLD equipment-transition entry point: run an EXPLICIT candidate list that is NOT read from the worn
     * state. An UNEQUIP walk's ability has already left that state and took its {@code FactMask} bits with it, so
     * gating on the post-refresh mask would read every authored fact as its default; this resolves the FULL mask
     * instead (a safe superset, and this path runs once per equipment change, never per hit). No worn lookup at
     * all — hence no generation, the caller having already rejected a stale state — and no heroic fold, since
     * nothing here folds onto a damage event. The CALLER owns the sink lifecycle.
     */
    public void runDetached(Ability[] abilities, int worldId, int triggerId, Player actor,
                            ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                            int[] candidates) {
        if (candidates.length == 0) {
            return;
        }
        long now = nowTicks.getAsLong();
        Activation.Builder builder = Activation.builder(actor.getUniqueId(), worldId, triggerId, now)
                .chanceRoll(() -> ThreadLocalRandom.current().nextDouble() * 100.0)
                .facts(factPopulator.populate(context, now, FactMask.ALL))
                .location(context.location())
                .targetBucket(context.victim() instanceof Player ? 1 : 0)
                .victimId(context.victim() == null ? null : context.victim().getUniqueId());
        soulBinder.apply(actor).ifPresent(binding -> builder.soulMode(binding.marker()));
        executor.run(abilities, candidates, builder.build(), context, sink, stableKeys);
    }

    /**
     * The COLD use-item entry point (§3.6): run a held use-item's EXPLICIT candidate abilities on {@code USE} and
     * report a compact {@link UseAttempt}. Unlike the trigger paths this does NOT read {@code byTrigger} or apply
     * the worn heroic fold — a use-item's abilities live on the held item, not worn gear — and it resolves the full
     * {@link FactMask} (cold path, a safe superset) so any authored condition's facts are populated. The Activation
     * is built identically to {@link #runResolved} (chance supplier, facts, location, target bucket, soul mode);
     * the CALLER owns the sink lifecycle (flush after this returns).
     */
    public UseAttempt runUse(Ability[] abilities, int generation, int worldId, int triggerId, Player actor,
                             ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys, int[] candidates) {
        long now = nowTicks.getAsLong();
        Activation.Builder builder = Activation.builder(actor.getUniqueId(), worldId, triggerId, now)
                .chanceRoll(() -> ThreadLocalRandom.current().nextDouble() * 100.0)
                .facts(factPopulator.populate(context, now, FactMask.ALL))
                .location(context.location())
                .targetBucket(context.victim() instanceof Player ? 1 : 0)
                .victimId(context.victim() == null ? null : context.victim().getUniqueId());
        soulBinder.apply(actor).ifPresent(binding -> builder.soulMode(binding.marker()));
        return executor.runUse(abilities, candidates, builder.build(), context, sink, stableKeys);
    }

    /**
     * The COLD swapped re-execution entry point (PROC_REBOUND): run the abilities gate 9 took off the
     * reflector against a context whose roles are swapped. No worn lookup (these ids are the ATTACKER's
     * abilities, which the reflector does not carry and never will), no heroic fold (the reflector's heroic
     * armour is already priced on the incoming hit), and no soul binding — nothing here is charged to
     * anyone. Resolves the FULL {@link FactMask}: a cold path, so a safe superset beats gating on a mask
     * built for a walk this run is not part of. The CALLER owns the sink lifecycle and the rebound window.
     */
    public void runForced(Ability[] abilities, int worldId, int triggerId, Player actor,
                          ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                          int[] candidates) {
        if (candidates.length == 0) {
            return;
        }
        long now = nowTicks.getAsLong();
        Activation activation = Activation.builder(actor.getUniqueId(), worldId, triggerId, now)
                // R-QC25c: the same random-backed roll every other entry point installs. This run walks no
                // gates, but the per-target defender consult still draws per window — with the builder's
                // constant 0.0 default a partial SUPPRESS_INCOMING blocked every hop instead of its authored share.
                .chanceRoll(() -> ThreadLocalRandom.current().nextDouble() * 100.0)
                .facts(factPopulator.populate(context, now, FactMask.ALL))
                .location(context.location())
                .targetBucket(context.victim() instanceof Player ? 1 : 0)
                // No victimId, deliberately: the per-target consult exempts it as "the body gate 5 already
                // adjudicated", and on a gateless run gate 5 never spoke. Naming one here would exempt the
                // reflected-upon attacker from every window — the one body the re-run is most aimed at.
                .build();
        executor.runForced(abilities, candidates, activation, context, sink, stableKeys);
    }

    private void runResolved(Ability[] abilities, int generation, int worldId, int triggerId, boolean attackSide,
                             Player actor, ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys,
                             WornState wornState, int[] candidates, boolean applyHeroic, ReboundPlan rebound) {
        if (applyHeroic) {
            if (attackSide) {
                // Attacker-side heroic weapon damage stays on the PLAIN adders — IGNORE_HEROIC negates only
                // the victim's heroic armor (ADR-0053).
                sink.addOutgoingDamage(wornState.heroic().percentDamage()); // §F additive fold (ADR-0037)
                sink.addFlatDamage(wornState.heroic().flatDamage());        // §F diamond base-attack delta (gold→diamond)
            } else {
                // Victim-side heroic reduction rides the heroic-tagged buckets (ADR-0053): folded identically
                // unless the hit set ignoreHeroic (§F additive fold, ADR-0037).
                sink.addHeroicReduction(wornState.heroic().percentReduction());
                sink.addHeroicFlatReduction(wornState.heroic().flatReduction()); // §F diamond armour delta
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
                // gate-7 condition facts, read on the firing thread; the mask computes ONLY the slots this
                // trigger's worn abilities reference (ADR-0039), skipping e.g. the %nearbyenemies% scan otherwise.
                .facts(factPopulator.populate(context, now, wornState.factMask(triggerId)))
                .location(context.location()) // captured on the firing thread → safe for the gate-2 guard
                // Cooldown buckets: the other combat party's kind (player vs mob) routes the cooldown, so an
                // ability proc'd on a mob and on a player run on two independent cooldowns (gates 6 + 11).
                .targetBucket(context.victim() instanceof Player ? 1 : 0)
                .victimId(context.victim() == null ? null : context.victim().getUniqueId())
                .rebound(rebound); // gate 9: null everywhere but the attack walk of a hit on a rebound wearer
        soulBinder.apply(actor).ifPresent(binding -> builder.soulMode(binding.marker()));
        executor.run(abilities, candidates, builder.build(), context, sink, stableKeys);
    }

    /** The interned world id for {@code world} (−1 if named in no blacklist; {@code Ability} never blocks on −1). */
    public static int worldId(Snapshot snapshot, World world) {
        return world == null ? -1 : snapshot.interners().worlds().idOf(world.getName());
    }
}
