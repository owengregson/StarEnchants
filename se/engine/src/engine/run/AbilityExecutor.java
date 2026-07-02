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
 * <p>Shared across snapshots; its only state is two volatile references rebound per reload — the
 * {@link AbilityQuarantine} and the effect registry (so add-on kinds registered after boot become runnable,
 * ADR-0038). Failures are isolated per effect and per ability so one bad unit never aborts the rest (§9
 * warn-and-skip), and an ability that keeps faulting is quarantined for the life of its snapshot (§10).
 */
public final class AbilityExecutor {

    private static final Logger LOG = System.getLogger("StarEnchants.Executor");

    // Rebound per reload so add-on effect kinds registered after boot become runnable (ADR-0038): a
    // volatile reference-swap, never torn, mirroring the quarantine binding below. An add-on registers via
    // StarEnchantsApi (after this plugin's onEnable), triggers a reload, and the composition root rebinds the
    // built-in + add-on registry here. Read once per activation, so the hot path pays only a volatile read.
    private volatile EffectRegistry effects;
    private final SelectorRegistry selectors;
    private final ActivationPipeline pipeline;
    private final AreaScan areaScan;
    private final ActivationListener listener;

    // Per-snapshot fault quarantine (§10), rebound by the composition root on each reload swap so a fixed edit
    // clears the block. The executor is shared across snapshots, so this is the one mutable field — a volatile
    // reference-swap, never torn. Inert until bound (unit tests keep the NONE default).
    private volatile AbilityQuarantine quarantine = AbilityQuarantine.NONE;

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

    /** Bind the quarantine for the live snapshot; call on boot and on every reload swap so it resets per snapshot (§10). */
    public void bindQuarantine(AbilityQuarantine quarantine) {
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine");
    }

    /** Rebind the effect registry (built-ins + registered add-on kinds); call on every reload swap so a newly registered add-on head becomes runnable (ADR-0038). */
    public void bindEffects(EffectRegistry effects) {
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /** The stable keys currently quarantined in the live snapshot — the read surface a command can query later (§10). */
    public List<String> quarantinedKeys() {
        return quarantine.quarantinedKeys();
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
        AbilityQuarantine quarantine = this.quarantine;
        int activated = 0;
        for (int id : candidateIds) {
            if (id < 0 || id >= abilities.length) {
                continue; // stale/foreign id (e.g. across a reload)
            }
            if (quarantine.isDisabled(id)) {
                continue; // §10: disabled for the life of this snapshot after repeated faults — skip before effects run
            }
            Ability ability = abilities[id];
            try {
                if (pipeline.evaluate(ability, activation).activated()) {
                    boolean faulted = runEffects(ability, context, sink, activation.activeGem(), activation.facts(), quarantine);
                    activated++;
                    notifyActivation(ability, context, stableKeys);
                    if (faulted) {
                        quarantine.recordFailure(id, ability.defId());
                    }
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "ability " + quarantine.describe(ability.defId()) + " failed during execution", failed);
                quarantine.recordFailure(id, ability.defId());
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
        EffectRegistry effects = this.effects; // read the volatile once per activation
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

    // Returns true if any effect KIND threw (a genuine fault the quarantine counts). An unregistered head is
    // warn-and-skip, NOT a fault — the ability still activates and its sibling effects run (§9).
    private boolean runEffects(Ability ability, ActivationContext context, SinkReadback sink, UUID activeGem,
                               engine.condition.FactBuffer facts, AbilityQuarantine quarantine) {
        EffectRegistry effects = this.effects; // read the volatile once per activation
        boolean faulted = false;
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
                faulted = true;
                LOG.log(Level.WARNING, "effect " + effect.head() + " of " + quarantine.describe(ability.defId())
                        + " failed during execution", failed);
            }
        }
        return faulted;
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
