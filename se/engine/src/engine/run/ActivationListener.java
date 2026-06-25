package engine.run;

import compile.model.Ability;

/**
 * A Bukkit-FREE hook the {@link AbilityExecutor} invokes once per activated ability (docs/architecture.md
 * §13): keeps the engine off any event API while the feature/bootstrap impl fires the public Bukkit
 * {@code EnchantActivateEvent}. Invoked on the firing thread, so it must be cheap and must not block.
 *
 * <p>{@code enchantKey} contracts (resolved by the executor; see {@link AbilityExecutor}): reload-safe
 * (named against the run's own snapshot), the BASE content key with level carried on {@link Ability#level()},
 * and {@code null} only defensively (no stable-key index for the run).
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
