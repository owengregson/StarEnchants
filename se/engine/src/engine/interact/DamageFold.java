package engine.interact;

/**
 * The damage arbiter: a per-event accumulator that folds every source's contribution
 * into one final number, <em>once</em> (docs/architecture.md §6.1, ADR-0012). Damage
 * effects never call {@code event.setDamage}; they contribute deltas here, and after
 * the gate walk over the attacker's and victim's abilities the pipeline calls
 * {@link #apply} and writes the event a single time.
 *
 * <p><strong>Fully additive, no cross-source compounding</strong> (ADR-0012). All
 * outgoing percentages sum into one factor and all reductions into a parallel factor,
 * so the result is order-independent by construction — the registration-order
 * multiplicative compounding that was the catalog's worst combat bug cannot occur. The
 * full fold, with the flat terms made explicit:
 *
 * <pre>final = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)</pre>
 *
 * <p>The flat terms are placed for <em>predictability</em> (ADR-0012's goal): a flat
 * <em>damage</em> bonus (heroic offensive flat stat) is added to the attacker's output
 * <em>after</em> the outgoing multiplier — so it is not silently inflated by the
 * attacker's own percent buffs — but is still subject to the defender's reduction, like
 * any incoming damage. A flat <em>reduction</em> (heroic defensive flat stat) is
 * subtracted last, so it absorbs exactly the advertised amount regardless of percent
 * context. Both flat sources thus deliver their stated value rather than a
 * percent-scaled surprise.
 *
 * <p>Percentages are fractions: {@code 0.25} means +25%. Each factor is clamped at zero
 * (a reduction beyond 100%, or an outgoing debuff beyond −100%, yields no contribution
 * rather than negative healing), and the final result is clamped at zero.
 *
 * <p>This is per-event scratch owned by the single firing thread (§6) — not shared,
 * not thread-safe by design. Reuse across events via {@link #reset}.
 */
public final class DamageFold {

    private double flatDamage;
    private double flatReduction;
    private double outgoingPercent;
    private double reductionPercent;

    /** Contribute an outgoing-damage bonus, e.g. {@code 0.25} for +25% (may be negative). */
    public void addOutgoing(double percent) {
        outgoingPercent += percent;
    }

    /** Contribute a damage-reduction bonus, e.g. {@code 0.30} for −30% incoming (may be negative). */
    public void addReduction(double percent) {
        reductionPercent += percent;
    }

    /**
     * Contribute a flat damage bonus added to the attacker's output (after the outgoing
     * multiplier, before the defender's reduction). Delivers the advertised amount
     * without being inflated by the attacker's own percent buffs (heroic flat damage).
     */
    public void addFlatDamage(double amount) {
        flatDamage += amount;
    }

    /**
     * Contribute a flat reduction subtracted from the final incoming damage (after the
     * reduction multiplier). Absorbs exactly the advertised amount regardless of percent
     * context (heroic flat reduction, §6.1).
     */
    public void addFlatReduction(double amount) {
        flatReduction += amount;
    }

    /** Fold {@code base} with every accumulated contribution into the final damage (never negative). */
    public double apply(double base) {
        double outgoing = base * Math.max(0.0, 1.0 + outgoingPercent) + flatDamage;
        double mitigated = outgoing * Math.max(0.0, 1.0 - reductionPercent) - flatReduction;
        return Math.max(0.0, mitigated);
    }

    /** Reset every bucket for reuse on the next event. */
    public void reset() {
        flatDamage = 0.0;
        flatReduction = 0.0;
        outgoingPercent = 0.0;
        reductionPercent = 0.0;
    }

    /** The summed outgoing-damage fraction (for diagnostics / display). */
    public double outgoingPercent() {
        return outgoingPercent;
    }

    /** The summed reduction fraction (for diagnostics / display). */
    public double reductionPercent() {
        return reductionPercent;
    }

    /** The summed flat damage bonus (for diagnostics / display). */
    public double flatDamage() {
        return flatDamage;
    }

    /** The summed flat reduction (for diagnostics / display). */
    public double flatReduction() {
        return flatReduction;
    }
}
