package compile.load;

/**
 * One entry of a set's mint roster (§6.6): the enchant is stamped onto a minted piece at a level this entry
 * DESCRIBES rather than fixes. Authored either as a bare level ({@code PROTECTION: 5}) or as a map
 * ({@code { min: 2, max: 5 }}, {@code { nearly-maxed: 4 }}, {@code { chance: 25, min: 1, max: 4 }}).
 *
 * <p>Pure data — {@code se-compile} is Bukkit-free and RNG-free, so the draw itself lives at the mint
 * ({@code feature.apply.SetMint}). {@link #max} doubles as the deterministic level: it is what a fixed entry
 * mints at and what the image-fixture importer uses so generated art never depends on a draw.
 *
 * @param min    lowest level the entry can mint at ({@code >= 1}); equals {@link #max} for a fixed entry
 * @param max    highest level the entry can mint at — the authored level of a fixed entry, and the {@code M}
 *               of a {@link Mode#NEARLY_MAXED} draw
 * @param chance percent probability {@code (0,100]} that the entry mints at all; 100 for an unconditional
 *               one. Fractional (R-QC51): the measured rosters carry half-points, and rounding one to the
 *               nearer integer moves a rung by up to a whole percent of every mint
 * @param mode   how a level is drawn between {@link #min} and {@link #max}
 */
public record EnchantRoll(int min, int max, double chance, Mode mode) {

    /** How a level is drawn. */
    public enum Mode {
        /** Always {@link EnchantRoll#max} — the one-integer form every pack authored before rolls existed. */
        FIXED,
        /** Uniform over {@code [min, max]}. */
        UNIFORM,
        /**
         * The family's nearly-maxed draw, {@code min(M, max(1, M - 2) + rand(3))} with {@code M = max}. NOT a
         * uniform band: the outer {@code min} clamps two of the three rungs together once {@code M < 3}, and
         * reproducing that skew is the point — it is the measured distribution.
         */
        NEARLY_MAXED
    }

    /** The finest chance the draw can express: it is priced in basis points, so 0.01 %. */
    private static final double MIN_CHANCE = 0.01;

    public EnchantRoll {
        min = Math.max(1, min);
        max = Math.max(min, max);
        // (0,100], as before — a non-positive or non-finite chance is raised to the least the draw can express
        // rather than silently zeroing an entry the roster deliberately lists.
        chance = Double.isFinite(chance) ? Math.min(100.0, Math.max(MIN_CHANCE, chance)) : MIN_CHANCE;
    }

    /** A plain authored level — the pre-roll form, and what an unparseable map degrades to. */
    public static EnchantRoll fixed(int level) {
        return new EnchantRoll(level, level, 100.0, Mode.FIXED);
    }

    /** Whether this entry always mints (no chance gate) at one certain level — the pre-roll form's contract. */
    public boolean isCertain() {
        return chance >= 100.0 && mode == Mode.FIXED;
    }
}
