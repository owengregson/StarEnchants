package engine.sink;

/**
 * The {@code TEMP_BLOCK fill-chance} rule: which columns of a shape are actually placed. Deliberately a pure
 * integer mix of the coordinate rather than a {@code Random} draw — the same reason the palette scatter is —
 * so a layered re-stamp of the same ground EXTENDS the same field instead of gradually filling in its holes,
 * and a revert fingerprint stays stable across placements. Lives in {@code engine.sink} so the {@code TEMP_BLOCK}
 * kind and the sink's box fill share one rule rather than two that drift.
 */
public final class ScatterFill {

    private ScatterFill() {
    }

    /** A cheap deterministic spatial mix of a block coordinate (the classic 73856093/19349663 hash, finalized). */
    public static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        return h;
    }

    /**
     * Whether the column at {@code (x, z)} is placed, given a {@code chancePercent} in {@code [0, 100]}. Reads a
     * DIFFERENT slice of the mix than the palette does, so a block's material and its presence do not correlate
     * into visible banding.
     */
    public static boolean fills(int x, int z, double chancePercent) {
        if (chancePercent >= 100) {
            return true; // the default: a solid shape, byte-identical to the pre-fill-chance placement
        }
        return chancePercent > 0 && Math.floorMod(hash(x, z) >>> 8, 100) < chancePercent;
    }
}
