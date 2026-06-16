package engine.condition;

import java.util.Arrays;
import java.util.function.UnaryOperator;

/**
 * A thread-local, reusable buffer of activation facts as <em>primitives</em>
 * (docs/architecture.md §3.4) — the safe form of pooling: a flat struct of numeric,
 * boolean (a {@code long} bitset), and string slots, read by both conditions and
 * effect arguments by compiled slot index. Populated lazily, once per activation, so
 * the hot path does zero string parsing and zero boxing.
 *
 * <p>Slot indices are assigned by the {@link VarVocabulary} the compiler lowered
 * against, so a compiled condition's {@code slot} and this buffer agree by
 * construction. Not thread-safe by design — one buffer per worker thread, cleared and
 * repopulated per activation via {@link #clear()}.
 */
public final class FactBuffer {

    /** The maximum number of boolean flags (one {@code long} bitset). */
    public static final int MAX_FLAGS = Long.SIZE;

    private final double[] numbers;
    private final String[] strings;
    private long flags;
    private UnaryOperator<String> papi = t -> null;

    public FactBuffer(int numberSlots, int flagSlots, int stringSlots) {
        if (numberSlots < 0 || flagSlots < 0 || stringSlots < 0) {
            throw new IllegalArgumentException("slot counts must be non-negative");
        }
        if (flagSlots > MAX_FLAGS) {
            throw new IllegalArgumentException("at most " + MAX_FLAGS + " flag slots, got " + flagSlots);
        }
        this.numbers = new double[numberSlots];
        this.strings = new String[stringSlots];
    }

    public void setNumber(int slot, double value) {
        numbers[slot] = value;
    }

    public double number(int slot) {
        return numbers[slot];
    }

    public void setFlag(int slot, boolean value) {
        long bit = 1L << slot;
        flags = value ? (flags | bit) : (flags & ~bit);
    }

    public boolean flag(int slot) {
        return (flags & (1L << slot)) != 0;
    }

    public void setString(int slot, String value) {
        strings[slot] = value;
    }

    public String string(int slot) {
        return strings[slot];
    }

    /**
     * Install the PlaceholderAPI resolver for this activation (typically bound to the
     * acting player). A {@code null}-returning resolver (the default) means "no PAPI".
     */
    public void papiResolver(UnaryOperator<String> resolver) {
        this.papi = resolver == null ? t -> null : resolver;
    }

    /**
     * Resolve a PlaceholderAPI token (the {@code %...%} text without the percents), or
     * {@code null} if PlaceholderAPI is absent or the placeholder is unknown.
     */
    public String resolvePapi(String token) {
        return papi.apply(token);
    }

    /** Reset every slot for reuse on the next activation (thread-local pooling). */
    public void clear() {
        Arrays.fill(numbers, 0.0);
        Arrays.fill(strings, null);
        flags = 0L;
        papi = t -> null;
    }
}
