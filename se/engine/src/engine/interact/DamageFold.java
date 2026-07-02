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
 * <pre>final = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)</pre>
 *
 * <p>Flat-term placement gives predictable, percent-independent stats: flat damage adds
 * after the outgoing multiplier (so the attacker's own buffs don't inflate it) but still
 * faces the defender's reduction; flat reduction subtracts last (so it absorbs exactly its
 * advertised amount).
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
    private double flatReduction;
    private double outgoingPercent;
    private double reductionPercent;
    // Combat caps (config.yml combat.*): ceilings on the summed additive fractions; +inf = uncapped.
    private double maxBonusOutgoing = Double.POSITIVE_INFINITY;
    private double maxBonusReduction = Double.POSITIVE_INFINITY;

    /**
     * Additive combat caps for this event (config.yml {@code combat.max-bonus-damage} /
     * {@code combat.max-bonus-reduction}): ceilings on the summed outgoing% and reduction%
     * applied in {@link #apply}. A negative ceiling means "no cap" (the default).
     */
    public void caps(double maxBonusOutgoing, double maxBonusReduction) {
        this.maxBonusOutgoing = maxBonusOutgoing < 0 ? Double.POSITIVE_INFINITY : maxBonusOutgoing;
        this.maxBonusReduction = maxBonusReduction < 0 ? Double.POSITIVE_INFINITY : maxBonusReduction;
    }

    /** Contribute an outgoing-damage bonus, e.g. {@code 0.25} for +25% (may be negative). */
    public void addOutgoing(double percent) {
        outgoingPercent += percent;
    }

    /** Contribute a damage-reduction bonus, e.g. {@code 0.30} for −30% incoming (may be negative). */
    public void addReduction(double percent) {
        reductionPercent += percent;
    }

    /** Contribute a flat damage bonus, applied after the outgoing multiplier (§6.1). */
    public void addFlatDamage(double amount) {
        flatDamage += amount;
    }

    /** Contribute a flat reduction, subtracted last from incoming damage (§6.1). */
    public void addFlatReduction(double amount) {
        flatReduction += amount;
    }

    /** Fold {@code base} with every contribution into the final damage, never negative (§6.1). */
    public double apply(double base) {
        double cappedOutgoing = Math.min(outgoingPercent, maxBonusOutgoing);
        double cappedReduction = Math.min(reductionPercent, maxBonusReduction);
        double outgoing = base * Math.max(0.0, 1.0 + cappedOutgoing) + flatDamage;
        double mitigated = outgoing * Math.max(0.0, 1.0 - cappedReduction) - flatReduction;
        return Math.max(0.0, mitigated);
    }

    /** Reset every bucket for reuse on the next event. */
    public void reset() {
        flatDamage = 0.0;
        flatReduction = 0.0;
        outgoingPercent = 0.0;
        reductionPercent = 0.0;
    }

    // Summed-bucket accessors for diagnostics / display.

    public double outgoingPercent() {
        return outgoingPercent;
    }

    public double reductionPercent() {
        return reductionPercent;
    }

    public double flatDamage() {
        return flatDamage;
    }

    public double flatReduction() {
        return flatReduction;
    }
}
