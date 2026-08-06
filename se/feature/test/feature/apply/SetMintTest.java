package feature.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.EnchantRoll;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The set mint's roster draw (§6.6). Server-free: the draw is arithmetic over an injected {@link Random}, so
 * the whole contract — the distributions, the chance gate, and which entries consume a draw at all — is
 * provable without an ItemStack.
 */
final class SetMintTest {

    /** A Random whose int draws are scripted, so a distribution assertion is a fact and not a sample. */
    private static final class ScriptedRandom extends Random {
        private final int[] draws;
        private int next;
        private int used;

        ScriptedRandom(int... draws) {
            this.draws = draws;
        }

        @Override
        public int nextInt(int bound) {
            used++;
            return draws[next++] % bound;
        }
    }

    @Test
    void aCertainFixedEntryMintsItsLevelAndConsumesNoDraw() {
        ScriptedRandom random = new ScriptedRandom();
        assertEquals(5, SetMint.level(EnchantRoll.fixed(5), random));
        // A pack authoring no rolls must mint exactly what it did before rolls existed — and a live suite over
        // such a pack must stay deterministic, which it only is if nothing draws.
        assertEquals(0, random.used);
    }

    @Test
    void aUniformBandDrawsAcrossItsWholeSpanAndNeverOutsideIt() {
        EnchantRoll band = new EnchantRoll(2, 5, 100, EnchantRoll.Mode.UNIFORM);
        Random random = new Random(20260804L);
        TreeMap<Integer, Integer> seen = new TreeMap<>();
        for (int i = 0; i < 4000; i++) {
            seen.merge(SetMint.level(band, random), 1, Integer::sum);
        }
        assertEquals(List.of(2, 3, 4, 5), List.copyOf(seen.keySet()));
    }

    @Test
    void nearlyMaxedReproducesTheMeasuredFormulaIncludingItsClampedSkew() {
        // min(M, max(1, M - 2) + rand(3)) with M = 4: the three rungs 2, 3, 4, one draw each.
        EnchantRoll four = new EnchantRoll(2, 4, 100, EnchantRoll.Mode.NEARLY_MAXED);
        assertEquals(2, SetMint.level(four, new ScriptedRandom(0)));
        assertEquals(3, SetMint.level(four, new ScriptedRandom(1)));
        assertEquals(4, SetMint.level(four, new ScriptedRandom(2)));
        // M = 2: max(1, 0) = 1, so the draws are 1, 2, 3 CLAMPED to 2 — the top rung takes two thirds of the
        // weight. A uniform band over the same bounds would be the wrong distribution, not a simplification.
        EnchantRoll two = new EnchantRoll(1, 2, 100, EnchantRoll.Mode.NEARLY_MAXED);
        assertEquals(1, SetMint.level(two, new ScriptedRandom(0)));
        assertEquals(2, SetMint.level(two, new ScriptedRandom(1)));
        assertEquals(2, SetMint.level(two, new ScriptedRandom(2)));
        // M = 1: every rung clamps to the only level there is.
        EnchantRoll one = new EnchantRoll(1, 1, 100, EnchantRoll.Mode.NEARLY_MAXED);
        assertEquals(1, SetMint.level(one, new ScriptedRandom(2)));
    }

    @Test
    void theAbilitySetDrawOpensAWiderBandThanNearlyMaxedAndThenTaxesItsTopRung() {
        // R-QC64, codex A.11: `M - rand(min(4, M))`, then a 25 % shave off a natural max above 1. The first
        // draw is the band, the second (only reached when the band landed on M) is the shave gate.
        EnchantRoll five = new EnchantRoll(2, 5, 100, EnchantRoll.Mode.ABILITY_SET);
        assertEquals(2, SetMint.level(five, new ScriptedRandom(3)), "M-3 is a rung NEARLY_MAXED cannot reach");
        assertEquals(3, SetMint.level(five, new ScriptedRandom(2)));
        assertEquals(4, SetMint.level(five, new ScriptedRandom(1)));
        // The shave gate is Rolls.passes(random, 25) — nextInt(100) < 25 — so 24 shaves and 25 does not.
        assertEquals(4, SetMint.level(five, new ScriptedRandom(0, 24)), "a natural max is shaved a quarter of the time");
        assertEquals(5, SetMint.level(five, new ScriptedRandom(0, 25)));

        // M < 4 narrows the band with it: M = 2 draws 1 or 2, and 1 cannot be shaved below itself.
        EnchantRoll two = new EnchantRoll(1, 2, 100, EnchantRoll.Mode.ABILITY_SET);
        assertEquals(1, SetMint.level(two, new ScriptedRandom(1)));
        assertEquals(1, SetMint.level(two, new ScriptedRandom(0, 24)));
        // M = 1: one rung, and the shave's `> 1` guard is what stops it emptying the entry.
        EnchantRoll one = new EnchantRoll(1, 1, 100, EnchantRoll.Mode.ABILITY_SET);
        assertEquals(1, SetMint.level(one, new ScriptedRandom(0, 0)));
    }

