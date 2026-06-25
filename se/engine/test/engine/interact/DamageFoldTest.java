package engine.interact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Hand-computed damage-fold corpus (§6.8) proving the additive, order-independent policy
 * (ADR-0012): final = max(0, (base × (1 + Σout%) + ΣflatDmg) × (1 − Σred%) − ΣflatRed).
 */
class DamageFoldTest {

    private static final double EPS = 1e-9;

    @Test
    void baseOnlyIsUnchanged() {
        assertEquals(10.0, new DamageFold().apply(10.0), EPS);
    }

    @Test
    void outgoingPercentBoostsDamage() {
        DamageFold f = new DamageFold();
        f.addOutgoing(0.25);
        assertEquals(12.5, f.apply(10.0), EPS);
    }

    @Test
    void reductionPercentMitigatesDamage() {
        DamageFold f = new DamageFold();
        f.addReduction(0.30);
        assertEquals(7.0, f.apply(10.0), EPS);
    }

    @Test
    void flatDamageAddsToOutputButIsNotInflatedByOutgoingPercent() {
        // (base × (1 + out) + flat) = (10 × 2.0 + 5) = 25 — flat stays +5, not +10.
        DamageFold f = new DamageFold();
        f.addFlatDamage(5.0);
        f.addOutgoing(1.0);
        assertEquals(25.0, f.apply(10.0), EPS);
    }

    @Test
    void flatReductionAbsorbsAfterPercentMitigation() {
        // (10 × 0.5) − 2 = 3
        DamageFold f = new DamageFold();
        f.addReduction(0.5);
        f.addFlatReduction(2.0);
        assertEquals(3.0, f.apply(10.0), EPS);
    }

    @Test
    void allBucketsCombineByTheAdditiveFormula() {
        // (10 × 1.2 + 5) × 0.7 − 1 = (12 + 5) × 0.7 − 1 = 11.9 − 1 = 10.9
        DamageFold f = new DamageFold();
        f.addOutgoing(0.20);
        f.addFlatDamage(5.0);
        f.addReduction(0.30);
        f.addFlatReduction(1.0);
        assertEquals(10.9, f.apply(10.0), EPS);
    }

    @Test
    void sourcesSumWithinEachSideNoCompounding() {
        // Two +50% outgoing sources sum to +100% (×2.0), NOT compound to ×2.25.
        DamageFold f = new DamageFold();
        f.addOutgoing(0.50);
        f.addOutgoing(0.50);
        assertEquals(20.0, f.apply(10.0), EPS);
    }

    @Test
    void foldIsOrderIndependent() {
        DamageFold a = new DamageFold();
        a.addOutgoing(0.1);
        a.addReduction(0.2);
        a.addFlatDamage(3.0);
        a.addFlatReduction(1.0);
        a.addOutgoing(0.4);
        a.addReduction(0.1);

        DamageFold b = new DamageFold();
        b.addReduction(0.1);
        b.addFlatReduction(1.0);
        b.addFlatDamage(3.0);
        b.addOutgoing(0.4);
        b.addReduction(0.2);
        b.addOutgoing(0.1);

        assertEquals(a.apply(8.0), b.apply(8.0), EPS);
    }

    @Test
    void reductionBeyondHundredPercentClampsToZeroNotNegative() {
        DamageFold f = new DamageFold();
        f.addReduction(1.5);
        assertEquals(0.0, f.apply(10.0), EPS);
    }

    @Test
    void outgoingDebuffBeyondMinusHundredPercentClampsToZero() {
        DamageFold f = new DamageFold();
        f.addOutgoing(-2.0);
        assertEquals(0.0, f.apply(10.0), EPS);
    }

    @Test
    void flatReductionBeyondDamageClampsToZero() {
        DamageFold f = new DamageFold();
        f.addFlatReduction(100.0);
        assertEquals(0.0, f.apply(10.0), EPS);
    }

    @Test
    void heroicOutgoingIsAMultiplicativeStageOnTopOfTheFold() {
        // folded = 10 × 1.2 = 12; heroic ×(1 + 0.5) = ×1.5 → 18 (multiplicative, NOT summed into the fold).
        DamageFold f = new DamageFold();
        f.addOutgoing(0.20);
        f.addHeroicOutgoing(0.50);
        assertEquals(18.0, f.apply(10.0), EPS);
    }

    @Test
    void heroicReductionMultipliesAfterTheFold() {
        // folded = 10; heroic ×(1 − 0.25) = ×0.75 → 7.5
        DamageFold f = new DamageFold();
        f.addHeroicReduction(0.25);
        assertEquals(7.5, f.apply(10.0), EPS);
    }

    @Test
    void heroicCompoundsRatherThanSums() {
        // +100% outgoing in the fold = ×2 → 20; heroic +100% = ×2 again → 40 (compounds, the §F exception).
        DamageFold f = new DamageFold();
        f.addOutgoing(1.0);
        f.addHeroicOutgoing(1.0);
        assertEquals(40.0, f.apply(10.0), EPS);
    }

    @Test
    void heroicOutgoingIsBoundedAtQuadruple() {
        // +500% heroic would be ×6, but the stage is clamped to ×4 → 40, not 60.
        DamageFold f = new DamageFold();
        f.addHeroicOutgoing(5.0);
        assertEquals(40.0, f.apply(10.0), EPS);
    }

    @Test
    void heroicReductionBeyondHundredPercentClampsToZero() {
        DamageFold f = new DamageFold();
        f.addHeroicReduction(1.5);
        assertEquals(0.0, f.apply(10.0), EPS);
    }

    @Test
    void maxBonusDamageCapCeilsTheSummedOutgoing() {
        // Two sources sum to +200% (×3 → 30), but the combat cap ceils Σout at +100% (×2 → 20).
        DamageFold f = new DamageFold();
        f.caps(1.0, -1.0); // max-bonus-damage = +100%; reduction uncapped
        f.addOutgoing(1.0);
        f.addOutgoing(1.0);
        assertEquals(20.0, f.apply(10.0), EPS);
    }

    @Test
    void maxBonusReductionCapForbidsImmunityStacking() {
        // Σreduction = 100% would zero the hit, but the cap ceils it at 80% → ×0.2 → 2.0.
        DamageFold f = new DamageFold();
        f.caps(-1.0, 0.8); // damage uncapped; max-bonus-reduction = 80%
        f.addReduction(1.0);
        assertEquals(2.0, f.apply(10.0), EPS);
    }

    @Test
    void negativeCapMeansUncapped() {
        // A negative ceiling = no cap (the default) — the full +200% applies (×3 → 30).
        DamageFold f = new DamageFold();
        f.caps(-1.0, -1.0);
        f.addOutgoing(2.0);
        assertEquals(30.0, f.apply(10.0), EPS);
    }

    @Test
    void resetClearsEveryBucket() {
        DamageFold f = new DamageFold();
        f.addFlatDamage(5.0);
        f.addFlatReduction(2.0);
        f.addOutgoing(0.5);
        f.addReduction(0.5);
        f.addHeroicOutgoing(0.5);
        f.addHeroicReduction(0.5);
        f.reset();
        assertEquals(10.0, f.apply(10.0), EPS);
        assertEquals(0.0, f.flatDamage(), EPS);
        assertEquals(0.0, f.flatReduction(), EPS);
        assertEquals(0.0, f.outgoingPercent(), EPS);
        assertEquals(0.0, f.reductionPercent(), EPS);
        assertEquals(0.0, f.heroicOutgoing(), EPS);
        assertEquals(0.0, f.heroicReduction(), EPS);
    }
}
