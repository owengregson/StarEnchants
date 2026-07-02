package compile.model;

/**
 * The set of {@code FactBuffer} slots an ability actually reads (ADR-0039): one bitset per fact space
 * (numbers, flags, strings), since the three spaces have independent slot indices. Derived at compile time
 * from an ability's condition AST and expression-valued effect args ({@link FactMasks}); the runtime
 * populator skips every slot NOT in this mask, so a wearer whose conditions never mention {@code %nearbyenemies%}
 * never pays its 8-block entity scan.
 *
 * <p>Each space is a single {@code long}: the built-in vocabulary is 15 numbers / 17 flags / 9 strings and
 * add-ons contribute effects, not variables (ADR-0038), so 64 slots per space is generous headroom. A slot
 * {@code >= 64} cannot be represented, so {@link FactMasks} degrades that ability's whole mask to {@link #ALL}
 * (populate everything) rather than silently aliasing — correctness is preserved if the vocabulary ever grows.
 */
public record FactMask(long num, long flag, long str) {

    /** Nothing referenced — the populator computes no gated slots. */
    public static final FactMask NONE = new FactMask(0L, 0L, 0L);

    /** Every slot referenced — the populator computes everything (the safe default for hand-built abilities). */
    public static final FactMask ALL = new FactMask(-1L, -1L, -1L);

    public boolean readsNum(int slot) {
        return (num & (1L << slot)) != 0;
    }

    public boolean readsFlag(int slot) {
        return (flag & (1L << slot)) != 0;
    }

    public boolean readsStr(int slot) {
        return (str & (1L << slot)) != 0;
    }

    /** The union of two masks — the per-trigger fold across a wearer's abilities (§5.5). */
    public FactMask union(FactMask other) {
        return new FactMask(num | other.num, flag | other.flag, str | other.str);
    }
}
