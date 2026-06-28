package schema.spec;

import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprParser;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The type of a single DSL argument. Immutable with optional constraints; the fluent
 * wither methods ({@link #min}, {@link #def}, …) each return a new instance, so the
 * shared {@link D} bases ({@code D.DOUBLE.min(0).max(100)}) are reused freely. One
 * declaration drives parse, doc, and {@link #completions} (docs/architecture.md §7).
 */
public final class ParamType {

    public enum Kind {
        DOUBLE, INT, BOOL, STRING, ENUM,
        /** A non-negative integer count of ticks (a typed INT for durations). */
        TICKS,
        /** A version-volatile referent (material/sound/potion/…) resolved to an interned id. */
        HANDLE
    }

    private final Kind kind;
    private final boolean required;
    private final Double min;
    private final Double max;
    private final String defaultRaw;
    private final List<String> allowed; // ENUM only; canonical spellings
    private final HandleCategory handleCategory; // HANDLE only

    private ParamType(Kind kind, boolean required, Double min, Double max,
                      String defaultRaw, List<String> allowed, HandleCategory handleCategory) {
        this.kind = kind;
        this.required = required;
        this.min = min;
        this.max = max;
        this.defaultRaw = defaultRaw;
        this.allowed = allowed;
        this.handleCategory = handleCategory;
    }

    static ParamType of(Kind kind) {
        Double lo = kind == Kind.TICKS ? 0.0 : null; // TICKS implicitly floored at 0
        return new ParamType(kind, true, lo, null, null, null, null);
    }

    /** A version-volatile {@code HANDLE} of the given category, resolved at compile time (§9). */
    static ParamType handle(HandleCategory category) {
        return new ParamType(Kind.HANDLE, true, null, null, null, null, category);
    }

    private ParamType with(Boolean req, Double mn, Double mx, String def, List<String> al) {
        return new ParamType(kind,
                req != null ? req : required,
                mn != null ? mn : min,
                mx != null ? mx : max,
                def != null ? def : defaultRaw,
                al != null ? al : allowed,
                handleCategory);
    }

    /** Lower bound (inclusive) for numeric kinds. */
    public ParamType min(double m) {
        return with(null, m, null, null, null);
    }

    /** Upper bound (inclusive) for numeric kinds. */
    public ParamType max(double m) {
        return with(null, null, m, null, null);
    }

    /** Inclusive numeric range, shorthand for {@code min(lo).max(hi)}. */
    public ParamType range(double lo, double hi) {
        return with(null, lo, hi, null, null);
    }

    /** A default value (also makes the arg optional); stored as raw text and run through {@link #parse}, so a bad default is a diagnostic. */
    public ParamType def(Object value) {
        return with(false, null, null, String.valueOf(value), null);
    }

    /** Optional with no default (absent → not present in args). */
    public ParamType optional() {
        return with(false, null, null, null, null);
    }

    public ParamType requiredArg() {
        return with(true, null, null, null, null);
    }

    /** Restrict an {@code ENUM} to a fixed, case-insensitive value set. */
    ParamType allowing(String... values) {
        return with(null, null, null, null, List.of(values));
    }

    public Kind kind() {
        return kind;
    }

    /** The handle category for a {@code HANDLE} type, else {@code null}. */
    public HandleCategory handleCategory() {
        return handleCategory;
    }

    public boolean isRequired() {
        return required;
    }

    public Optional<String> defaultRaw() {
        return Optional.ofNullable(defaultRaw);
    }

    public OptionalDouble min() {
        return min == null ? OptionalDouble.empty() : OptionalDouble.of(min);
    }

    public OptionalDouble max() {
        return max == null ? OptionalDouble.empty() : OptionalDouble.of(max);
    }

    public List<String> allowed() {
        return allowed == null ? List.of() : allowed;
    }

    /** Validate a raw token into a typed value ({@link Double}/{@link Long}/{@link Boolean}/{@link String}), or empty if invalid (diagnostic recorded). */
    public Optional<Object> parse(String raw, Source source, Diagnostics diags) {
        return switch (kind) {
            case DOUBLE -> parseDouble(raw, source, diags);
            case INT, TICKS -> parseLong(raw, source, diags);
            case BOOL -> parseBool(raw, source, diags);
            case ENUM -> parseEnum(raw, source, diags);
            case STRING -> Optional.of(raw);
            case HANDLE -> parseHandle(raw, source, diags);
        };
    }

    private Optional<Object> parseHandle(String raw, Source source, Diagnostics diags) {
        String t = raw.trim();
        if (t.isEmpty()) {
            diags.error(DiagCode.E_TYPE, "expected a " + handleCategory.label() + " name but got an empty token", source);
            return Optional.empty();
        }
        // Token survives verbatim; resolve interns it (§9) and warns-and-skips unknowns, not here.
        return Optional.of(t);
    }

    private Optional<Object> parseDouble(String raw, Source source, Diagnostics diags) {
        String t = raw.trim();
        double v;
        try {
            v = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            // A %var%/arithmetic expression is a valid numeric argument, evaluated per-activation (§3.4).
            if (looksLikeExpression(t)) {
                return numericExpression(t, source, diags);
            }
            diags.error(DiagCode.E_TYPE, "expected a number but got '" + raw + "'", source,
                    "use a decimal like 2.5, or a %variable% expression like %combo% * 10");
            return Optional.empty();
        }
        if (!Double.isFinite(v)) {
            diags.error(DiagCode.E_TYPE, "number must be finite but got '" + raw + "'", source);
            return Optional.empty();
        }
        return checkRange(v, source, diags) ? Optional.of(v) : Optional.empty();
    }

    private Optional<Object> parseLong(String raw, Source source, Diagnostics diags) {
        String t = raw.trim();
        long v;
        try {
            v = Long.parseLong(t);
        } catch (NumberFormatException e) {
            // An expression is admissible here too; its value is narrowed to a whole number at read time.
            if (looksLikeExpression(t)) {
                return numericExpression(t, source, diags);
            }
            // A decimal where an integer is required is the classic getInt() trap.
            String hint = t.contains(".") ? "use a whole number (no decimal point)" : "use a whole number";
            diags.error(DiagCode.E_TYPE, "expected a whole number but got '" + raw + "'", source, hint);
            return Optional.empty();
        }
        return checkRange((double) v, source, diags) ? Optional.of(v) : Optional.empty();
    }

    /**
     * Whether a non-literal numeric token is an expression (a {@code %var%} or arithmetic). A leading
     * sign alone never reaches here (a signed literal parses), so only an interior op marks arithmetic.
     */
    private static boolean looksLikeExpression(String t) {
        return t.indexOf('%') >= 0 || t.indexOf('*') >= 0 || t.indexOf('/') >= 0
                || t.indexOf('+', 1) >= 0 || t.indexOf('-', 1) >= 0;
    }

    /**
     * Parse a numeric-argument token as an expression, producing the untyped {@link Expr} AST.
     * Range bounds cannot be checked statically on an expression, so they are not — the author owns the value.
     */
    private static Optional<Object> numericExpression(String token, Source source, Diagnostics diags) {
        return ExprParser.parse(token, source, diags).map(expr -> expr);
    }

    private boolean checkRange(double v, Source source, Diagnostics diags) {
        if (min != null && v < min) {
            diags.error(DiagCode.E_RANGE, "value " + trim(v) + " is below the minimum " + trim(min), source);
            return false;
        }
        if (max != null && v > max) {
            diags.error(DiagCode.E_RANGE, "value " + trim(v) + " is above the maximum " + trim(max), source);
            return false;
        }
        return true;
    }

    private Optional<Object> parseBool(String raw, Source source, Diagnostics diags) {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "true", "yes", "on", "1" -> Optional.of(Boolean.TRUE);
            case "false", "no", "off", "0" -> Optional.of(Boolean.FALSE);
            default -> {
                diags.error(DiagCode.E_TYPE, "expected true or false but got '" + raw + "'", source);
                yield Optional.empty();
            }
        };
    }

    private Optional<Object> parseEnum(String raw, Source source, Diagnostics diags) {
        String t = raw.trim();
        for (String canon : allowed()) {
            if (canon.equalsIgnoreCase(t)) {
                return Optional.of(canon); // normalize to the canonical spelling
            }
        }
        diags.error(DiagCode.E_ENUM, "'" + raw + "' is not one of " + allowed(), source,
                "allowed values: " + String.join(", ", allowed()));
        return Optional.empty();
    }

    /** Tab-completion candidates for a partial token (enums + booleans only). */
    public List<String> completions(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case ENUM -> allowed().stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).toList();
            case BOOL -> List.of("true", "false").stream().filter(v -> v.startsWith(p)).toList();
            default -> List.of();
        };
    }

    /** A short type label for usage/doc strings, e.g. {@code double[0..100]}. */
    public String label() {
        StringBuilder sb = new StringBuilder(switch (kind) {
            case DOUBLE -> "double";
            case INT -> "int";
            case TICKS -> "ticks";
            case BOOL -> "bool";
            case STRING -> "string";
            case ENUM -> "enum";
            case HANDLE -> handleCategory.label();
        });
        if (kind == Kind.ENUM) {
            sb.append('{').append(String.join("|", allowed())).append('}');
        } else if (min != null || max != null) {
            sb.append('[')
                    .append(min != null ? trim(min) : "")
                    .append("..")
                    .append(max != null ? trim(max) : "")
                    .append(']');
        }
        return sb.toString();
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
