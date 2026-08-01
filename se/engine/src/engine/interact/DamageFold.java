package engine.interact;

/**
 * The damage arbiter: a per-event accumulator that folds every source's contribution into
 * one final number, <em>once</em> (docs/architecture.md §6.1, ADR-0012). Damage effects
 * never call {@code event.setDamage}; they contribute deltas here and the pipeline calls
 * {@link #apply} once after the gate walk.
 *
 * <p><strong>Fully additive, no cross-source compounding</strong> (ADR-0012, restored to full
 * scope by ADR-0037): all outgoing percentages sum into one factor and all reductions into a
 * parallel factor, so the result is order-independent and the registration-order multiplicative
 * compounding (the catalog's worst combat bug) cannot occur. Heroic percents feed the same
 * additive buckets as any enchant contribution — there is no separate multiplicative stage.
 *
 * <pre>final = max(0, max(0, (base × (1 + Σ outgoing% × scale) + Σ flatDamage × scale)
 *                          × (1 − Σ reduction%) − Σ flatReduction) + Σ effectiveDamage)</pre>
 *
 * <p>Flat-term placement gives predictable, percent-independent stats: flat damage adds
 * after the outgoing multiplier (so the attacker's own buffs don't inflate it) but still
 * faces the defender's reduction; flat reduction subtracts last (so it absorbs exactly its
 * advertised amount).
 *
 * <p><strong>Effective damage is the same-hit rider bucket</strong> (ADR-0055): authored = delivered,
 * pre-armor. It joins after the whole scaled-and-mitigated economy — never multiplied by
 * {@code attack-scale} and never priced by the defense terms — because the pre-1.8.2 bare hurt it
 * restores was a separate event that neither the attack economy nor the defense walk ever saw.
 *
 * <p>Percentages are fractions ({@code 0.25} = +25%). Each factor and the final result are
 * clamped at zero, so an over-100% reduction or sub-−100% debuff contributes nothing rather
 * than healing.
 *
 * <p>Per-event scratch owned by the single firing thread (§6); not thread-safe. Reuse via
 * {@link #reset}.
 */
public final class DamageFold {

    private double flatDamage;
    private double effectiveDamage;
    private double flatReduction;
    private double outgoingPercent;
    private double reductionPercent;
    private double outgoingMultiplier = 1.0;
    private double incomingMultiplier = 1.0;
    // Victim heroic reduction rides its own pair of buckets (ADR-0053 IGNORE_HEROIC): the commit adds them
    // back into the shared reduction terms unless the per-hit ignore flag is set, so unflagged behavior is
    // identical to when heroic fed the plain buckets directly. Attacker-side heroic stays on the plain adders.
    private double heroicReductionPercent;
    private double heroicFlatReduction;
    private boolean heroicIgnored;
    // Combat caps (config.yml combat.*): ceilings on the summed additive fractions; +inf = uncapped.
    private double maxBonusOutgoing = Double.POSITIVE_INFINITY;
    private double maxBonusReduction = Double.POSITIVE_INFINITY;
    // Attack-side throughput compensation (config.yml combat.attack-scale): content stays on a
    // normalized human scale; ONE multiplier adapts it to the server's armor pipeline (a 1.8-era
    // flat armor+Prot stack passes only ~5% of a hit, so the cosmic pack ships 5.0 there).
    private double attackScale = 1.0;
    // ADR-0071 self-malus channel: a reforge downside prices the attacker's OWN committed hit.
    // Multiplicative and applied to the WHOLE result (base + scaled economy + riders) by design:
    // it is the only shape that survives combat.attack-scale AND the rider bucket — an additive
    // outgoing term is multiplied by the scale (−0.2 × 5 = −100%, the scale trap) and can never
    // reach the base or the riders. Clamped to [0,1] so content cannot smuggle a multiplicative
    // BUFF through it (ADR-0012 stays intact: buffs remain additive-only).
    private double finalFactor = 1.0;

    /**
     * Additive combat caps for this event (config.yml {@code combat.max-bonus-damage} /
     * {@code combat.max-bonus-reduction}): ceilings on the summed outgoing% and reduction%
     * applied in {@link #apply}. A negative ceiling means "no cap" (the default).
     */
    public void caps(double maxBonusOutgoing, double maxBonusReduction) {
        this.maxBonusOutgoing = maxBonusOutgoing < 0 ? Double.POSITIVE_INFINITY : maxBonusOutgoing;
        this.maxBonusReduction = maxBonusReduction < 0 ? Double.POSITIVE_INFINITY : maxBonusReduction;
    }

    /**
     * Attack-side scale for this event (config.yml {@code combat.attack-scale}): multiplies the
     * CAPPED summed outgoing% and the flat-damage bucket in {@link #apply} — the custom attack
     * economy only, never the base hit and never the defense side, so vanilla-vs-vanilla combat
     * and every reduction stay untouched. Non-positive values fall back to the neutral 1.0.
     */
    public void attackScale(double scale) {
        this.attackScale = scale <= 0 ? 1.0 : scale;
    }

    /**
     * Multiply the committed hit by {@code factor} (clamped to [0,1]) — the ADR-0071 self-malus
     * channel a reforge downside prices its holder's own hit through. Factors accumulate
     * multiplicatively, so two self-maluses both apply, order-free.
     */
    public void mulFinal(double factor) {
        finalFactor *= Math.max(0.0, Math.min(1.0, factor));
    }

