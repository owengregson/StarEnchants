package compile.load;

/**
 * One pet's own exp-per-level curve: the cost of reaching level {@code N} is {@code base + perLevel * N}.
 * Every recorded shape fits it — {@code level * 1000}, flat {@code 500}, {@code 250 + 1000 * level} — so no
 * expression language is needed for a two-number ladder.
 *
 * <p>A def with no curve falls back to the universal flat {@code pets.exp-per-level}, which is why the
 * signature pack's pets are untouched by this existing at all.
 */
public record PetCurve(int base, int perLevel) {

    public PetCurve {
        base = Math.max(0, base);
        perLevel = Math.max(0, perLevel);
    }

    /**
     * The exp needed to climb from {@code level} to {@code level + 1} — the recorded ladder indexes its
     * thresholds by the level being REACHED, so this reads the curve at {@code level + 1}. Never below 1: a
     * zero-cost level would roll forever.
     */
    public int neededFrom(int level) {
        return Math.max(1, base + perLevel * (level + 1));
    }
}
