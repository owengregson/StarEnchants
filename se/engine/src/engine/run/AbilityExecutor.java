package engine.run;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.StableKeyIndex;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import engine.sink.DispatchSink;
import engine.spec.TargetSpec;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;

/**
 * The runtime execution path — gate 12 (docs/architecture.md §3.3, §3.5). Given the candidate
 * abilities for one event, it runs each through the {@link ActivationPipeline} and, for every ability
 * that returns {@link engine.pipeline.GateOutcome#ACTIVATED ACTIVATED}, executes the ability's
 * effects: resolve each effect's target selector, build the read-only {@link EffectCtx}, set the
 * {@link DispatchSink}'s affinity to that effect's declared value, and run the {@code EffectKind} —
 * which emits intents into the sink and never touches the world. The caller flushes the sink once
 * after the gate walk so every activated ability's intents dispatch batched per owning thread (§3.6).
 *
 * <p>Pure orchestration over already-verified pieces: the gate pipeline, the pure selector/effect
 * kinds, and the matrix-verified dispatcher. Stateless and shareable; the per-event {@code Snapshot}
 * abilities, {@link Activation}, {@link ActivationContext}, and {@link DispatchSink} are passed in.
 * A failing selector or effect (e.g. an absent kind, or an area scan off the firing region) is
 * isolated per effect — and a failing gate walk per ability — so one bad unit never aborts the rest
 * (§9 warn-and-skip); the sink's own deferred flush isolates the rest.
 */
public final class AbilityExecutor {

    private static final Logger LOG = System.getLogger("StarEnchants.Executor");

    private final EffectRegistry effects;
    private final SelectorRegistry selectors;
    private final ActivationPipeline pipeline;
    private final AreaScan areaScan;
    private final ActivationListener listener;

    /** An executor with no activation observer (the listener is {@link ActivationListener#NONE}). */
    public AbilityExecutor(EffectRegistry effects, SelectorRegistry selectors,
                           ActivationPipeline pipeline, AreaScan areaScan) {
        this(effects, selectors, pipeline, areaScan, ActivationListener.NONE);
    }

    /** An executor that notifies {@code listener} once per ability that activates (e.g. to fire the public event). */
    public AbilityExecutor(EffectRegistry effects, SelectorRegistry selectors,
                           ActivationPipeline pipeline, AreaScan areaScan, ActivationListener listener) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.selectors = Objects.requireNonNull(selectors, "selectors");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.areaScan = Objects.requireNonNull(areaScan, "areaScan");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Evaluate each candidate ability against {@code activation} and run the effects of every
     * ACTIVATED one into {@code sink}. {@code candidateIds} are dense ids indexing {@code abilities}
     * (typically {@code WornState.byTrigger(triggerId)}). {@code stableKeys} is THIS snapshot's
     * key index — the one whose {@code abilities[]} is being walked — used to resolve each activated
     * ability's stable key for the {@link ActivationListener}; it must pair with {@code abilities}, so
     * the key always names the ability that fired even if a reload concurrently swaps the live snapshot.
     * May be {@code null} when no listener is wired (resolution is skipped). Does NOT flush — the caller
     * flushes the sink once after this returns (and after any other passes into the same sink, e.g. the
     * attacker- and defender-side abilities of one hit).
     *
     * @return the number of abilities that activated
     */
    public int run(Ability[] abilities, int[] candidateIds, Activation activation,
                   ActivationContext context, DispatchSink sink, StableKeyIndex stableKeys) {
        int activated = 0;
        for (int id : candidateIds) {
            if (id < 0 || id >= abilities.length) {
                continue; // a stale/foreign id (e.g. across a reload) — skip defensively
            }
            Ability ability = abilities[id];
            try {
                if (pipeline.evaluate(ability, activation).activated()) {
                    runEffects(ability, context, sink, activation.activeGem());
                    activated++;
                    notifyActivation(ability, context, stableKeys);
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "ability " + id + " failed during execution", failed);
            }
        }
        return activated;
    }

    /**
     * Notify the activation listener, isolating any failure so a misbehaving observer never aborts the
     * hit. The key is resolved against {@code stableKeys} (the run's own snapshot index) so it pairs
     * with the dense id {@code abilities[]} produced — never a live holder a reload could swap — and is
     * reduced to the ability's BASE content key (e.g. {@code enchants/venom}) for the public seam: the
     * compiled per-level key {@code enchants/venom/1} is an internal accelerator, while the level is
     * carried separately on {@link Ability#level()}. Level-less sources (crystals/sets) are base-keyed
     * already, so they pass through unchanged.
     */
    private void notifyActivation(Ability ability, ActivationContext context, StableKeyIndex stableKeys) {
        if (listener == ActivationListener.NONE) {
            return; // no observer wired — skip the key resolution entirely (hot-path no-op)
        }
        try {
            String full = stableKeys == null ? null : stableKeys.keyOf(ability.id());
            listener.onActivate(baseKey(full, ability.level()), ability, context);
        } catch (Throwable failed) {
            LOG.log(Level.WARNING, "activation listener failed for ability " + ability.id(), failed);
        }
    }

