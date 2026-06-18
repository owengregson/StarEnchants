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
                .register(new FlatReduceEffect())
                .register(new AddDamageEffect())
                .register(new ReduceDamageEffect())
                // Entity intents.
                .register(new HealthModEffect()) // §C canonical MODIFY_HEALTH (give/take/transfer); replaces HEAL
                .register(new IgniteEffect())
                .register(new LightningEffect())
                .register(new TeleportEffect())
                // Player feedback + event control.
                .register(new MessageEffect())
                .register(new ActionBarEffect())
                .register(new TitleEffect())
                .register(new RunCommandEffect())
                .register(new CancelEffect())
                // Handle-using kinds (resolved tokens, §9).
                .register(new PotionEffect())
                .register(new RemovePotionEffect())
                .register(new CureEffect())
                .register(new SoundEffect())
                .register(new ParticleEffect())
                .register(new SpawnEffect())
                .register(new StrikeEffect())
                // Entity-state intents.
                .register(new KillEffect())
                .register(new ExtinguishEffect())
                .register(new FillOxygenEffect())
                .register(new RepairEffect())
                .register(new ExpEffect()) // §C canonical MODIFY_EXP (give/take/transfer); replaces GIVE_EXP
                .register(new FoodEffect()) // §C canonical MODIFY_FOOD (give/take); replaces FEED
                .register(new MoneyEffect()) // §C canonical MODIFY_MONEY (give/take/transfer); replaces GIVE_MONEY/TAKE_MONEY
                .register(new DisarmEffect())
                // World / spawn intents.
                .register(new ExplodeEffect())
                .register(new SpawnTntEffect())
                .register(new FireballEffect())
                // Movement + durability + vitals.
                .register(new VelocityEffect()) // §C canonical; replaces THROW/LAUNCH/KNOCKBACK
                .register(new FlyEffect())
                .register(new HealthEffect())
                .register(new DamageArmorEffect())
                .register(new AddDurabilityEffect())
                .register(new AddDurabilityItemEffect())
                // §C block + item primitives.
                .register(new SetBlockEffect())
                .register(new BreakBlockEffect())
                .register(new DropItemEffect())
                .register(new GiveItemEffect())
                .register(new RemoveItemEffect())
                // §C spawn / visual primitives.
                .register(new FireworkEffectKind())
                .register(new ProjectileEffect())
                // §C temporary player-state primitives.
                .register(new MovementSpeedEffect())
                .register(new InvincibleEffect())
                .build();
    }
}
