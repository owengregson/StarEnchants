package engine.pipeline;

import compile.model.Ability;
import compile.model.ScopeKinds;
import compile.model.cond.NumExpr;
import engine.condition.ConditionEvaluator;
import engine.condition.ConditionResult;
import engine.condition.Flow;
import engine.condition.NumExprEval;
import engine.interact.SoulSpender;
import engine.stores.CooldownStore;
import engine.stores.SoulEscalationStore;
import engine.stores.SuppressionStore;
import engine.stores.WhyRecorder;
import engine.stores.WhyRing;
import java.util.Objects;
import java.util.UUID;

/**
 * The activation pipeline: the fixed Cosmic Enchants-style gate sequence (docs/architecture.md §3.3),
 * identical for every source so no per-source path can drift. Each gate is a compiled
 * integer/bitset/primitive check, not a string op. A System walks the candidate abilities for a trigger
 * and runs each through {@link #evaluate}; {@link GateOutcome#ACTIVATED} means the caller runs the
 * ability's effects (gate 12).
 *
 * <p>Gates 1, 3–8, 10 are pure logic over the {@link Ability} and {@link Activation}; gate 2
 * (protection) and gate 9 ({@code PreActivate}) are injected {@link Guard}s (default allow) so the
 * cross-version/Bukkit edges stay outside this pure core. Gate 6 atomically RESERVES the cooldown (a fused
 * check-and-arm, so two same-tick Folia hits on one key can't both pass); any later gate failure
 * (condition/chance/{@code PreActivate}/souls) RELEASES it, and ACTIVATED commits it implicitly — the
 * pass-then-arm window is zero-width. The souls debit (gate 10) runs AFTER {@code PreActivate}. Exception: the
 * cold use-item path ({@code spendCooldownOnChanceFail}) keeps the reservation on a chance fail, so a spammed
 * sub-100% use-item is charged the cooldown per attempt.
 *
 * <p>Every {@code evaluate} reports {@code (defId, trigger, verdict, per-gate payload)} to the injected
 * {@link WhyRecorder} (ADR-0045); payloads are captured at the failing gate and names resolve at render time.
 */
public final class ActivationPipeline {

    /** A pluggable gate (protection at gate 2, {@code PreActivate} at gate 9). */
    @FunctionalInterface
    public interface Guard {
        boolean allows(Ability ability, Activation activation);

        /** Always allows — the default for both injected gates. */
        Guard ALLOW = (ability, activation) -> true;
    }


    /** Escalation-scope namespaces for the two fallbacks, kept clear of the {@link ScopeKinds} range. */
    private static final int ESCALATION_BY_SUPPRESS_KEY = 8;
    private static final int ESCALATION_BY_DEF_ID = 9;

    private final CooldownStore cooldowns;
    private final SoulSpender spender;
    private final SuppressionStore suppression;
    private final Guard protection;
    private final Guard preActivate;
    private final WhyRecorder recorder;
    private final SoulEscalationStore escalation;

    public ActivationPipeline(CooldownStore cooldowns, SoulSpender spender) {
        this(cooldowns, spender, new SuppressionStore(), Guard.ALLOW, Guard.ALLOW, WhyRecorder.NONE);
    }

    public ActivationPipeline(CooldownStore cooldowns, SoulSpender spender,
                              Guard protection, Guard preActivate) {
        this(cooldowns, spender, new SuppressionStore(), protection, preActivate, WhyRecorder.NONE);
    }

    public ActivationPipeline(CooldownStore cooldowns, SoulSpender spender, SuppressionStore suppression,
                              Guard protection, Guard preActivate) {
        this(cooldowns, spender, suppression, protection, preActivate, WhyRecorder.NONE);
    }

    public ActivationPipeline(CooldownStore cooldowns, SoulSpender spender, SuppressionStore suppression,
                              Guard protection, Guard preActivate, WhyRecorder recorder) {
        this(cooldowns, spender, suppression, protection, preActivate, recorder, new SoulEscalationStore());
    }

