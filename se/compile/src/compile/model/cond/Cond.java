package compile.model.cond;

import java.util.regex.Pattern;
import schema.grammar.expr.Cmp;

/**
 * The boolean-valued root of a compiled condition: a typed, slot-resolved tree the runtime evaluates over
 * a thread-local {@code FactBuffer} to gate an activation (docs/architecture.md §3.2, §3.4). Variables are
 * dense slots and literals pre-parsed, so the hot path does no string work and no boxing.
 *
 * <p>Comparisons split by operand type because legal operators differ: numbers admit all six comparators,
 * strings and booleans only equality.
 */
public sealed interface Cond
        permits Cond.And, Cond.Or, Cond.Not,
                Cond.NumCmp, Cond.StrCmp, Cond.BoolCmp,
                Cond.StrContains, Cond.Regex,
                Cond.BoolVar, Cond.BoolLit, Cond.BoolPapi {

    /** Short-circuiting logical AND. */
    record And(Cond left, Cond right) implements Cond {}

    /** Short-circuiting logical OR. */
    record Or(Cond left, Cond right) implements Cond {}

    record Not(Cond operand) implements Cond {}

    /** A numeric comparison {@code left op right} (any of the six comparators). */
    record NumCmp(NumExpr left, Cmp op, NumExpr right) implements Cond {}

    /** A string (in)equality test; {@code equal} is {@code true} for {@code ==}, {@code false} for {@code !=}. */
    record StrCmp(StrExpr left, boolean equal, StrExpr right) implements Cond {}

    /** A boolean (in)equality test; {@code equal} is {@code true} for {@code ==}, {@code false} for {@code !=}. */
    record BoolCmp(Cond left, boolean equal, Cond right) implements Cond {}

    /**
     * A {@code contains} membership test: true if {@code left} contains any of {@code alternatives}
     * (case-insensitive substring). Like {@link Regex}, the alternative list is a literal split on {@code |}
     * and lower-cased ONCE at load, so the hot path is an allocation-free {@code regionMatches} scan — never
     * a per-evaluation {@code String#split} + {@code toLowerCase} (docs/architecture.md §3.4, performance-hot-paths).
     * Empty alternatives are dropped at compile, so the array is never {@code null} and holds no empty string.
     */
    record StrContains(StrExpr left, String[] alternatives) implements Cond {}

    /**
     * A {@code matchesregex} full-match test: true if {@code left} fully matches {@code pattern}. The
     * pattern is a literal compiled once at load (so a bad regex is a load-time diagnostic, never a
     * per-evaluation cost or a syntax surprise at runtime).
     */
    record Regex(StrExpr left, Pattern pattern) implements Cond {}

    /** A boolean variable resolved to its dense {@code FactBuffer} flag slot. */
    record BoolVar(int slot) implements Cond {}

    record BoolLit(boolean value) implements Cond {}

    /**
     * A PlaceholderAPI token used in a boolean context — produced when a placeholder
     * is compared to a boolean (e.g. {@code %essentials_afk% == true}). The engine
     * resolves the placeholder at evaluation time and reads its result as truthy
     * ({@code true/yes/on/1}); an absent placeholder is {@code false}. The {@code raw}
     * token is the {@code %...%} text without the surrounding percents.
     */
    record BoolPapi(String raw) implements Cond {}
}
