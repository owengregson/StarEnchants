package engine.condition;

import java.util.Arrays;
import java.util.function.UnaryOperator;

/**
 * A reusable buffer of activation facts as primitives (docs/architecture.md §3.4): numeric, boolean
 * (a {@code long} bitset), and string slots read by compiled slot index, so the hot path does zero string
 * parsing and zero boxing. Slot indices come from the {@link VarVocabulary} the compiler lowered against,
 * so a condition's {@code slot} and this buffer agree by construction. Not thread-safe — one buffer per
 * worker thread, cleared and repopulated per activation via {@link #clear()}.
 */
public final class FactBuffer {

    /** Max boolean flags: two {@code long} bitsets (v3.1 §A). */
    public static final int MAX_FLAGS = 2 * Long.SIZE;

    private final double[] numbers;
    private final String[] strings;
    private long flags0; // flags 0..63
    private long flags1; // flags 64..127
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
        long bit = 1L << (slot & 63);
        if (slot < Long.SIZE) {
            flags0 = value ? (flags0 | bit) : (flags0 & ~bit);
        } else {
            flags1 = value ? (flags1 | bit) : (flags1 & ~bit);
        }
    }

    public boolean flag(int slot) {
        long bit = 1L << (slot & 63);
        return slot < Long.SIZE ? (flags0 & bit) != 0 : (flags1 & bit) != 0;
    }

    public void setString(int slot, String value) {
        strings[slot] = value;
    }

    public String string(int slot) {
        return strings[slot];
    }

    /** Install the per-activation PlaceholderAPI resolver; {@code null} (the default) means "no PAPI". */
    public void papiResolver(UnaryOperator<String> resolver) {
        this.papi = resolver == null ? t -> null : resolver;
    }

    /** Resolve a PAPI token (the {@code %...%} text without the percents); {@code null} if PAPI absent or unknown. */
    public String resolvePapi(String token) {
        return papi.apply(token);
    }

    /** Reset all slots; called once per activation for thread-local reuse. */
    public void clear() {
        Arrays.fill(numbers, 0.0);
        Arrays.fill(strings, null);
        flags0 = 0L;
        flags1 = 0L;
        papi = t -> null;
    }
}
