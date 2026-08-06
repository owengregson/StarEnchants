package compile.model.cond;

import java.util.List;

/**
 * A numeric-valued operand of a compiled condition <em>or an expression-valued effect argument</em>
 * (docs/architecture.md §3.4): a resolved {@code FactBuffer} slot, a constant, a PlaceholderAPI token
 * parsed to a number at evaluation time, or an arithmetic combination of those. Pure data — the runtime
 * walks it over a primitive fact buffer with no parsing on the hot path (see the engine's {@code NumExprEval}).
 */
public sealed interface NumExpr
        permits NumExpr.Var, NumExpr.Lit, NumExpr.Papi, NumExpr.Bin, NumExpr.Neg, NumExpr.Fn,
                NumExpr.EntityVar, NumExpr.PotionLevel, NumExpr.EnchantLevel, NumExpr.CrystalCount,
                NumExpr.SubjectNum {

    /** A numeric variable resolved to its dense {@code FactBuffer} number slot. */
    record Var(int slot) implements NumExpr {}

    /** A numeric literal, pre-parsed at compile time. */
    record Lit(double value) implements NumExpr {}

    /**
     * A PlaceholderAPI token used in a numeric comparison; the engine resolves the
     * placeholder and parses it to a double only when this node is reached, and only
     * if PlaceholderAPI is present (§3.4). The {@code raw} token is the original
     * {@code %...%} text (without the surrounding percents).
     */
    record Papi(String raw) implements NumExpr {}

    record Bin(NumExpr left, Op op, NumExpr right) implements NumExpr {}

    record Neg(NumExpr operand) implements NumExpr {}

    /**
     * A dynamic var read from a NAMED entity rather than the activator — {@code %victim.var.<name>%}.
     * Author-named vars can't be enumerated at compile time, so this carries the name to a runtime
     * {@code VarStore} lookup scoped to {@link Scope}; unset/absent reads {@code 0} (§5.4).
     */
    record EntityVar(Scope scope, String name) implements NumExpr {}

    /**
     * An entity's active level of one potion effect — {@code %actor.potion.<effect>%} /
     * {@code %victim.potion.<effect>%}. The {@code <effect>} token resolves to an interned handle at COMPILE
     * time (§9), so the runtime never sees a name and never touches a renamed constant. The value is
     * {@code amplifier + 1} (Strength I reads 1) and {@code 0} when the effect is absent, which makes
     * {@code > 0} the boolean idiom.
     */
    record PotionLevel(Scope scope, int handleId) implements NumExpr {}

    /**
     * An entity's worn level of one custom enchant — {@code %actor.enchlevel.<key>%} /
     * {@code %victim.enchlevel.<key>%}, {@code 0} when not worn. Unlike {@link PotionLevel} the key stays a
     * STRING: dense ids and the stable-key index are assigned by the ERASE stage, which runs after conditions
     * lower, so no id exists yet — this follows {@link EntityVar}'s lazy-runtime-lookup template instead.
     * {@code key} is the lower-cased enchant stem, matching the runtime's canonically lower-cased worn map.
     */
    record EnchantLevel(Scope scope, String key) implements NumExpr {}

    /** {@code %scope.crystals.<key>%} — worn ARMOUR pieces carrying that crystal, 0..4 (R-QC52). */
    record CrystalCount(Scope scope, String key) implements NumExpr {}

    /**
     * A numeric fact of the SUBJECT CURSOR that owns no {@code FactBuffer} slot — {@code %target.souls%},
     * {@code %target.heroicpieces%}, {@code %target.roll%} (ADR-0076). Their actor/victim twins are populated
     * slots, but a per-body value cannot be: the cursor re-points N times inside one activation, so these read
     * LAZILY off the bound subject exactly as the keyed families do.
     */
    record SubjectNum(SubjectFact fact) implements NumExpr {}

    /** The slot-less numeric facts {@link SubjectNum} can name. */
    enum SubjectFact {
        /** The bound body's cached cross-gem soul total; {@code 0} for a mob. */
        SOULS,
        /** How many of the bound body's four worn armour pieces carry a heroic upgrade. */
        HEROIC_PIECES,
        /** The ONE uniform {@code [0,100)} draw taken when the cursor bound this body — {@code each-chance}'s. */
        ROLL
    }

    /**
     * Which entity of the activation an entity-scoped operand reads from. {@code TARGET} is the SUBJECT CURSOR
     * (ADR-0076): the body an effect's selector resolved, re-bound per target inside gate 12 — legal only on an
     * effect row, never in an ability's {@code condition:}/{@code chance:}, which run before any selector does.
     */
    enum Scope { ACTOR, VICTIM, TARGET }

    /** A function over nested operands; {@code args} arity is guaranteed by the parser's {@code ExprFn} check. */
    record Fn(FnKind kind, List<NumExpr> args) implements NumExpr {
        public Fn {
            args = List.copyOf(args);
        }
    }

    enum Op { ADD, SUBTRACT, MULTIPLY, DIVIDE }

    /** The lowered counterpart of {@code schema.grammar.expr.ExprFn} (compile owns its own IR vocabulary). */
    enum FnKind { MIN, MAX, CLAMP, FLOOR, RAND }
}
