package engine.effect.kind;

import engine.effect.EffectRegistry;

/**
 * The explicit, greppable list of every built-in effect kind (docs/architecture.md
 * §7, §13.2). Adding an effect is: implement {@link engine.effect.EffectKind}, then
 * add one {@code .register(new ...)} line here. There is no annotation scan and no
 * generated table — the wiring is visible in one file a reviewer can read top to
 * bottom.
 */
public final class BuiltinEffects {

    private BuiltinEffects() {
    }

    /** A registry of all built-in effect kinds. */
    public static EffectRegistry registry() {
        return EffectRegistry.builder()
                .register(new DamageEffect())
                .register(new HealEffect())
                .build();
    }
}
