package engine.run;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.StableKeyIndex;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.pipeline.GateOutcome;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import engine.sink.SinkReadback;
import engine.spec.TargetSpec;
import engine.stores.SuppressionStore;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The runtime execution path — gate 12 (docs/architecture.md §3.3): runs each candidate ability through
 * the {@link ActivationPipeline} and emits every ACTIVATED one's effect intents into the sink
 * ({@link SinkReadback}) without touching the world. The caller flushes once after the gate walk.
 *
 * <p>Shared across snapshots; its only state is two volatile references rebound per reload — the
 * {@link AbilityQuarantine} and the {@link LinkedContent} pair (effect + selector kinds) it dispatches
 * against (ADR-0039: dispatch by dense {@code kindId} array index, so add-on kinds registered after boot
 * become runnable, ADR-0038). Failures are isolated per effect and per ability so one bad unit never aborts
 * the rest (§9 warn-and-skip), and an ability that keeps faulting is quarantined for the life of its snapshot (§10).
 */
public final class AbilityExecutor {

    private static final Logger LOG = System.getLogger("StarEnchants.Executor");

    // The effect + selector kind arrays, bound as ONE reference (ADR-0039) so a reload never exposes a torn
    // mix; rebound per reload so add-on effect kinds registered after boot become runnable (ADR-0038). An
    // add-on registers via StarEnchantsApi (after onEnable), triggers a reload, and the composition root
    // rebinds here. Read once per activation, so the hot path pays only a volatile read then an array index.
    private volatile LinkedContent linked;
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
        this.linked = LinkedContent.of(effects, selectors);
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.areaScan = Objects.requireNonNull(areaScan, "areaScan");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** Bind the quarantine for the live snapshot; call on boot and on every reload swap so it resets per snapshot (§10). */
    public void bindQuarantine(AbilityQuarantine quarantine) {
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine");
    }

