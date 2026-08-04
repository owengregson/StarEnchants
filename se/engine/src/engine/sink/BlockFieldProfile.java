package engine.sink;

import java.util.Random;

/**
 * The authored shape of a falling-block RAIN field (the {@code FALLING_BLOCK} block-field profile): a
 * (2·radius+1)² grid repeated over several randomly-stepped layers above the target, each position raining at
 * {@code density} percent, plus the two counterplay knobs the LANDING path reads — the per-victim re-hit
 * ceiling and the material that kills a block mid-flight. An immutable carrier, so a deferred intent can never
 * alias a mutable argument (§3.6).
 *
 * <p>Draws are fresh {@link Random} draws, deliberately NOT {@link ScatterFill}'s coordinate-stable hash. That
 * hash exists so a re-stamped static shape EXTENDS the same field instead of filling in its holes; a rain field
 * re-cast on a stationary victim would then fall as the same stencil, in the same holes, every single proc.
 * Rain is not a stencil.
 *
 * <p>The defaults ({@code layers 1..1}, {@code step 0..0}, {@code density 100}) consume no draw at all and
 * yield exactly the single-layer, fully-populated grid the pre-profile {@code FALLING_BLOCK} rained.
 */
public record BlockFieldProfile(int radius, int height, int layersMin, int layersMax, int stepMin, int stepMax,
                                double density, int rehitMax, int rehitWindowTicks, int killMaterialId) {

    /**
     * The Y offsets of this field's layers above the target's feet, with an INDEPENDENT step draw per layer:
     * layer {@code i} sits at {@code height + step_i × i}, so layer 0 is always the bare {@code height} and each
     * higher layer is spread by its own draw (the measured {@code (12..19) × layerIndex} above a +10 origin).
     */
    public int[] layerYOffsets(Random rnd) {
        int[] offsets = new int[between(layersMin, layersMax, rnd)];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = height + between(stepMin, stepMax, rnd) * i;
        }
        return offsets;
    }

    /** Whether ONE grid position of ONE layer actually rains a block. */
    public boolean spawns(Random rnd) {
        return density >= 100 || (density > 0 && rnd.nextDouble() * 100.0 < density);
    }

    /** A uniform draw over the inclusive {@code [min, max]}; a degenerate or reversed authored pair draws nothing. */
    private static int between(int min, int max, Random rnd) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo >= hi ? lo : lo + rnd.nextInt(hi - lo + 1);
    }
}
