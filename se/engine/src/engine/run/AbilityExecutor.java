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
import engine.sink.SinkReadback;
import engine.spec.TargetSpec;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;

/**
 * The runtime execution path — gate 12 (docs/architecture.md §3.3): runs each candidate ability through
 * the {@link ActivationPipeline} and emits every ACTIVATED one's effect intents into the sink
 * ({@link SinkReadback}) without touching the world. The caller flushes once after the gate walk.
 *
 * <p>Stateless and shareable. Failures are isolated per effect and per ability so one bad unit never
 * aborts the rest (§9 warn-and-skip).
 */
public final class AbilityExecutor {

    private static final Logger LOG = System.getLogger("StarEnchants.Executor");

    private final EffectRegistry effects;
    private final SelectorRegistry selectors;
    private final ActivationPipeline pipeline;
    private final AreaScan areaScan;
    private final ActivationListener listener;

    public AbilityExecutor(EffectRegistry effects, SelectorRegistry selectors,
                           ActivationPipeline pipeline, AreaScan areaScan) {
        this(effects, selectors, pipeline, areaScan, ActivationListener.NONE);
    }

    /** {@code listener} is notified once per ability that activates (e.g. to fire the public event). */
    public AbilityExecutor(EffectRegistry effects, SelectorRegistry selectors,
                           ActivationPipeline pipeline, AreaScan areaScan, ActivationListener listener) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.selectors = Objects.requireNonNull(selectors, "selectors");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.areaScan = Objects.requireNonNull(areaScan, "areaScan");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Evaluate each candidate ability and run every ACTIVATED one's effects into {@code sink}.
     * {@code stableKeys} MUST pair with {@code abilities} (this snapshot's index) so a listener key names
     * the right ability even if a reload concurrently swaps the live snapshot; {@code null} when no
     * listener is wired. Does NOT flush — the caller flushes once after sibling passes into the same sink.
     *
     * @return the number of abilities that activated
     */
    public int run(Ability[] abilities, int[] candidateIds, Activation activation,
                   ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys) {
        int activated = 0;
        for (int id : candidateIds) {
            if (id < 0 || id >= abilities.length) {
                continue; // stale/foreign id (e.g. across a reload)
            }
            Ability ability = abilities[id];
            try {
                if (pipeline.evaluate(ability, activation).activated()) {
                    runEffects(ability, context, sink, activation.activeGem(), activation.facts());
                    activated++;
                    notifyActivation(ability, context, stableKeys);
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "ability " + id + " failed during execution", failed);
            }
        }
        return activated;
    }

    // Failure isolated so a bad observer never aborts the hit. Key resolved against the run's own snapshot
    // (never a live holder a reload could swap) and reduced to the BASE content key — level is on Ability.
    private void notifyActivation(Ability ability, ActivationContext context, StableKeyIndex stableKeys) {
        if (listener == ActivationListener.NONE) {
            return; // hot-path no-op: skip key resolution when no observer is wired
        }
        try {
            String full = stableKeys == null ? null : stableKeys.keyOf(ability.id());
            listener.onActivate(baseKey(full, ability.level()), ability, context);
        } catch (Throwable failed) {
            LOG.log(Level.WARNING, "activation listener failed for ability " + ability.id(), failed);
        }
    }

    /** {@code enchants/venom/1} → {@code enchants/venom}; level-less sources (crystals, sets) pass through. */
    private static String baseKey(String stableKey, int level) {
        if (stableKey == null || level <= 0) {
            return stableKey;
        }
        String suffix = "/" + level;
        return stableKey.endsWith(suffix) ? stableKey.substring(0, stableKey.length() - suffix.length()) : stableKey;
    }

    /**
     * Run ONE HELD/PASSIVE ability's effects as a lifecycle transition (ADR-0022), NOT through the gate
     * pipeline: a maintained buff is deterministic, so chance/cooldown/condition/soul gates do not apply.
     * {@code stopping} selects {@link EffectKind#stop} (teardown) over {@link EffectKind#run}; STOP is
     * unconditional so a buff can never leak. No {@code WAIT} deferral — teardown must land with the unequip.
     */
    public void runLifecycle(Ability ability, ActivationContext context, SinkReadback sink, boolean stopping) {
        for (CompiledEffect effect : ability.effects()) {
            try {
                EffectKind kind = effects.lookup(effect.head()).orElse(null);
                if (kind == null) {
                    LOG.log(Level.WARNING, "no effect kind registered for head " + effect.head());
                    continue;
                }
                SelectorKind selector = selectors.lookup(effect.target().head()).orElse(null);
                RuntimeSelectorCtx sel = selector == null ? null
                        : new RuntimeSelectorCtx(context, effect.target().args(), areaScan);
                List<LivingEntity> targets = selector == null ? List.of() : selector.resolve(sel);
                List<org.bukkit.Location> locations = selector == null ? List.of() : selector.resolveLocations(sel);
                EffectCtx ctx = new RuntimeEffectCtx(effect.args(), context, slotMap(kind, targets),
                        locationSlotMap(kind, locations), ability.level(), null, null);
                sink.delay(0);
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

    private void runEffects(Ability ability, ActivationContext context, SinkReadback sink, UUID activeGem,
                            engine.condition.FactBuffer facts) {
        for (CompiledEffect effect : ability.effects()) {
            try {
                EffectKind kind = effects.lookup(effect.head()).orElse(null);
                if (kind == null) {
                    LOG.log(Level.WARNING, "no effect kind registered for head " + effect.head());
                    continue;
                }
                SelectorKind selector = selectors.lookup(effect.target().head()).orElse(null);
                RuntimeSelectorCtx sel = selector == null ? null
                        : new RuntimeSelectorCtx(context, effect.target().args(), areaScan);
                List<LivingEntity> targets = selector == null ? List.of() : selector.resolve(sel);
                List<org.bukkit.Location> locations = selector == null ? List.of() : selector.resolveLocations(sel);
                if (selector == null) {
                    LOG.log(Level.WARNING, "no selector kind registered for head " + effect.target().head());
                }
                EffectCtx ctx = new RuntimeEffectCtx(effect.args(), context, slotMap(kind, targets),
                        locationSlotMap(kind, locations), ability.level(), activeGem, facts);
                // WAIT (§3.6): defer only this effect's world-mutation intents by its accumulated tick tier.
                // Targets are resolved now on the firing thread; inline feedback (fold/cancel) stays instant.
                sink.delay(effect.cumulativeWaitTicks());
                kind.run(ctx, sink);
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "effect " + effect.head() + " failed during execution", failed);
            }
        }
    }

    /** Bind resolved entity targets to the effect's primary slot; empty map for effects that declare none. */
    private static Map<String, List<LivingEntity>> slotMap(EffectKind kind, List<LivingEntity> targets) {
        List<TargetSpec> slots = kind.spec().targets();
        return slots.isEmpty() ? Map.of() : Map.of(slots.get(0).name(), targets);
    }

    /** Bind the resolved LOCATION targets to the effect's primary slot (block/coordinate selectors, §A). */
    private static Map<String, List<org.bukkit.Location>> locationSlotMap(
            EffectKind kind, List<org.bukkit.Location> locations) {
        List<TargetSpec> slots = kind.spec().targets();
        return slots.isEmpty() ? Map.of() : Map.of(slots.get(0).name(), locations);
    }
}