    /**
     * Rebind the effect + selector kinds as one atomic {@link LinkedContent} (built-ins + registered add-on
     * kinds); call on every reload swap so a newly registered add-on head becomes runnable (ADR-0038/0039).
     * Selectors are the built-in set (add-ons contribute effects only), so they carry over unchanged.
     */
    public void bindContent(EffectRegistry effects) {
        this.linked = LinkedContent.of(effects, this.linked.selectors());
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
                GateOutcome outcome = pipeline.evaluate(ability, activation);
                if (outcome.activated()) {
                    boolean faulted = runEffects(ability, context, sink, activation.activeGem(), activation.facts(), quarantine);
                    activated++;
                    notifyActivation(ability, context, stableKeys);
                    emitSoulRefund(ability, activation, context, sink);
                    if (faulted) {
                        quarantine.recordFailure(id, ability.defId());
                    }
                } else {
                    emitVerdictFeedback(outcome, ability, activation, context, sink);
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "ability " + quarantine.describe(ability.defId()) + " failed during execution", failed);
                quarantine.recordFailure(id, ability.defId());
            }
        }
        return activated;
    }

    /**
     * Emit the authored feedback for a BLOCKED verdict. This is the DISPATCH layer's job, not the pipeline's:
     * the pipeline is deliberately Bukkit-free and holds only a UUID, and the verdict it returns here is the
     * same one it recorded for {@code /se why}, so both read one decision rather than two. Costs two enum
     * compares on an ordinary blocked verdict (wrong trigger, cooldown, chance); a silent suppression window
     * reads back the {@code null} it stored, so the no-feedback case allocates nothing.
     */
    private void emitVerdictFeedback(GateOutcome outcome, Ability ability, Activation activation,
                                     ActivationContext context, SinkReadback sink) {
        Player actor = context.actor();
        if (actor == null) {
            return;
        }
        if (outcome == GateOutcome.SUPPRESSED) {
            SuppressionStore.Feedback feedback = pipeline.suppressionFeedback(ability, activation);
            if (feedback == null) {
                return;
            }
            if (!feedback.actorMessage().isEmpty()) {
                sink.messageTo(feedback.by(), feedback.actorMessage()); // whoever armed it; offline is a no-op
            }
            if (!feedback.victimMessage().isEmpty()) {
                sink.message(actor, feedback.victimMessage()); // the SUPPRESS's victim IS the blocked activator
            }
            if (feedback.soundId() >= 0) {
                sink.sound(actor.getLocation(), feedback.soundId(), 1.0f, 1.0f);
            }
        } else if (outcome == GateOutcome.NO_SOULS) {
            String notice = ability.noSoulsMessage();
            int soundId = ability.noSoulsSound();
            int particleId = ability.noSoulsParticle();
            if ((notice != null && !notice.isEmpty()) || soundId >= 0 || particleId >= 0) {
                // Throttled in the sink: many abilities, one empty pool — and one throttle for line + cue.
                sink.outOfSoulsNotice(actor, notice, soundId, particleId);
            }
        }
    }

    /**
     * Emit the refund line for a soul cost gate 10 WAIVED (SOUL_COST_EXEMPT) — the DISPATCH layer's job for the
     * same reason {@link #emitVerdictFeedback} is: the pipeline is Bukkit-free and holds only a UUID. The
     * threshold test and the wording live on the exemption window, so an ordinary activation pays one
     * {@code soulCost() <= 0} compare and nothing else.
     */
    private void emitSoulRefund(Ability ability, Activation activation, ActivationContext context,
                                SinkReadback sink) {
        int waived = pipeline.soulCostWaived(ability, activation);
        if (waived > 0 && context.actor() != null) {
            sink.soulRefundNotice(context.actor(), waived);
        }
    }

    /**
     * The COLD use-item path (§3.6, docs/decisions/0048-use-items.md): evaluate the explicit {@code candidateIds}
     * (a held use-item's abilities, resolved from its def's stable keys — NOT a worn {@code byTrigger} set) and
     * run every ACTIVATED one's effects into {@code sink}, exactly like {@link #run} but returning a compact
     * {@link UseAttempt} the feature layer renders feedback from instead of the void hot-path fold. Separate
     * from {@link #run} so the combat hot path's signature/allocation profile is untouched. Does NOT flush.
     */
    public UseAttempt runUse(Ability[] abilities, int[] candidateIds, Activation activation,
                             ActivationContext context, SinkReadback sink, StableKeyIndex stableKeys) {
        AbilityQuarantine quarantine = this.quarantine;
        boolean activated = false;
        boolean onCooldown = false;
        long cooldownRemaining = 0;
        int conditionIndex = -1;
        boolean chanceFailed = false;
        for (int i = 0; i < candidateIds.length; i++) {
            int id = candidateIds[i];
            if (id < 0 || id >= abilities.length) {
                continue; // stale/unresolved stable key (e.g. across a reload) — the aligned index is skipped
            }
            if (quarantine.isDisabled(id)) {
                continue; // §10: disabled for the life of this snapshot after repeated faults
            }
            Ability ability = abilities[id];
            try {
                // spendCooldownOnChanceFail=true: a use-item charges per ATTEMPT, so a failed roll arms the cooldown
                // and right-click spam can't retry a sub-100% use-item for free (the hot #run path must NOT do this).
                GateOutcome outcome = pipeline.evaluate(ability, activation, true);
                switch (outcome) {
                    case ACTIVATED -> {
                        boolean faulted = runEffects(ability, context, sink, activation.activeGem(),
                                activation.facts(), quarantine);
                        activated = true;
                        notifyActivation(ability, context, stableKeys);
                        emitSoulRefund(ability, activation, context, sink);
                        if (faulted) {
                            quarantine.recordFailure(id, ability.defId());
                        }
                    }
                    case ON_COOLDOWN -> {
                        onCooldown = true;
                        cooldownRemaining = Math.max(cooldownRemaining,
                                pipeline.remainingCooldownTicks(ability, activation));
                    }
                    case CONDITION_FAILED -> {
                        if (conditionIndex < 0) {
                            conditionIndex = i; // first condition stop; index aligns with the def's ability order
                        }
                    }
                    case CHANCE_FAILED -> chanceFailed = true;
                    default -> { } // world/protection/trigger/level/suppression/souls/cancel → the BLOCKED bucket
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "use-item ability " + quarantine.describe(ability.defId())
                        + " failed during execution", failed);
                quarantine.recordFailure(id, ability.defId());
            }
        }
        return new UseAttempt(activated, onCooldown, cooldownRemaining, conditionIndex, chanceFailed);
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
    public static String baseKey(String stableKey, int level) {
        if (stableKey == null || level <= 0) {
            return stableKey;
        }
        String suffix = "/" + level;
        return stableKey.endsWith(suffix) ? stableKey.substring(0, stableKey.length() - suffix.length()) : stableKey;
    }

    /**
     * The GATELESS re-execution entry point (PROC_REBOUND): run {@code candidateIds}' effects against
     * {@code context} without walking the pipeline. Every one of these abilities ALREADY passed its own
     * gates on the attacker's walk and was vetoed at gate 9 only so the reflector could take it — re-gating
     * would re-roll its chance, evaluate its condition against the REFLECTOR's facts, reserve a cooldown
     * under a key they do not own, and debit their souls.
     *
     * <p>Unlike {@link #runLifecycle} this is a real activation: it carries the caller's populated
     * {@link engine.condition.FactBuffer} and honours each effect's {@code cumulativeWaitTicks}. It does NOT
     * notify the {@link ActivationListener} — the public event and the {@code /se why} record already name
     * the attacker's activation, and announcing it again as the reflector's would credit them an enchant
     * they do not carry. Does NOT flush; the caller flushes once.
     */
    public void runForced(Ability[] abilities, int[] candidateIds, Activation activation,
                          ActivationContext context, SinkReadback sink) {
        AbilityQuarantine quarantine = this.quarantine;
        for (int id : candidateIds) {
            if (id < 0 || id >= abilities.length || quarantine.isDisabled(id)) {
                continue; // stale/foreign id, or disabled for the life of this snapshot (§10)
            }
            Ability ability = abilities[id];
            try {
                // No active gem: this run spends nothing, so a soul-reading effect must not see one.
                if (runEffects(ability, context, sink, null, activation.facts(), quarantine)) {
                    quarantine.recordFailure(id, ability.defId());
                }
            } catch (Throwable failed) {
                LOG.log(Level.WARNING, "forced ability " + quarantine.describe(ability.defId())
                        + " failed during execution", failed);
                quarantine.recordFailure(id, ability.defId());
            }
        }
    }

    /**
     * Run ONE HELD/PASSIVE ability's effects as a lifecycle transition (ADR-0022), NOT through the gate
     * pipeline: a maintained buff is deterministic, so chance/cooldown/condition/soul gates do not apply.
     * {@code stopping} selects {@link EffectKind#stop} (teardown) over {@link EffectKind#run}; STOP is
     * unconditional so a buff can never leak. No {@code WAIT} deferral — teardown must land with the unequip.
     */
    public void runLifecycle(Ability ability, ActivationContext context, SinkReadback sink, boolean stopping) {
        LinkedContent linked = this.linked; // read the volatile once per activation (atomic effect+selector pair)
        ActorOrigin origin = null;
        for (CompiledEffect effect : ability.effects()) {
            try {
                EffectKind kind = linked.effectFor(effect); // ADR-0039: dense-id dispatch, head-fallback only for -1
                if (kind == null) {
                    LOG.log(Level.WARNING, "no effect kind registered for head " + effect.head());
                    continue;
                }
                if ("HEALTH".equals(effect.head()) && "SELF".equals(effect.target().head())) {
                    // Worn max-health is RECONCILED by the MaxHealthDriver's keyed modifier (the potion-driver
                    // ownership split): a lifecycle run/stop here would ADD into a second channel and compound
                    // on every relog. Non-SELF / event-trigger HEALTH keeps its direct permanent shift.
                    continue;
                }
                if (kind.spec().needsActorOrigin() && origin == null) {
                    // ADR-0043: one firing-thread snapshot per activated ability, before any region hop.
                    origin = ActorOrigin.capture(context.actor());
                }
                SelectorKind selector = linked.selectorFor(effect.target());
                RuntimeSelectorCtx sel = selector == null ? null
                        : new RuntimeSelectorCtx(context, effect.target().args(), areaScan);
                List<LivingEntity> targets = selector == null ? List.of() : selector.resolve(sel);
                List<org.bukkit.Location> locations = selector == null ? List.of() : selector.resolveLocations(sel);
                EffectCtx ctx = new RuntimeEffectCtx(effect.args(), context, slotMap(kind, targets),
                        locationSlotMap(kind, locations), ability.level(), ability.defId(), null, null, origin);
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
        LinkedContent linked = this.linked; // read the volatile once per activation (atomic effect+selector pair)
        boolean faulted = false;
        ActorOrigin origin = null;
        for (CompiledEffect effect : ability.effects()) {
            try {
                EffectKind kind = linked.effectFor(effect); // ADR-0039: dense-id dispatch, head-fallback only for -1
                if (kind == null) {
                    LOG.log(Level.WARNING, "no effect kind registered for head " + effect.head());
                    continue;
                }
                if (kind.spec().needsActorOrigin() && origin == null) {
                    // ADR-0043: one firing-thread snapshot per activated ability, before any region hop.
                    origin = ActorOrigin.capture(context.actor());
                }
                SelectorKind selector = linked.selectorFor(effect.target());
                RuntimeSelectorCtx sel = selector == null ? null
                        : new RuntimeSelectorCtx(context, effect.target().args(), areaScan);
                List<LivingEntity> targets = selector == null ? List.of() : selector.resolve(sel);
                List<org.bukkit.Location> locations = selector == null ? List.of() : selector.resolveLocations(sel);
                if (selector == null) {
                    LOG.log(Level.WARNING, "no selector kind registered for head " + effect.target().head());
                }
                EffectCtx ctx = new RuntimeEffectCtx(effect.args(), context, slotMap(kind, targets),
                        locationSlotMap(kind, locations), ability.level(), ability.defId(), activeGem, facts, origin);
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