    /** The production seam: {@code escalation} must be the aggregate's store, or gate 10's counters never
     *  see the quit sweep. */
    public ActivationPipeline(CooldownStore cooldowns, SoulSpender spender, SuppressionStore suppression,
                              Guard protection, Guard preActivate, WhyRecorder recorder,
                              SoulEscalationStore escalation) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.spender = Objects.requireNonNull(spender, "spender");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.preActivate = Objects.requireNonNull(preActivate, "preActivate");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.escalation = Objects.requireNonNull(escalation, "escalation");
    }

    /** Run {@code ability} through every gate against {@code act}, returning where it stopped. Every path
     *  reports its verdict + per-gate payload to the {@link WhyRecorder} on the way out (ADR-0045). The combat
     *  hot path uses this 2-arg form: a chance/condition fail RELEASES the gate-6 reservation, so a sub-100%
     *  proc enchant with a cooldown still rolls every hit. */
    public GateOutcome evaluate(Ability ability, Activation act) {
        return evaluate(ability, act, false);
    }

    /**
     * As {@link #evaluate(Ability, Activation)}, but {@code spendCooldownOnChanceFail} charges a failed chance
     * roll (gate 8) the cooldown: the gate-6 reservation STANDS instead of being released, so the failed attempt
     * arms the cooldown. Only the COLD use-item path passes {@code true} — a use-item charges per ATTEMPT, so
     * right-click spam can't retry a sub-100% roll for free. The hot path must keep {@code false} or a proc
     * enchant rolling chance under a cooldown would almost never fire.
     */
    public GateOutcome evaluate(Ability ability, Activation act, boolean spendCooldownOnChanceFail) {
        // 1. world blacklist — primitive AND
        if (ability.blockedInWorld(act.worldId())) {
            return record(GateOutcome.BLOCKED_WORLD, ability, act, act.worldId(), 0);
        }
        // 2. protection / region — injected, cached per tick in production
        if (!protection.allows(ability, act)) {
            return record(GateOutcome.BLOCKED_PROTECTION, ability, act, 0, 0);
        }
        // 3. trigger-match (slot applicability is pre-filtered into WornState.byTrigger)
        if (!ability.firesOn(act.triggerId())) {
            return record(GateOutcome.WRONG_TRIGGER, ability, act, 0, 0);
        }
        // 4. level bounds — compile-guaranteed; a negative level can never fire
        if (ability.level() < 0) {
            return record(GateOutcome.OUT_OF_LEVEL, ability, act, ability.level(), 0);
        }
        // 5. suppression — the per-activation set (legacy/role scratch) OR a per-player timed DISABLE_* across
        //    the three scopes (enchant/group/type), keyed identically to cooldowns. Split so the recorder knows
        //    which arm matched (transient vs timed), and the timed arm can name the suppressor.
        if (!ability.suppressImmune() && act.suppression().contains(ability.suppressKey())) {
            return record(GateOutcome.SUPPRESSED, ability, act,
                    WhyRing.packScope(0, 0, ability.suppressKey()), -1); // transient; id = suppress interner
        }
        if (suppressed(ability, act)) {
            long d = suppression.blockedDetail(ability, act.actor(), act.nowTicks());
            // d==0 is the benign cross-region race: suppressesAny won, then the window evicted before
            // blockedDetail re-read it. No live window to attribute, so record unattributed (pB -1) — else
            // detailByDefId(0)=0 would render a spurious "from <defId 0's key>" clause in /se why.
            int byDefId = d == 0 ? -1 : SuppressionStore.detailByDefId(d);
            return record(GateOutcome.SUPPRESSED, ability, act,
                    WhyRing.packScope(1, SuppressionStore.detailScopeKind(d), SuppressionStore.detailScopeId(d)),
                    byDefId);
        }
        // 6. cooldown (ENCHANT scope only) — atomically RESERVE here (check-and-arm fused) so two same-tick Folia
        //    hits on one key can't both pass; a later gate failure rolls the reservation back, ACTIVATED commits
        //    it. The group/type scope ids are gate-5 suppression match keys ONLY: arming them here made every
        //    cooldown-carrying enchant lock out its whole group's cooldown-0 siblings (rage, armored) with
        //    near-100% uptime on a god kit — the ADR-0050 R4 field regression.
        long cd = reserveCooldowns(ability, act); // 0 = reserved/ready
        if (cd != 0) {
            return record(GateOutcome.ON_COOLDOWN, ability, act,
                    (int) cd,           // low 32: packScope(0, scopeKind, scopeId)
                    (int) (cd >>> 32)); // high 32: remaining ticks (fits: remaining <= int duration)
        }
        // 7. condition + chanceΔ — AST walk over the primitive FactBuffer, no alloc
        ConditionResult cond = ConditionEvaluator.eval(ability.condition(), act.facts());
        if (cond.flow() == Flow.STOP) {
            releaseCooldowns(ability, act); // a condition fail must NOT arm the cooldown
            return record(GateOutcome.CONDITION_FAILED, ability, act, 0, 0);
        }
        // 8. chance roll — roll [0,100) < (base + Δ); FORCE/ALLOW skip the roll. Basis points are captured for
        //    both the fail path and ACTIVATED (rollBp -1 = no roll: forced/allowed).
        int rollBp = -1;
        double chance = chanceOf(ability, act) + cond.chanceDelta();
        int chanceBp = (int) Math.round(chance * 100.0);
        if (cond.flow() != Flow.FORCE && cond.flow() != Flow.ALLOW) {
            double roll = act.chanceRoll().getAsDouble();
            rollBp = (int) Math.round(roll * 100.0);
            if (!(roll < chance)) {
                if (!spendCooldownOnChanceFail) {
                    releaseCooldowns(ability, act); // hot path: a chance fail must NOT arm the cooldown
                }
                // use path: keep the gate-6 reservation so the failed attempt spends the cooldown (charge-per-attempt).
                return record(GateOutcome.CHANCE_FAILED, ability, act, rollBp, chanceBp);
            }
        }
        // 9. PreActivate — injected; cancellable
        if (!preActivate.allows(ability, act)) {
            releaseCooldowns(ability, act);
            return record(GateOutcome.CANCELLED, ability, act, 0, 0);
        }
        // 10. soul cost — only if a gem is active (§3.3); single-authority debit. Fail code = pA (0 no gem, 1 pool
        //     short). The ESCALATED price is what /se why renders as COST, so it is what the abort reports.
        int cost = soulCostFor(ability, act);
        int soulFail = consumeSouls(ability, act, cost);
        if (soulFail >= 0) {
            releaseCooldowns(ability, act);
            return record(GateOutcome.NO_SOULS, ability, act, soulFail, cost);
        }
        // 11. ACTIVATED — the gate-6 reservation IS the arm, so commit is implicit (zero-width pass-then-arm window).
        return record(GateOutcome.ACTIVATED, ability, act, rollBp, chanceBp);
    }

    /**
     * The remaining ticks on {@code ability}'s first blocked cooldown scope against {@code act}, or {@code 0}
     * when every scope is ready — the read-back the COLD use-item path surfaces as {@code {TIME_FORMATTED}}
     * (§3.6). A pure read (the same lazy eviction gate 6 does), so calling it after an {@code ON_COOLDOWN}
     * verdict reports the identical remainder the gate saw.
     */
    public long remainingCooldownTicks(Ability ability, Activation act) {
        long cd = blockedCooldown(ability, act);
        return cd == 0 ? 0 : (cd >>> 32);
    }

    /**
     * The consume-time feedback carried by the TIMED suppression window that blocked {@code ability}, or
     * {@code null} — the DISPATCH layer's read-back after a {@code SUPPRESSED} verdict, exactly as
     * {@link #remainingCooldownTicks} is read back after {@code ON_COOLDOWN}. A pure re-read, so the pipeline
     * stays Bukkit-free and free of any notion of who gets told; the emit belongs to whoever holds the
     * player handles. {@code null} on the transient per-activation set and on one-shots, neither of which
     * carries an authored window.
     */
    public SuppressionStore.Feedback suppressionFeedback(Ability ability, Activation act) {
        return ability.suppressImmune() ? null
                : suppression.blockedFeedback(ability, act.actor(), act.nowTicks());
    }

    /** Report one attempt's verdict + per-gate payload to the recorder, then return the verdict (ADR-0045). */
    /**
     * The ability's chance for THIS activation: the primitive when {@code chance:} was a constant (one null
     * check on the hot path), else the expression over the already-populated buffer, clamped to {@code [0,100]}
     * so a runaway variable can neither guarantee nor forbid a proc.
     */
    private static double chanceOf(Ability ability, Activation act) {
        NumExpr expr = ability.chanceExpr();
        if (expr == null) {
            return ability.baseChance();
        }
        double value = NumExprEval.eval(expr, act.facts());
        if (!Double.isFinite(value)) {
            return 0.0; // a missing variable degrades to "never", never to a NaN comparison
        }
        return Math.min(Math.max(value, 0.0), 100.0);
    }

    private GateOutcome record(GateOutcome out, Ability ability, Activation act, int pA, int pB) {
        recorder.record(act.actor(), act.nowTicks(), act.triggerId(), ability.defId(), out.ordinal(), pA, pB);
        return out;
    }

    /**
     * Whether any of {@code ability}'s three scopes is under an active timed {@code DISABLE_*} — mirrors
     * {@link #cooldownsReady} over the SAME packed scope keys, so {@code SUPPRESS:ENCHANT|GROUP|TYPE:key}
     * silences exactly the abilities whose scope lowered to that key.
     */
    private boolean suppressed(Ability ability, Activation act) {
        return suppression.suppressesAny(ability, act.actor(), act.nowTicks());
    }

    /**
     * The ENCHANT-scope cooldown blocking {@code ability}, packed as {@code (remaining << 32) |
     * packScope(0, scopeKind, scopeId)}, or {@code 0} when ready. Same map gets + lazy eviction as the gate.
     * Group/type scope ids are deliberately NOT consulted: they exist so gate-5 {@code SUPPRESS:GROUP|TYPE}
     * can match this ability, and letting them participate in cooldowns made every cooldown-carrying enchant
     * arm its whole GROUP — locking out cooldown-0 same-group siblings (rage, armored) fight-long (ADR-0050 R4).
     */
    private long blockedCooldown(Ability ability, Activation act) {
        return scopeRemaining(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, ability, act);
    }

    private long scopeRemaining(int scopeId, int scopeKind, Ability ability, Activation act) {
        if (scopeId < 0) {
            return 0; // no cooldown on this scope
        }
        // Cooldowns route by target bucket (mob vs player): proccing on a mob never spends the player route's cooldown.
        long rem = cooldowns.remainingTicks(act.actor(), victimDimension(ability, act),
                CooldownStore.key(scopeKind, scopeId, act.targetBucket()), act.nowTicks());
        return rem == 0 ? 0 : (rem << 32) | (WhyRing.packScope(0, scopeKind, scopeId) & 0xFFFF_FFFFL);
    }

    /**
     * The cooldown dimension this activation keys in: the victim for a {@code cooldown-per-victim} ability,
     * else {@code null} — the coarse player/mob bucket every other ability shares.
     *
     * <p>A per-victim ability with NO victim (a non-combat trigger) deliberately falls back to the coarse
     * bucket: the authored cooldown still holds, where keying on nothing at all would silently disable it and
     * let the ability fire every tick.
     */
    private static UUID victimDimension(Ability ability, Activation act) {
        return ability.cooldownPerVictim() ? act.victimId() : null;
    }

    /**
     * Atomically reserve {@code ability}'s ENCHANT-scope cooldown at gate 6: the acquire both checks and arms,
     * so two same-tick Folia hits on one key can't both pass (exactly one wins the CAS). Returns
     * {@code (remaining << 32) | packScope(0, scopeKind, scopeId)} when blocked, else {@code 0}. A later gate
     * failure calls {@link #releaseCooldowns}; ACTIVATED commits the reservation implicitly. The enchant scope
     * is the stable key base, so the four armor copies of one enchant (and its levels) share ONE cooldown —
     * the ADR-0050 stacking rule — while group/type ids stay suppression-only (see {@link #blockedCooldown}).
     */
    private long reserveCooldowns(Ability ability, Activation act) {
        return acquireScope(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, ability, act);
    }

    private long acquireScope(int scopeId, int scopeKind, Ability ability, Activation act) {
        if (scopeId < 0) {
            return 0; // no cooldown on this scope
        }
        // Cooldowns route by target bucket (mob vs player): proccing on a mob never spends the player route's cooldown.
        long rem = cooldowns.tryAcquire(act.actor(), victimDimension(ability, act),
                CooldownStore.key(scopeKind, scopeId, act.targetBucket()), act.nowTicks(), ability.cooldownTicks());
        return rem == 0 ? 0 : (rem << 32) | (WhyRing.packScope(0, scopeKind, scopeId) & 0xFFFF_FFFFL);
    }

    /** Roll back the ENCHANT-scope reservation gate 6 made for {@code ability} — restores readiness on a later fail. */
    private void releaseCooldowns(Ability ability, Activation act) {
        releaseScope(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, ability, act);
    }

    private void releaseScope(int scopeId, int scopeKind, Ability ability, Activation act) {
        // Only a positive-duration scope wrote a reservation; the reserved expiry is recomputable (no carried state).
        if (scopeId >= 0 && ability.cooldownTicks() > 0) {
            cooldowns.release(act.actor(), victimDimension(ability, act),
                    CooldownStore.key(scopeKind, scopeId, act.targetBucket()),
                    act.nowTicks() + ability.cooldownTicks());
        }
    }

    /** Gate 10: {@code -1} = paid (or free), else the NO_SOULS pA fail code (0 = no active gem, 1 = pool short). */
    private int consumeSouls(Ability ability, Activation act, int cost) {
        if (cost <= 0) {
            return -1; // free — not a soul-cost ability
        }
        boolean paid;
        if (act.activeGem() == null) {
            if (!ability.soulCostCarried()) {
                return 0; // §J a soul-cost ability NEVER fires outside soul mode (was: fired free — the bug)
            }
            // soul-cost-carried: the gem is a wallet, not a switch — charge the carried gems directly.
            paid = spender.trySpendCarried(act.actor(), cost);
        } else {
            // In soul mode: fire only if the player's cross-gem pool can pay — all-or-nothing, no partial spend.
            paid = spender.trySpend(act.actor(), cost);
        }
        if (!paid) {
            return 1; // wallet short — the abort must NOT advance the ladder, or the retry price runs away
        }
        if (ability.soulCostGrowth() != 1.0) {
            escalation.step(act.actor(), escalationScope(ability), act.nowTicks(), ability.soulCostDecayPeriod());
        }
        return -1;
    }

    /**
     * The price THIS activation is charged: the authored {@code soul-cost} compounded by the actor's escalation
     * steps for this ability. A static price (growth 1.0 — nearly all content) reads no store at all.
     */
    private int soulCostFor(Ability ability, Activation act) {
        int base = ability.soulCost();
        if (base <= 0 || ability.soulCostGrowth() == 1.0) {
            return SoulEscalationStore.escalatedCost(base, 1.0, ability.soulCostCap(), 0);
        }
        return SoulEscalationStore.escalatedCost(base, ability.soulCostGrowth(), ability.soulCostCap(),
                escalation.steps(act.actor(), escalationScope(ability), act.nowTicks(),
                        ability.soulCostDecayPeriod()));
    }

    /**
     * The escalation counter's key: the ability's COOLDOWN scope, so "per ability" means exactly what it means
     * for cooldowns (the four armour copies and every level of one enchant share one price ladder) and stays
     * stable across reload and relog. Scope-less sources fall back to their suppression key, then their defId,
     * each in its own namespace so two interners cannot collide into one ladder.
     *
     * <p>Deliberately blind to the target bucket and to {@code cooldown-per-victim}: the ladder is per-actor-
     * per-ability, so switching targets must not rewind an escalating price.
     */
    private static long escalationScope(Ability ability) {
        if (ability.cdScopeEnchant() >= 0) {
            return CooldownStore.key(ScopeKinds.ENCHANT, ability.cdScopeEnchant());
        }
        if (ability.suppressKey() >= 0) {
            return CooldownStore.key(ESCALATION_BY_SUPPRESS_KEY, ability.suppressKey());
        }
        return CooldownStore.key(ESCALATION_BY_DEF_ID, ability.defId());
    }
}
