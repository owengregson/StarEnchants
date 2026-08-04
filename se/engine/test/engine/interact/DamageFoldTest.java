package engine.interact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Hand-computed damage-fold corpus (§6.8) proving the additive, order-independent policy
 * (ADR-0012, restored to full scope by ADR-0037): final =
 * max(0, max(0, (base × (1 + Σout%×scale) + ΣflatDmg×scale) × (1 − Σred%) − ΣflatRed) + Σeff).
 * Heroic percents feed the same buckets as any enchant contribution — no separate multiplicative
 * stage; Σeff is the ADR-0055 same-hit rider bucket (effective units: unscaled, unmitigated).
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
    void anUnscaledOutgoingTermIsExactlyTheAuthoredMultiplier() {
        // The contract DOT_AMPLIFY_MARK stands on: the environmental damage path sets no attack-scale, so
        // addOutgoing(factor − 1) prices a wither tick at exactly base × factor. If that path ever gained an
        // attack-scale, a ×3 mark would silently become ×(1 + 2·scale).
        DamageFold f = new DamageFold();
        f.addOutgoing(3.0 - 1.0);
        assertEquals(30.0, f.apply(10.0), EPS);
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

    // ── ADR-0055 effective rider bucket: authored = delivered, unscaled, unmitigated ─────────────

    @Test
    void aRiderContributesExactlyItsAuthoredAmountUnderAttackScale() {
        // THE restoration pin (ADR-0055): under the signature pack's attack-scale 5.0, a rider authored 6
        // moves the fold result by exactly 6 pre-armor — the pre-1.8.2 bare hurt's delivery — where the
        // 1.8.2 flat-bucket routing landed it as 6 × 5 = 30 (the ~5x regression this bucket removes).
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.addEffectiveDamage(6.0);
        assertEquals(16.0, f.apply(10.0), EPS);
    }

    @Test
    void theScaledFlatBucketKeepsItsSemanticsBesideARider() {
        // The pre-1.8.2 flat economy (DAMAGE_MOD mode:flat, heroic delta — ADR-0050 R3/R4) still rides
        // the scale; the rider still does not: (10 + 2×5) + 6 = 26, never (10 + (2+6)×5) = 50.
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.addFlatDamage(2.0);
        f.addEffectiveDamage(6.0);
        assertEquals(26.0, f.apply(10.0), EPS);
    }

    @Test
    void percentEconomyIsUnchangedByARider() {
        // Adding a rider must not perturb the scaled percent economy: (10 × (1 + 0.5×5)) + 6 = 41.
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.addOutgoing(0.50);
        f.addEffectiveDamage(6.0);
        assertEquals(41.0, f.apply(10.0), EPS);
    }

    @Test
    void ridersAreNotPricedByTheDefenseTerms() {
        // Pre-1.8.2 the rider was a separate event the defense walk never saw — a 50% reduction halves
        // the melee but never the rider: 10 × 0.5 + 6 = 11, not (10 + 6) × 0.5 = 8.
        DamageFold f = new DamageFold();
        f.addReduction(0.50);
        f.addEffectiveDamage(6.0);
        assertEquals(11.0, f.apply(10.0), EPS);
    }

    @Test
    void flatReductionCannotAbsorbARider() {
        // Flat reduction zeroes the whole scaled economy, but the rider bucket joins after the inner
        // clamp: max(0, 10 − 100) + 6 = 6 — a stacked flat-red wall never learns to eat riders.
        DamageFold f = new DamageFold();
        f.addFlatReduction(100.0);
        f.addEffectiveDamage(6.0);
        assertEquals(6.0, f.apply(10.0), EPS);
    }

    @Test
    void ridersSumAcrossSourcesLikeSeparateHurtsDid() {
        // Two same-hit riders (two procs on one swing) each deliver their authored amount: 10 + 4 + 6 = 20.
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.addEffectiveDamage(4.0);
        f.addEffectiveDamage(6.0);
        assertEquals(20.0, f.apply(10.0), EPS);
    }

    @Test
    void resetClearsTheRiderBucket() {
        DamageFold f = new DamageFold();
        f.addEffectiveDamage(6.0);
        f.reset();
        assertEquals(0.0, f.effectiveDamage(), EPS);
        assertEquals(10.0, f.apply(10.0), EPS);
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

    // ── ADR-0071 self-malus channel (mulFinal): prices the WHOLE committed hit, scale-immune ──────

    @Test
    void mulFinalPricesWholeHitAtScaleFive() {
        // The pack's flat-forward shape at scale 5.0: (7 × (1 + 0.10×5) + 0.1×5) + 1.5 = 11.0 + 1.5
        // = 12.5, then the −20% self-malus prices the whole hit: 12.5 × 0.8 = 10.0 (felt −20%).
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.addOutgoing(0.10);
        f.addFlatDamage(0.1);
        f.addEffectiveDamage(1.5);
        f.mulFinal(0.8);
        assertEquals(10.0, f.apply(7.0), EPS);
    }

    @Test
    void mulFinalPricesWholeHitAtScaleOne() {
        // Same buckets at scale 1.0: (7 × 1.1 + 0.1) + 1.5 = 9.3, then ×0.8 = 7.44 — felt −20% at
        // BOTH scales (the point of a post-fold multiplicative factor).
        DamageFold f = new DamageFold();
        f.attackScale(1.0);
        f.addOutgoing(0.10);
        f.addFlatDamage(0.1);
        f.addEffectiveDamage(1.5);
        f.mulFinal(0.8);
        assertEquals(7.44, f.apply(7.0), EPS);
    }

    @Test
    void negativeOutgoingIsScaleTrapped() {
        // Pins the TRAP mulFinal exists for (ADR-0071): authoring the −20% as addOutgoing(−0.2) at
        // scale 5.0 folds Σout to +0.10−0.20 = −0.10 → (7 × (1 + (−0.10)×5) + 0.1×5) = 3.5 + 0.5 =
        // 4.0, NOT the intended −20% of the 11.0 economy (8.8). The scale multiplies the malus into
        // a −63.6% overshoot — which is why the malus rides mulFinal, never the outgoing bucket.
        DamageFold f = new DamageFold();
        f.attackScale(5.0);
        f.addOutgoing(0.10);
        f.addFlatDamage(0.1);
        f.addOutgoing(-0.20);
        assertEquals(4.0, f.apply(7.0), EPS);
    }

    @Test
    void mulFinalStacksMultiplicatively() {
        // Two self-maluses both apply, order-free: 9.0 × 0.8 × (1/3) = 2.4.
        DamageFold f = new DamageFold();
        f.mulFinal(0.8);
        f.mulFinal(1.0 / 3.0);
        assertEquals(2.4, f.apply(9.0), EPS);
    }

    @Test
    void mulFinalClampsToUnitInterval() {
        // A factor > 1 is clamped to 1.0 — no multiplicative BUFF can be smuggled through (ADR-0012).
        DamageFold buff = new DamageFold();
        buff.mulFinal(1.5);
        assertEquals(10.0, buff.apply(10.0), EPS);
        // A negative factor clamps to 0.0 — zeroes the hit, never heals.
        DamageFold neg = new DamageFold();
        neg.mulFinal(-0.5);
        assertEquals(0.0, neg.apply(10.0), EPS);
    }

    @Test
    void mulFinalAppliesToRiderOnlyHits() {
        // The factor prices the rider bucket too (it applies to the whole result): 0 base + 2.0
        // rider = 2.0, ×0.5 = 1.0.
        DamageFold f = new DamageFold();
        f.addEffectiveDamage(2.0);
        f.mulFinal(0.5);
        assertEquals(1.0, f.apply(0.0), EPS);
    }

    @Test
    void resetRestoresFinalFactor() {
        DamageFold f = new DamageFold();
        f.mulFinal(0.5);
        f.reset();
        assertEquals(1.0, f.finalFactor(), EPS);
        assertEquals(4.0, f.apply(4.0), EPS);
    }

    // ── contribution(): the rebound-direction read (PROC_REBOUND) ──

    @Test
    void contributionIsTheMarginalDamageOverTheBase() {
        DamageFold f = new DamageFold();
        f.addOutgoing(0.50);
        assertEquals(5.0, f.contribution(10.0), EPS); // 15 folded − 10 base
    }

    @Test
    void contributionOfAnEmptyFoldIsZero() {
        // The commit site reads this on every hit that claimed anything; a re-execution whose effects were
        // all intents (POTION, SPAWN) must return nothing rather than a phantom rebound.
        assertEquals(0.0, new DamageFold().contribution(10.0), EPS);
    }

    @Test
    void contributionClampsANetNegativeFoldToZero() {
        DamageFold f = new DamageFold();
        f.addReduction(0.40);
        assertEquals(0.0, f.contribution(10.0), EPS, "a rebounded reduction must not heal its target");
    }

    @Test
    void contributionIsPricedByAttackScaleExactlyAsTheIncomingFoldIs() {
        // The whole point of folding the rebound rather than committing the raw percent: at attack-scale 5
        // a +50% enchant is worth +250% of the base on the incoming hit, and must be worth the same rebounded.
        DamageFold incoming = new DamageFold();
        incoming.attackScale(5.0);
        incoming.addOutgoing(0.50);
        DamageFold rebound = new DamageFold();
        rebound.adoptLimits(incoming);
        rebound.addOutgoing(0.50);

        assertEquals(incoming.apply(10.0) - 10.0, rebound.contribution(10.0), EPS);
        assertEquals(25.0, rebound.contribution(10.0), EPS); // 10 × 0.5 × 5
    }

    @Test
    void adoptLimitsCarriesTheCapsButNoContributionOrSelfMalus() {
        DamageFold incoming = new DamageFold();
        incoming.caps(0.20, -1.0);
        incoming.attackScale(2.0);
        incoming.addOutgoing(0.50);   // must NOT travel
        incoming.mulFinal(0.5);       // the attacker's own self-malus must NOT price the rebound
        DamageFold rebound = new DamageFold();
        rebound.adoptLimits(incoming);
        rebound.addOutgoing(0.50);

        assertEquals(1.0, rebound.finalFactor(), EPS);
        assertEquals(4.0, rebound.contribution(10.0), EPS); // 10 × min(0.5, 0.20) × 2
    }
}
