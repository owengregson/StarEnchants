package engine.pipeline;

import engine.interact.ReboundPlan;

/**
 * Gate 9's PROC_REBOUND veto (Enchant Reflect). Gate 9 is the structurally right slot: a veto there has
 * already released the gate-6 cooldown reservation, never debits souls, and records {@code CANCELLED} for
 * {@code /se why} — which is exactly "the reflected enchant is NOT applied to the reflector for that hit".
 * The re-execution against the swapped roles is the dispatcher's, not the pipeline's.
 *
 * <p>An activation with no plan (every non-combat trigger, the defence walk, every hit on a victim with
 * nothing armed) costs one null check.
 */
public final class ReboundGate {

    /** The production gate-9 guard; install it once at the composition root. */
    public static final ActivationPipeline.Guard INSTANCE = (ability, activation) -> {
        ReboundPlan plan = activation.rebound();
        return plan == null || !plan.claim(ability);
    };

    private ReboundGate() {
    }
}
