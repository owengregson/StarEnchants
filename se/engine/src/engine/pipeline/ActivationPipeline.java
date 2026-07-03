package engine.pipeline;

import compile.model.Ability;
import compile.model.ScopeKinds;
import engine.condition.ConditionEvaluator;
import engine.condition.ConditionResult;
import engine.condition.Flow;
import engine.interact.SoulSpender;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import engine.stores.WhyRecorder;
import engine.stores.WhyRing;
import java.util.Objects;

/**
 * The activation pipeline: the fixed Cosmic Enchants-style gate sequence (docs/architecture.md §3.3),
 * identical for every source so no per-source path can drift. Each gate is a compiled
 * integer/bitset/primitive check, not a string op. A System walks the candidate abilities for a trigger
 * and runs each through {@link #evaluate}; {@link GateOutcome#ACTIVATED} means the caller runs the
 * ability's effects (gate 12).
 *
 * <p>Gates 1, 3–8, 10, 11 are pure logic over the {@link Ability} and {@link Activation}; gate 2
 * (protection) and gate 9 ({@code PreActivate}) are injected {@link Guard}s (default allow) so the
 * cross-version/Bukkit edges stay outside this pure core. The side-effecting gates run only after every
 * preceding gate passes: souls debit (gate 10) AFTER {@code PreActivate}, cooldown armed last (gate 11).
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


    private final CooldownStore cooldowns;
    private final SoulSpender spender;
    private final SuppressionStore suppression;
    private final Guard protection;
    private final Guard preActivate;
    private final WhyRecorder recorder;

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
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.spender = Objects.requireNonNull(spender, "spender");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.preActivate = Objects.requireNonNull(preActivate, "preActivate");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    /** Run {@code ability} through every gate against {@code act}, returning where it stopped. Every path
     *  reports its verdict + per-gate payload to the {@link WhyRecorder} on the way out (ADR-0045). */
    public GateOutcome evaluate(Ability ability, Activation act) {
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
        if (act.suppression().contains(ability.suppressKey())) {
            return record(GateOutcome.SUPPRESSED, ability, act,
                    WhyRing.packScope(0, 0, ability.suppressKey()), -1); // transient; id = suppress interner
        }
        if (suppressed(ability, act)) {
            long d = suppression.blockedDetail(ability, act.actor(), act.nowTicks());
            return record(GateOutcome.SUPPRESSED, ability, act,
                    WhyRing.packScope(1, SuppressionStore.detailScopeKind(d), SuppressionStore.detailScopeId(d)),
                    SuppressionStore.detailByDefId(d));
        }
        // 6. cooldown (three scopes) — primitive long map; remaining captured for the flight recorder
        long cd = blockedCooldown(ability, act); // 0 = all ready
        if (cd != 0) {
            return record(GateOutcome.ON_COOLDOWN, ability, act,
                    (int) cd,           // low 32: packScope(0, scopeKind, scopeId)
                    (int) (cd >>> 32)); // high 32: remaining ticks (fits: remaining <= int duration)
        }
        // 7. condition + chanceΔ — AST walk over the primitive FactBuffer, no alloc
        ConditionResult cond = ConditionEvaluator.eval(ability.condition(), act.facts());
        if (cond.flow() == Flow.STOP) {
            return record(GateOutcome.CONDITION_FAILED, ability, act, 0, 0);
        }
        // 8. chance roll — roll [0,100) < (base + Δ); FORCE/ALLOW skip the roll. Basis points are captured for
        //    both the fail path and ACTIVATED (rollBp -1 = no roll: forced/allowed).
        int rollBp = -1;
        double chance = ability.baseChance() + cond.chanceDelta();
        int chanceBp = (int) Math.round(chance * 100.0);
        if (cond.flow() != Flow.FORCE && cond.flow() != Flow.ALLOW) {
            double roll = act.chanceRoll().getAsDouble();
            rollBp = (int) Math.round(roll * 100.0);
            if (!(roll < chance)) {
                return record(GateOutcome.CHANCE_FAILED, ability, act, rollBp, chanceBp);
            }
        }
        // 9. PreActivate — injected; cancellable
        if (!preActivate.allows(ability, act)) {
            return record(GateOutcome.CANCELLED, ability, act, 0, 0);
        }
        // 10. soul cost — only if a gem is active (§3.3); single-authority debit. Fail code = pA (0 no gem, 1 pool short).
        int soulFail = consumeSouls(ability, act);
        if (soulFail >= 0) {
            return record(GateOutcome.NO_SOULS, ability, act, soulFail, ability.soulCost());
        }
        // 11. start cooldown
        armCooldowns(ability, act);
        return record(GateOutcome.ACTIVATED, ability, act, rollBp, chanceBp);
    }

    /** Report one attempt's verdict + per-gate payload to the recorder, then return the verdict (ADR-0045). */
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
     * The first blocked cooldown scope (enchant&rarr;group&rarr;type) packed as {@code (remaining << 32) |
     * packScope(0, scopeKind, scopeId)}, or {@code 0} when every scope is ready. Same map gets + lazy eviction
     * as the old boolean check ({@code remainingTicks} mirrors {@code ready}), so the gate decision is
     * byte-identical — it only now also surfaces WHICH scope blocked and by how long.
     */
    private long blockedCooldown(Ability ability, Activation act) {
        long r = scopeRemaining(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, act);
        if (r != 0) {
            return r;
        }
        r = scopeRemaining(ability.cdScopeGroup(), ScopeKinds.GROUP, act);
        if (r != 0) {
            return r;
        }
        return scopeRemaining(ability.cdScopeType(), ScopeKinds.TYPE, act);
    }

    private long scopeRemaining(int scopeId, int scopeKind, Activation act) {
        if (scopeId < 0) {
            return 0; // no cooldown on this scope
        }
        // Cooldowns route by target bucket (mob vs player): proccing on a mob never spends the player route's cooldown.
        long rem = cooldowns.remainingTicks(act.actor(),
                CooldownStore.key(scopeKind, scopeId, act.targetBucket()), act.nowTicks());
        return rem == 0 ? 0 : (rem << 32) | (WhyRing.packScope(0, scopeKind, scopeId) & 0xFFFF_FFFFL);
    }

    private void armCooldowns(Ability ability, Activation act) {
        armScope(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, ability.cooldownTicks(), act);
        armScope(ability.cdScopeGroup(), ScopeKinds.GROUP, ability.cooldownTicks(), act);
        armScope(ability.cdScopeType(), ScopeKinds.TYPE, ability.cooldownTicks(), act);
    }

    private void armScope(int scopeId, int scopeKind, int durationTicks, Activation act) {
        if (scopeId >= 0) {
            cooldowns.arm(act.actor(), CooldownStore.key(scopeKind, scopeId, act.targetBucket()),
                    act.nowTicks(), durationTicks);
        }
    }

    /** Gate 10: {@code -1} = paid (or free), else the NO_SOULS pA fail code (0 = no active gem, 1 = pool short). */
    private int consumeSouls(Ability ability, Activation act) {
        if (ability.soulCost() <= 0) {
            return -1; // free — not a soul-cost ability
        }
        if (act.activeGem() == null) {
            return 0; // §J a soul-cost ability NEVER fires outside soul mode (was: fired free — the bug)
        }
        // In soul mode: fire only if the player's cross-gem pool can pay — all-or-nothing, no partial spend.
        return spender.trySpend(act.actor(), ability.soulCost()) ? -1 : 1; // 1 = pool short
    }
}
