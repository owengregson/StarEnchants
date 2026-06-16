package engine.run;

import compile.model.Ability;

/**
 * A Bukkit-FREE hook the {@link AbilityExecutor} invokes once per ability that activates
 * (docs/architecture.md §13). It keeps the engine free of any event API: the engine calls this
 * callback; the feature/bootstrap layer's implementation is what fires the public Bukkit
 * {@code EnchantActivateEvent}. Invoked on the firing thread, after the ability's gates pass and
 * before (or alongside) its effects emit into the Sink — so it must be cheap and must not block.
 *
 * <p>The {@code enchantKey} is resolved by the executor against the SAME snapshot whose
 * {@code abilities[]} produced the activated ability (never re-read from a live holder that a
 * concurrent reload could have swapped), so it always names the ability that actually fired. It is the
 * BASE content key (e.g. {@code enchants/venom}) — the compiled per-level key {@code enchants/venom/1}
 * has its {@code /<level>} stripped, with the level carried separately on {@link Ability#level()}. It
 * is {@code null} only if the snapshot exposed no stable-key index for the run — a defensive case the
 * implementation should skip rather than propagate.
 */
@FunctionalInterface
public interface ActivationListener {

    /** A no-op listener — the default when no observer is wired. */
    ActivationListener NONE = (enchantKey, ability, context) -> { };

    /**
     * Called once for {@code ability} when it activates in {@code context}.
     *
     * @param enchantKey the activated ability's BASE stable content key (e.g. {@code enchants/venom},
     *                   {@code crystals/jolt}), resolved against the run's own snapshot; {@code null}
     *                   only defensively
     */
    void onActivate(String enchantKey, Ability ability, ActivationContext context);
}