    /** The accumulated self-malus factor (1.0 = none) — diagnostics/display. */
    public double finalFactor() {
        return finalFactor;
    }

    /** Contribute an outgoing-damage bonus, e.g. {@code 0.25} for +25% (may be negative). */
    public void addOutgoing(double percent) {
        outgoingPercent += percent;
    }

    /** Contribute a damage-reduction bonus, e.g. {@code 0.30} for −30% incoming (may be negative). */
    public void addReduction(double percent) {
        reductionPercent += percent;
    }

    /** Multiply base outgoing damage directly; factors compose multiplicatively and are clamped non-negative. */
    public void multiplyOutgoing(double factor) {
        outgoingMultiplier *= Math.max(0.0, factor);
    }

    /** Multiply incoming damage directly; factors compose multiplicatively and are clamped non-negative. */
    public void multiplyIncoming(double factor) {
        incomingMultiplier *= Math.max(0.0, factor);
    }

    /** Contribute a flat damage bonus, applied after the outgoing multiplier (§6.1). */
    public void addFlatDamage(double amount) {
        flatDamage += amount;
    }

    /**
     * Contribute a same-hit rider in EFFECTIVE units (ADR-0055): the authored amount is what the victim's
     * health pool sees pre-armor, exactly as the pre-1.8.2 bare {@code target.damage(amount)} delivered it.
     * Added after the scaled-and-mitigated custom economy in {@link #apply} — never multiplied by
     * {@code attack-scale} (that adapter belongs to the percent/flat economy alone) and never priced by the
     * defense terms (the old separate hurt was invisible to the defense walk, so a reduction or flat-red
     * stack must not learn to absorb riders it never could).
     */
    public void addEffectiveDamage(double amount) {
        effectiveDamage += amount;
    }

    /** Contribute a flat reduction, subtracted last from incoming damage (§6.1). */
    public void addFlatReduction(double amount) {
        flatReduction += amount;
    }

    /** Contribute the victim's heroic percent reduction — folded with {@link #addReduction}'s bucket at
     *  commit unless {@link #ignoreHeroic()} was set this hit (ADR-0053). */
    public void addHeroicReduction(double percent) {
        heroicReductionPercent += percent;
    }

    /** Contribute the victim's heroic flat reduction — folded with {@link #addFlatReduction}'s bucket at
     *  commit unless {@link #ignoreHeroic()} was set this hit (ADR-0053). */
    public void addHeroicFlatReduction(double amount) {
        heroicFlatReduction += amount;
    }

    /** Drop the victim's heroic buckets from this event's commit (IGNORE_HEROIC — Midas, ADR-0053). */
    public void ignoreHeroic() {
        heroicIgnored = true;
    }

    /** Fold {@code base} with every contribution into the final damage, never negative (§6.1). */
    public double apply(double base) {
        // Heroic joins the reduction sums BEFORE the cap (its old seat inside the plain buckets), so the
        // max-bonus-reduction ceiling and clamps see the identical totals when unflagged.
        double reduction = heroicIgnored ? reductionPercent : heroicReductionPercent + reductionPercent;
        double flatRed = heroicIgnored ? flatReduction : heroicFlatReduction + flatReduction;
        double cappedOutgoing = Math.min(outgoingPercent, maxBonusOutgoing);
        double cappedReduction = Math.min(reduction, maxBonusReduction);
        double outgoing = base * outgoingMultiplier
                * Math.max(0.0, 1.0 + cappedOutgoing * attackScale) + flatDamage * attackScale;
        double mitigated = outgoing * incomingMultiplier * Math.max(0.0, 1.0 - cappedReduction) - flatRed;
        // The rider bucket joins AFTER the inner clamp: flat reduction may zero the scaled economy but can
        // never reach into the riders (pre-1.8.2 they were separate events it could not absorb, ADR-0055).
        // The self-malus factor prices the WHOLE committed hit last (ADR-0071), after the riders join.
        return Math.max(0.0, Math.max(0.0, mitigated) + effectiveDamage) * finalFactor;
    }

    /** Reset every bucket for reuse on the next event. */
    public void reset() {
        flatDamage = 0.0;
        effectiveDamage = 0.0;
        flatReduction = 0.0;
        outgoingPercent = 0.0;
        reductionPercent = 0.0;
        outgoingMultiplier = 1.0;
        incomingMultiplier = 1.0;
        heroicReductionPercent = 0.0;
        heroicFlatReduction = 0.0;
        heroicIgnored = false;
        attackScale = 1.0;
        finalFactor = 1.0;
    }

    // Summed-bucket accessors for diagnostics / display.

    public double outgoingPercent() {
        return outgoingPercent;
    }

    public double reductionPercent() {
        return reductionPercent;
    }

    public double outgoingMultiplier() {
        return outgoingMultiplier;
    }

    public double incomingMultiplier() {
        return incomingMultiplier;
    }

    public double flatDamage() {
        return flatDamage;
    }

    public double effectiveDamage() {
        return effectiveDamage;
    }

    public double flatReduction() {
        return flatReduction;
    }

    public double heroicReductionPercent() {
        return heroicReductionPercent;
    }

    public double heroicFlatReduction() {
        return heroicFlatReduction;
    }

    public boolean heroicIgnored() {
        return heroicIgnored;
    }
}