    @Test
    void theTwoBandFromMDrawsAreGenuinelyDifferentDistributions() {
        // The reason D-12-37's "one draw is enough" reading was reversed: at the M these rosters use, only the
        // ability-set draw can produce M-3, and only it can shave a natural max down.
        EnchantRoll nearly = new EnchantRoll(2, 4, 100, EnchantRoll.Mode.NEARLY_MAXED);
        EnchantRoll ability = new EnchantRoll(1, 4, 100, EnchantRoll.Mode.ABILITY_SET);
        Random random = new Random(20260805L);
        TreeMap<Integer, Integer> nearlySeen = new TreeMap<>();
        TreeMap<Integer, Integer> abilitySeen = new TreeMap<>();
        for (int i = 0; i < 8000; i++) {
            nearlySeen.merge(SetMint.level(nearly, random), 1, Integer::sum);
            abilitySeen.merge(SetMint.level(ability, random), 1, Integer::sum);
        }
        assertEquals(List.of(2, 3, 4), List.copyOf(nearlySeen.keySet()));
        assertEquals(List.of(1, 2, 3, 4), List.copyOf(abilitySeen.keySet()));
        // The shave moves weight off the top: the ability-set draw lands on M less often than its 1-in-4 band
        // alone would, where NEARLY_MAXED's 1-in-3 top rung is untaxed.
        assertTrue(abilitySeen.get(4) < nearlySeen.get(4), "the top rung is taxed on one draw and not the other");
    }

    @Test
    void aFailedChanceGateYieldsNoLevelAndDropsTheEntryRatherThanMintingItAtZero() {
        EnchantRoll gated = new EnchantRoll(1, 4, 25, EnchantRoll.Mode.UNIFORM);
        // The fractional gate draws BASIS points: nextInt(10_000) < chance x 100, so 2499 passes a 25% gate
        // and 2500 does not.
        assertTrue(SetMint.level(gated, new ScriptedRandom(2499, 0)) > 0);
        assertEquals(0, SetMint.level(gated, new ScriptedRandom(2500)));

        Map<String, EnchantRoll> roster = new LinkedHashMap<>();
        roster.put("PROTECTION", EnchantRoll.fixed(4));
        roster.put("enchants/immortal", gated);
        roster.put("UNBREAKING", EnchantRoll.fixed(3));
        Map<String, Integer> minted = SetMint.resolve(roster, new ScriptedRandom(2500));

        assertFalse(minted.containsKey("enchants/immortal"), "a gate that fails leaves the entry off the piece");
        // authored order survives the draw — it is the piece's enchant-lore order
        assertEquals(List.of("PROTECTION", "UNBREAKING"), List.copyOf(minted.keySet()));
        assertEquals(4, minted.get("PROTECTION"));
    }

    @Test
    void aHalfPointChanceRollsAtItsHalfPointAndNotAtAnAdjacentInteger() {
        // R-QC51: the measured rosters carry half-points. Rounding 17.5 either way moves the gate by a whole
        // basis-point block — 1750 must be the boundary, not 1700 or 1800.
        EnchantRoll half = new EnchantRoll(1, 4, 17.5, EnchantRoll.Mode.UNIFORM);
        assertTrue(SetMint.level(half, new ScriptedRandom(1749, 0)) > 0);
        assertEquals(0, SetMint.level(half, new ScriptedRandom(1750)));
    }
}
