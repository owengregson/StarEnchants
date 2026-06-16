package engine.pipeline;

import compile.model.Ability;
import engine.condition.ConditionEvaluator;
import engine.condition.ConditionResult;
import engine.condition.Flow;
import engine.interact.SoulLedger;
import engine.stores.CooldownStore;
import java.util.Objects;

/**
 * The activation pipeline: the fixed EE gate sequence (docs/architecture.md §3.3),
 * preserved exactly and identically for every source — which is what prevents the
 * EE-path/EA-path drift that broke the originals. Each gate is a compiled
 * integer/bitset/primitive check, not a string op. A System walks the candidate
 * abilities for a trigger and runs each through {@link #evaluate}; an {@link GateOutcome#ACTIVATED}
 * result means the caller should run the ability's effects (gate 12).
 *
 * <p>Gates 1, 3–8, 10, 11 are pure logic over the {@link Ability} and {@link Activation};
 * gate 2 (protection) and gate 9 ({@code PreActivate}) are injected {@link Guard}s
 * (default allow) so the cross-version/Bukkit edges stay outside this pure core. Gates 10
 * (soul debit) and 11 (cooldown arm) have side effects on the {@link SoulLedger} and
 * {@link CooldownStore} — performed only once every preceding gate has passed, matching
 * the EE ordering (souls are spent after {@code PreActivate}, cooldown armed last).
 */
public final class ActivationPipeline {

    /** A pluggable gate (protection at gate 2, {@code PreActivate} at gate 9). */
    @FunctionalInterface
    public interface Guard {
        boolean allows(Ability ability, Activation activation);

        /** Always allows — the default for both injected gates. */
        Guard ALLOW = (ability, activation) -> true;
    }

    /** Cooldown scope kinds, packed into the {@link CooldownStore} key so the three never collide. */
    private static final int SCOPE_ENCHANT = 0;
    private static final int SCOPE_GROUP = 1;
    private static final int SCOPE_TYPE = 2;

    private final CooldownStore cooldowns;
    private final SoulLedger souls;
    private final Guard protection;
    private final Guard preActivate;

    public ActivationPipeline(CooldownStore cooldowns, SoulLedger souls) {
        this(cooldowns, souls, Guard.ALLOW, Guard.ALLOW);
    }

    public ActivationPipeline(CooldownStore cooldowns, SoulLedger souls,
                              Guard protection, Guard preActivate) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.preActivate = Objects.requireNonNull(preActivate, "preActivate");
    }

    /** Run {@code ability} through every gate against {@code act}, returning where it stopped. */
    public GateOutcome evaluate(Ability ability, Activation act) {
        // 1. world blacklist — primitive AND
        if (ability.blockedInWorld(act.worldId())) {
            return GateOutcome.BLOCKED_WORLD;
        }
        // 2. protection / region — injected, cached per tick in production
        if (!protection.allows(ability, act)) {
            return GateOutcome.BLOCKED_PROTECTION;
        }
        // 3. trigger-match (slot applicability is pre-filtered into WornState.byTrigger)
        if (!ability.firesOn(act.triggerId())) {
            return GateOutcome.WRONG_TRIGGER;
        }
        // 4. level bounds — compile-guaranteed; a negative level can never fire
        if (ability.level() < 0) {
            return GateOutcome.OUT_OF_LEVEL;
        }
        // 5. suppression — O(1) interned-id membership
        if (act.suppression().contains(ability.suppressKey())) {
            return GateOutcome.SUPPRESSED;
        }
        // 6. cooldown (three scopes) — primitive long map
        if (!cooldownsReady(ability, act)) {
            return GateOutcome.ON_COOLDOWN;
        }
        // 7. condition + chanceΔ — AST walk over the primitive FactBuffer, no alloc
        ConditionResult cond = ConditionEvaluator.eval(ability.condition(), act.facts());
        if (cond.flow() == Flow.STOP) {
            return GateOutcome.CONDITION_FAILED;
        }
        // 8. chance roll — roll [0,100) < (base + Δ); FORCE/ALLOW skip the roll
        if (!rollPasses(ability, cond, act)) {
            return GateOutcome.CHANCE_FAILED;
        }
        // 9. PreActivate — injected; cancellable
        if (!preActivate.allows(ability, act)) {
            return GateOutcome.CANCELLED;
        }
        // 10. soul cost — only if a gem is active (§3.3); single-authority debit
        if (!consumeSouls(ability, act)) {
            return GateOutcome.NO_SOULS;
        }
        // 11. start cooldown
        armCooldowns(ability, act);
        return GateOutcome.ACTIVATED;
    }

    private boolean cooldownsReady(Ability ability, Activation act) {
        return scopeReady(ability.cdScopeEnchant(), SCOPE_ENCHANT, act)
                && scopeReady(ability.cdScopeGroup(), SCOPE_GROUP, act)
                && scopeReady(ability.cdScopeType(), SCOPE_TYPE, act);
    }

    private boolean scopeReady(int scopeId, int scopeKind, Activation act) {
        if (scopeId < 0) {
            return true; // no cooldown on this scope
        }
        return cooldowns.ready(act.actor(), CooldownStore.key(scopeKind, scopeId), act.nowTicks());
    }

    private void armCooldowns(Ability ability, Activation act) {
        armScope(ability.cdScopeEnchant(), SCOPE_ENCHANT, ability.cooldownTicks(), act);
        armScope(ability.cdScopeGroup(), SCOPE_GROUP, ability.cooldownTicks(), act);
        armScope(ability.cdScopeType(), SCOPE_TYPE, ability.cooldownTicks(), act);
    }

    private void armScope(int scopeId, int scopeKind, int durationTicks, Activation act) {
        if (scopeId >= 0) {
            cooldowns.arm(act.actor(), CooldownStore.key(scopeKind, scopeId), act.nowTicks(), durationTicks);
        }
    }

    private static boolean rollPasses(Ability ability, ConditionResult cond, Activation act) {
        if (cond.flow() == Flow.FORCE || cond.flow() == Flow.ALLOW) {
            return true; // forced / allowed: skip the roll entirely
        }
        double chance = ability.baseChance() + cond.chanceDelta();
        return act.chanceRoll().getAsDouble() < chance;
    }

    private boolean consumeSouls(Ability ability, Activation act) {
        if (ability.soulCost() <= 0) {
            return true; // free
        }
        if (act.activeGem() == null) {
            return true; // not in soul mode → the soul cost gate does not apply (§3.3)
        }
        return souls.tryConsume(act.activeGem(), act.gemBalance(), ability.soulCost());
    }
}
