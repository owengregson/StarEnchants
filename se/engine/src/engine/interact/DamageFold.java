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
 * <pre>folded = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)
 *final  = max(0, folded × clamp(1 + Σ heroicOut%, 0, 4) × clamp(1 − Σ heroicRed%, 0, 1))</pre>
 *
 * <p>Heroic (§F, ADR-0021) is a DISTINCT bounded <em>multiplicative</em> stage layered on top of the
 * additive fold — it compounds the folded result rather than summing into it, so heroic is the one
 * deliberate exception to ADR-0012's no-compounding rule (and it is bounded, so it cannot run away).
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

    /** The default ceiling on the heroic multiplicative stage: at most QUADRUPLE outgoing damage (§F/ADR-0021). */
    public static final double DEFAULT_MAX_HEROIC_OUTGOING_FACTOR = 4.0;

    /** The live ceiling on the heroic outgoing factor (config.yml {@code heroic.max-outgoing-factor}, §F). */
    private final java.util.function.DoubleSupplier maxOutgoingFactor;

    private double flatDamage;
    private double flatReduction;
    private double outgoingPercent;
    private double reductionPercent;
    private double heroicOutgoing;
    private double heroicReduction;
    // Combat caps (config.yml combat.*): ceilings on the summed additive fractions; +inf = uncapped.
    private double maxBonusOutgoing = Double.POSITIVE_INFINITY;
    private double maxBonusReduction = Double.POSITIVE_INFINITY;

    /** Fold with the built-in heroic ceiling (the common test/fixture form). */
    public DamageFold() {
        this(() -> DEFAULT_MAX_HEROIC_OUTGOING_FACTOR);
    }

    /**
     * Fold with a configurable heroic outgoing ceiling read live per {@link #apply} — the composition root
     * passes {@code () -> master.config().heroic().maxOutgoingFactor()} so a {@code /se reload} re-tunes it.
     */
    public DamageFold(java.util.function.DoubleSupplier maxOutgoingFactor) {
        this.maxOutgoingFactor = java.util.Objects.requireNonNull(maxOutgoingFactor, "maxOutgoingFactor");
    }

    /**
     * Set the additive combat caps for this event (config.yml {@code combat.max-bonus-damage} /
     * {@code combat.max-bonus-reduction}): a ceiling on the summed outgoing% and reduction% applied in
     * {@link #apply}. A negative ceiling means "no cap" (the default). The composition root calls this per
     * event from the live config so a {@code /se reload} re-tunes the caps.
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
     * context (a flat-reduction effect, §6.1).
     */
    public void addFlatReduction(double amount) {
        flatReduction += amount;
    }

    /**
     * Contribute the attacker's heroic outgoing-damage percent (e.g. {@code 0.10} for +10%), applied
     * as a distinct bounded MULTIPLICATIVE stage AFTER the additive fold (§F, ADR-0021). Distinct from
     * {@link #addOutgoing} (which sums into the additive fold) — heroic compounds the folded result.
     */
    public void addHeroicOutgoing(double percent) {
        heroicOutgoing += percent;
    }

    /** Contribute the defender's heroic damage-reduction percent (e.g. {@code 0.10} for −10%), §F. */
    public void addHeroicReduction(double percent) {
        heroicReduction += percent;
    }

    /**
     * Fold {@code base} with every accumulated contribution into the final damage (never negative).
     * The additive fold (ADR-0012) runs first and is order-independent; then the heroic percents apply
     * as a separate bounded multiplicative stage (§F): {@code ×clamp(1+ΣheroicOut) ×clamp(1−ΣheroicRed)}.
     * The outgoing factor is clamped to {@code [0, 4]} and the reduction factor to {@code [0, 1]}, so
     * heroic cannot quadruple-and-then-some nor invert damage.
     */
    public double apply(double base) {
        // Combat caps (config.yml) ceil the summed additive fractions before the fold; +inf = uncapped.
        double cappedOutgoing = Math.min(outgoingPercent, maxBonusOutgoing);
        double cappedReduction = Math.min(reductionPercent, maxBonusReduction);
        double outgoing = base * Math.max(0.0, 1.0 + cappedOutgoing) + flatDamage;
        double mitigated = outgoing * Math.max(0.0, 1.0 - cappedReduction) - flatReduction;
        double folded = Math.max(0.0, mitigated);
        double ceiling = Math.max(1.0, maxOutgoingFactor.getAsDouble());
        double heroicOut = Math.min(ceiling, Math.max(0.0, 1.0 + heroicOutgoing));
        double heroicRed = Math.max(0.0, Math.min(1.0, 1.0 - heroicReduction));
        return Math.max(0.0, folded * heroicOut * heroicRed);
    }

    /** Reset every bucket for reuse on the next event. */
    public void reset() {
        flatDamage = 0.0;
        flatReduction = 0.0;
        outgoingPercent = 0.0;
        reductionPercent = 0.0;
        heroicOutgoing = 0.0;
        heroicReduction = 0.0;
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

    /** The summed heroic outgoing-damage fraction (for diagnostics / display). */
    public double heroicOutgoing() {
        return heroicOutgoing;
    }

    /** The summed heroic reduction fraction (for diagnostics / display). */
    public double heroicReduction() {
        return heroicReduction;
    }
}
