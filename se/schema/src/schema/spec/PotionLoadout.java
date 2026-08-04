package schema.spec;

/**
 * The ONE encoding of a {@code POTION_EFFECT} handle-list entry: the interned id and the authored
 * {@code NAME*LEVEL} suffix packed into the single int the list already carries. Every entry is packed
 * (amplifier 0 for a bare name), so a value is never ambiguously raw-or-packed — read one back with
 * {@link #id} / {@link #amp}, never directly.
 */
public final class PotionLoadout {

    /** Highest authorable level; its amplifier ({@code level - 1}) is the widest that fits the low byte. */
    public static final int MAX_LEVEL = 256;

    private static final int AMP_BITS = 8;
    private static final int AMP_MASK = (1 << AMP_BITS) - 1;

    private PotionLoadout() {
    }

    /** Interned ids are dense and non-negative, so the shift never touches the sign bit. */
    public static int pack(int id, int amplifier) {
        return (id << AMP_BITS) | (amplifier & AMP_MASK);
    }

    public static int id(int packed) {
        return packed >>> AMP_BITS;
    }

    public static int amp(int packed) {
        return packed & AMP_MASK;
    }
}
