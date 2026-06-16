package schema.spec;

import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The type of a single DSL argument — the atom of the StarEnchants type system.
 *
 * <p>A {@code ParamType} is an <em>immutable</em> value with optional constraints
 * (numeric bounds, an enum value set, a default). The fluent "wither" methods
 * ({@link #min}, {@link #max}, {@link #def}, {@link #optional}) each return a new
 * instance, so the shared bases on {@link D} (e.g. {@code D.DOUBLE}) can be reused
 * freely:
 *
 * <pre>{@code D.DOUBLE.min(0).max(100)}</pre>
 *
 * <p>One declaration drives all four uses (docs/architecture.md §1.2 D5, §7):
 * {@link #parse} validates authored text into a typed value (collecting a precise
 * {@link schema.diag.Diagnostic} on failure), {@link #usage}
 * documents it, and {@link #completions} powers tab-completion.
 */
public final class ParamType {

    /** The underlying value kind. */
    public enum Kind {
        DOUBLE, INT, BOOL, STRING, ENUM,
        /** A non-negative integer count of ticks (a typed INT for durations). */
        TICKS
    }

    private final Kind kind;
    private final boolean required;
    private final Double min;
    private final Double max;
    private final String defaultRaw;
    private final List<String> allowed; // ENUM only; canonical spellings

    private ParamType(Kind kind, boolean required, Double min, Double max,
                      String defaultRaw, List<String> allowed) {
        this.kind = kind;
        this.required = required;
        this.min = min;
        this.max = max;
        this.defaultRaw = defaultRaw;
        this.allowed = allowed;
    }

    static ParamType of(Kind kind) {
        // Required by default; a TICKS value is implicitly floored at 0.
        Double lo = kind == Kind.TICKS ? 0.0 : null;
        return new ParamType(kind, true, lo, null, null, null);
    }

    private ParamType with(Boolean req, Double mn, Double mx, String def, List<String> al) {
        return new ParamType(kind,
                req != null ? req : required,
                mn != null ? mn : min,
                mx != null ? mx : max,
                def != null ? def : defaultRaw,
                al != null ? al : allowed);
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

    /**
     * A default value, which also makes the argument optional. The default is
     * stored as raw text and validated through the same {@link #parse} path, so a
     * bad default is itself a diagnostic rather than a special case.
     */
    public ParamType def(Object value) {
        return with(false, null, null, String.valueOf(value), null);
    }

    /** Mark the argument optional with no default (absent → not present in args). */
    public ParamType optional() {
        return with(false, null, null, null, null);
    }

    /** Mark the argument required (the default state); included for readability. */
    public ParamType requiredArg() {
        return with(true, null, null, null, null);
    }

    /** Restrict an {@code ENUM} type to a fixed, case-insensitive value set. */
    ParamType allowing(String... values) {
        return with(null, null, null, null, List.of(values));
    }

    public Kind kind() {
        return kind;
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

    /**
     * Validate a raw argument token into a typed value, reporting any fault into
     * {@code diags} at {@code source}.
     *
     * @return the typed value ({@link Double}, {@link Long}, {@link Boolean} or
     *     {@link String}), or empty if the token was invalid (a diagnostic was
     *     recorded).
     */
    public Optional<Object> parse(String raw, Source source, Diagnostics diags) {
        return switch (kind) {
            case DOUBLE -> parseDouble(raw, source, diags);
            case INT, TICKS -> parseLong(raw, source, diags);
            case BOOL -> parseBool(raw, source, diags);
            case ENUM -> parseEnum(raw, source, diags);
            case STRING -> Optional.of(raw);
        };
    }

    private Optional<Object> parseDouble(String raw, Source source, Diagnostics diags) {
        double v;
        try {
            v = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            diags.error("E_TYPE", "expected a number but got '" + raw + "'", source,
                    "use a decimal like 2.5");
            return Optional.empty();
        }
        if (!Double.isFinite(v)) {
            diags.error("E_TYPE", "number must be finite but got '" + raw + "'", source);
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
            // A decimal where an integer is required is the classic getInt() trap.
            String hint = t.contains(".") ? "use a whole number (no decimal point)" : "use a whole number";
            diags.error("E_TYPE", "expected a whole number but got '" + raw + "'", source, hint);
            return Optional.empty();
        }
        return checkRange((double) v, source, diags) ? Optional.of(v) : Optional.empty();
    }

    private boolean checkRange(double v, Source source, Diagnostics diags) {
        if (min != null && v < min) {
            diags.error("E_RANGE", "value " + trim(v) + " is below the minimum " + trim(min), source);
            return false;
        }
        if (max != null && v > max) {
            diags.error("E_RANGE", "value " + trim(v) + " is above the maximum " + trim(max), source);
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
                diags.error("E_TYPE", "expected true or false but got '" + raw + "'", source);
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
        diags.error("E_ENUM", "'" + raw + "' is not one of " + allowed(), source,
                "allowed values: " + String.join(", ", allowed()));
        return Optional.empty();
    }

    /** Tab-completion candidates for a partial token (enums + booleans). */
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
