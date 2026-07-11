package engine.interact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Hand-computed damage-fold corpus (§6.8) proving the additive, order-independent policy
 * (ADR-0012, restored to full scope by ADR-0037): final =
 * max(0, (base × (1 + Σout%) + ΣflatDmg) × (1 − Σred%) − ΣflatRed). Heroic percents feed the
 * same buckets as any enchant contribution — no separate multiplicative stage.
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
    void heroicPercentsSumIntoTheOutgoingBucketWithEnchants() {
        // ADR-0037: two heroic weapon pieces (+50% each) + one enchant DAMAGE_MOD (+25%) all land in the ONE
        // outgoing bucket → +125% → ×2.25, NOT a separate multiplicative stage. base 10 × 2.25 = 22.5.
        DamageFold f = new DamageFold();
        f.addOutgoing(0.50); // heroic piece
        f.addOutgoing(0.50); // heroic piece
        f.addOutgoing(0.25); // enchant DAMAGE_MOD
        assertEquals(22.5, f.apply(10.0), EPS);
    }

    @Test
    void heroicReductionSumsWithEnchantReduction() {
        // Heroic armour (−20%) + an enchant reduction (−30%) sum to −50% in the parallel bucket → ×0.5 → 5.0.
        DamageFold f = new DamageFold();
        f.addReduction(0.20); // heroic piece
        f.addReduction(0.30); // enchant reduction
        assertEquals(5.0, f.apply(10.0), EPS);
    }

    @Test
    void flatsStillApplyInAdrOrderWhenHeroicPercentsAreFolded() {
        // Heroic folded into the additive buckets, flats keep their ADR-0012 placement:
        // (10 × (1 + 0.75) + 5) × (1 − 0.40) − 1 = (17.5 + 5) × 0.6 − 1 = 13.5 − 1 = 12.5.
        DamageFold f = new DamageFold();
        f.addOutgoing(0.50); // heroic weapon
        f.addOutgoing(0.25); // enchant DAMAGE_MOD
        f.addFlatDamage(5.0); // heroic diamond base-attack delta
        f.addReduction(0.40); // enchant reduction
        f.addFlatReduction(1.0);
        assertEquals(12.5, f.apply(10.0), EPS);
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
    void attackScaleMultipliesOnlyTheCustomAttackSide() {
        // ADR-0050 R2: scale ×5 on Σout (+50% → +250%) and flat (+2 → +10); the base 10 and the
        // 20% reduction are untouched: (10 × 3.5 + 10) × 0.8 = 36.
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.addOutgoing(0.50);
        f.addFlatDamage(2.0);
        f.addReduction(0.20);
        assertEquals(36.0, f.apply(10.0), EPS);
    }

    @Test
    void attackScaleAppliesAfterTheOutgoingCap() {
        // Σout +200% ceils at the +100% cap FIRST, then scales ×5 → ×6 → 60 (a scaled economy keeps
        // its normalized cap semantics).
        DamageFold f = new DamageFold();
        f.caps(1.0, -1.0);
        f.attackScale(5.0);
        f.addOutgoing(2.0);
        assertEquals(60.0, f.apply(10.0), EPS);
    }

    @Test
    void attackScaleLeavesVanillaAndDefenseAlone() {
        // No custom contributions → a scaled fold is still the identity on the base hit; a
        // non-positive scale falls back to neutral.
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        assertEquals(10.0, f.apply(10.0), EPS);
        f.attackScale(0.0);
        f.addOutgoing(1.0);
        assertEquals(20.0, f.apply(10.0), EPS);
    }

    @Test
    void resetRestoresTheNeutralScale() {
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.reset();
        f.addOutgoing(1.0);
        assertEquals(20.0, f.apply(10.0), EPS);
    }

    @Test
    void resetClearsEveryBucket() {
        DamageFold f = new DamageFold();
        f.addFlatDamage(5.0);
        f.addFlatReduction(2.0);
        f.addOutgoing(0.5);
        f.addReduction(0.5);
        f.reset();
        assertEquals(10.0, f.apply(10.0), EPS);
        assertEquals(0.0, f.flatDamage(), EPS);
        assertEquals(0.0, f.flatReduction(), EPS);
        assertEquals(0.0, f.outgoingPercent(), EPS);
        assertEquals(0.0, f.reductionPercent(), EPS);
    }

    // ── ADR-0053 heroic-tagged reduction buckets (IGNORE_HEROIC) ──────────────────────────────────

    @Test
    void heroicAddersFoldIdenticallyToPlainAddersWhenUnflagged() {
        // Parity contract: routing the victim's heroic through the tagged buckets must not change the
        // commit — same corpus as flatsStillApplyInAdrOrderWhenHeroicPercentsAreFolded, heroic re-routed.
        DamageFold plain = new DamageFold();
        plain.addOutgoing(0.50);
        plain.addFlatDamage(5.0);
        plain.addReduction(0.40); // heroic armour, on the plain adder (the pre-change routing)
        plain.addFlatReduction(1.0); // heroic flat, on the plain adder

        DamageFold tagged = new DamageFold();
        tagged.addOutgoing(0.50);
        tagged.addFlatDamage(5.0);
        tagged.addHeroicReduction(0.40);
        tagged.addHeroicFlatReduction(1.0);

        assertEquals(plain.apply(10.0), tagged.apply(10.0), EPS);
    }

    @Test
    void heroicAndPlainReductionsSumWhenUnflagged() {
        // (10) × (1 − (0.20 + 0.30)) − (1 + 2) = 5 − 3 = 2
        DamageFold f = new DamageFold();
        f.addHeroicReduction(0.20);
        f.addReduction(0.30);
        f.addHeroicFlatReduction(1.0);
        f.addFlatReduction(2.0);
        assertEquals(2.0, f.apply(10.0), EPS);
    }

    @Test
    void ignoreHeroicDropsExactlyTheHeroicBuckets() {
        // Flagged: only the plain −30% and flat −2 survive: 10 × 0.7 − 2 = 5; the heroic −20%/−1 vanish.
        DamageFold f = new DamageFold();
        f.addHeroicReduction(0.20);
        f.addReduction(0.30);
        f.addHeroicFlatReduction(1.0);
        f.addFlatReduction(2.0);
        f.ignoreHeroic();
        assertEquals(5.0, f.apply(10.0), EPS);
    }

    @Test
    void ignoreHeroicWithOnlyHeroicReductionRestoresTheBareHit() {
        DamageFold f = new DamageFold();
        f.addHeroicReduction(0.40);
        f.addHeroicFlatReduction(3.0);
        f.ignoreHeroic();
        assertEquals(10.0, f.apply(10.0), EPS);
    }

    @Test
    void ignoreHeroicNeverTouchesAttackSideBuckets() {
        // Heroic WEAPON damage rides the plain attack adders; the flag must not perturb them.
        DamageFold f = new DamageFold();
        f.addOutgoing(0.50); // heroic weapon percent (plain adder by design)
        f.addFlatDamage(5.0); // heroic diamond base-attack delta (plain adder by design)
        f.ignoreHeroic();
        assertEquals(20.0, f.apply(10.0), EPS);
    }

    @Test
    void maxBonusReductionCapSeesTheCombinedHeroicPlusPlainSum() {
        // heroic 0.60 + plain 0.60 = 1.20 ceils at the 0.8 cap → ×0.2 → 2.0 (identical to the one-bucket world).
        DamageFold f = new DamageFold();
        f.caps(-1.0, 0.8);
        f.addHeroicReduction(0.60);
        f.addReduction(0.60);
        assertEquals(2.0, f.apply(10.0), EPS);

        // Flagged, only the plain 0.60 remains — under the cap now: ×0.4 → 4.0.
        f.ignoreHeroic();
        assertEquals(4.0, f.apply(10.0), EPS);
    }

    @Test
    void resetClearsTheHeroicBucketsAndTheFlag() {
        DamageFold f = new DamageFold();
        f.addHeroicReduction(0.50);
        f.addHeroicFlatReduction(2.0);
        f.ignoreHeroic();
        f.reset();
        assertEquals(0.0, f.heroicReductionPercent(), EPS);
        assertEquals(0.0, f.heroicFlatReduction(), EPS);
        assertEquals(false, f.heroicIgnored());
        f.addHeroicReduction(0.30);
        assertEquals(7.0, f.apply(10.0), EPS); // heroic folds again after reset (the flag did not leak)
    }
}