    /**
     * The base content key for the public seam: an enchant's compiled stable key is {@code <base>/<level>}
     * (e.g. {@code enchants/venom/1}), so strip the trailing {@code /<level>} to recover the identity an
     * item/config names ({@code enchants/venom}). Sources with no level ({@code level <= 0} — crystals,
     * sets) are already base-keyed and returned as-is.
     */
    private static String baseKey(String stableKey, int level) {
        if (stableKey == null || level <= 0) {
            return stableKey;
        }
        String suffix = "/" + level;
        return stableKey.endsWith(suffix) ? stableKey.substring(0, stableKey.length() - suffix.length()) : stableKey;
    }

    /**
     * Run ONE HELD/PASSIVE ability's effects as a lifecycle transition (§B, ADR-0022) — NOT through the
     * gate pipeline. A maintained buff is deterministic, so the chance/cooldown/condition/soul gates do not
     * apply; the {@code LifecycleDriver} has already decided this source just became active ({@code stopping
     * == false} → {@link EffectKind#run}) or inactive ({@code stopping == true} → {@link EffectKind#stop},
     * the teardown). World-blacklist is the caller's concern (it knows the activator's world); STOP is always
     * unconditional so a buff can never leak. No {@code WAIT} deferral — a teardown must land with the
     * unequip, not ticks later — and no {@link ActivationListener} notification (lifecycle is not a gated
     * activation). Failures are isolated per effect, like {@link #runEffects}.
     */
    public void runLifecycle(Ability ability, ActivationContext context, DispatchSink sink, boolean stopping) {
        for (CompiledEffect effect : ability.effects()) {
            try {
                EffectKind kind = effects.lookup(effect.head()).orElse(null);
                if (kind == null) {
                    LOG.log(Level.WARNING, "no effect kind registered for head " + effect.head());
                    continue;
                }
                List<LivingEntity> targets = resolveTargets(effect, context);
                EffectCtx ctx = new RuntimeEffectCtx(
                        effect.args(), context, slotMap(kind, targets), ability.level(), null);
                sink.delay(0); // lifecycle transitions are immediate — a buff turns on/off with the equip change
                if (stopping) {
                    kind.stop(ctx, sink);
                } else {
                    kind.run(ctx, sink);
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "lifecycle effect " + effect.head() + " failed during execution", failed);
            }
        }
    }

    private void runEffects(Ability ability, ActivationContext context, DispatchSink sink, UUID activeGem) {
        for (CompiledEffect effect : ability.effects()) {
            try {
                EffectKind kind = effects.lookup(effect.head()).orElse(null);
                if (kind == null) {
                    LOG.log(Level.WARNING, "no effect kind registered for head " + effect.head());
                    continue;
                }
                List<LivingEntity> targets = resolveTargets(effect, context);
                EffectCtx ctx = new RuntimeEffectCtx(
                        effect.args(), context, slotMap(kind, targets), ability.level(), activeGem);
                // WAIT (§3.6): route this effect's world-mutation intents into its accumulated delay tier so
                // they dispatch that many ticks after the hit. Targets are still resolved now, on the firing
                // thread; the sink defers only the mutation (and keeps inline feedback — fold/cancel — instant).
                sink.delay(effect.cumulativeWaitTicks());
                kind.run(ctx, sink);
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "effect " + effect.head() + " failed during execution", failed);
            }
        }
    }

    private List<LivingEntity> resolveTargets(CompiledEffect effect, ActivationContext context) {
        SelectorKind selector = selectors.lookup(effect.target().head()).orElse(null);
        if (selector == null) {
            LOG.log(Level.WARNING, "no selector kind registered for head " + effect.target().head());
            return List.of();
        }
        return selector.resolve(new RuntimeSelectorCtx(context, effect.target().args(), areaScan));
    }

    /**
     * Bind the resolved targets to the effect's primary target slot (the slot {@code CompiledEffect}'s
     * single selector fills). Effects with no declared slot never read {@code targets()}, so an empty
     * map is correct for them.
     */
    private static Map<String, List<LivingEntity>> slotMap(EffectKind kind, List<LivingEntity> targets) {
        List<TargetSpec> slots = kind.spec().targets();
        return slots.isEmpty() ? Map.of() : Map.of(slots.get(0).name(), targets);
    }
}
