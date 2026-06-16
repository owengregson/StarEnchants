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
                // Damage arbiter contributions + direct damage (§6.1).
                .register(new DamageEffect())
                .register(new FlatDamageEffect())
                .register(new AddDamageEffect())
                .register(new ReduceDamageEffect())
                // Entity intents.
                .register(new HealEffect())
                .register(new IgniteEffect())
                .register(new LightningEffect())
                .register(new LaunchEffect())
                // Player feedback + event control.
                .register(new MessageEffect())
                .register(new ActionBarEffect())
                .register(new RunCommandEffect())
                .register(new CancelEffect())
                // Handle-using kinds (resolved tokens, §9).
                .register(new PotionEffect())
                .register(new RemovePotionEffect())
                .register(new SoundEffect())
                .register(new ParticleEffect())
                .register(new SpawnEffect())
                .build();
    }
}
