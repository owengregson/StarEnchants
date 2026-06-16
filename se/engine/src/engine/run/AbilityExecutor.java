package engine.run;

import compile.model.Ability;
import compile.model.CompiledEffect;
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

    public AbilityExecutor(EffectRegistry effects, SelectorRegistry selectors,
                           ActivationPipeline pipeline, AreaScan areaScan) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.selectors = Objects.requireNonNull(selectors, "selectors");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.areaScan = Objects.requireNonNull(areaScan, "areaScan");
    }

    /**
     * Evaluate each candidate ability against {@code activation} and run the effects of every
     * ACTIVATED one into {@code sink}. {@code candidateIds} are dense ids indexing {@code abilities}
     * (typically {@code WornState.byTrigger(triggerId)}). Does NOT flush — the caller flushes the
     * sink once after this returns (and after any other passes into the same sink, e.g. the
     * attacker- and defender-side abilities of one hit).
     *
     * @return the number of abilities that activated
     */
    public int run(Ability[] abilities, int[] candidateIds, Activation activation,
                   ActivationContext context, DispatchSink sink) {
        int activated = 0;
        for (int id : candidateIds) {
            if (id < 0 || id >= abilities.length) {
                continue; // a stale/foreign id (e.g. across a reload) — skip defensively
            }
            Ability ability = abilities[id];
            try {
                if (pipeline.evaluate(ability, activation).activated()) {
                    runEffects(ability, context, sink);
                    activated++;
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "ability " + id + " failed during execution", failed);
            }
        }
        return activated;
    }

    private void runEffects(Ability ability, ActivationContext context, DispatchSink sink) {
        for (CompiledEffect effect : ability.effects()) {
            // NOTE: effect.cumulativeWaitTicks() (WAIT, §3.6) is not yet honored — every effect runs
            // in this pass. Honoring it needs the dispatcher to support delayed batches (a deferred
            // flush on the region/entity timer), which the DispatchSink does not model yet; wiring
            // WAIT is a follow-up that lands with that sink capability.
            try {
                EffectKind kind = effects.lookup(effect.head()).orElse(null);
                if (kind == null) {
                    LOG.log(Level.WARNING, "no effect kind registered for head " + effect.head());
                    continue;
                }
                List<LivingEntity> targets = resolveTargets(effect, context);
                EffectCtx ctx = new RuntimeEffectCtx(effect.args(), context, slotMap(kind, targets), ability.level());
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
