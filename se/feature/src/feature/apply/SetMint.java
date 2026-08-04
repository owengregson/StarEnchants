package feature.apply;

import compile.load.EnchantRoll;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Resolves a set's authored mint roster into the concrete {@code ref → level} map one piece is stamped with
 * (§6.6). The DRAW lives here rather than on {@link EnchantRoll} because {@code se-compile} is RNG-free and
 * because every apply/mint economy in this package draws through the one injected {@link Rolls} vocabulary —
 * an inline {@code ThreadLocalRandom} is unstubbable, which is the bug the legacy smoke once caught.
 */
public final class SetMint {

    private SetMint() {
    }

    /**
     * One draw per roster entry, in authored order (which is the piece's enchant-lore order). An entry whose
     * chance gate fails is ABSENT from the result rather than present at level 0 — a piece that did not roll
     * Immortal simply does not carry it.
     */
    public static Map<String, Integer> resolve(Map<String, EnchantRoll> roster, Random random) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, EnchantRoll> entry : roster.entrySet()) {
            int level = level(entry.getValue(), random);
            if (level > 0) {
                out.put(entry.getKey(), level);
            }
        }
        return out;
    }

    /**
     * The level one entry mints at, or {@code 0} when its chance gate fails. A certain fixed entry consumes
     * NO draw, so a pack with no rolls authored anywhere mints exactly what it did before rolls existed —
     * and its live suites stay deterministic.
     */
    public static int level(EnchantRoll roll, Random random) {
        if (roll.isCertain()) {
            return roll.max();
        }
        if (roll.chance() < 100 && !Rolls.passes(random, roll.chance())) {
            return 0;
        }
        return switch (roll.mode()) {
            case FIXED -> roll.max();
            case UNIFORM -> Rolls.between(random, roll.min(), roll.max());
            // The measured family draw, reproduced literally: the outer min() is what skews the top rung once
            // M < 3, so a uniform band over the same bounds would be the wrong distribution, not a shortcut.
            case NEARLY_MAXED -> Math.min(roll.max(), Math.max(1, roll.max() - 2) + random.nextInt(3));
        };
    }
}
