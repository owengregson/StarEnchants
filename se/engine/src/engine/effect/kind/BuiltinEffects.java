package engine.effect.kind;

import engine.effect.EffectRegistry;

/**
 * The explicit, greppable list of every built-in effect kind (§7, §13.2) — no annotation scan, no generated
 * table, so adding an effect is one {@code .register(new ...)} line a reviewer reads top to bottom.
 */
public final class BuiltinEffects {

    private BuiltinEffects() {
    }

    public static EffectRegistry registry() {
        return EffectRegistry.builder()
                // Damage arbiter contributions + direct damage (§6.1).
                .register(new DamageEffect())
                .register(new DamageModEffect()) // §C canonical; replaces ADD_DAMAGE/REDUCE_DAMAGE/FLAT_DAMAGE/FLAT_REDUCE
                .register(new DamageScaleEffect()) // count-scaled fold contribution (KOTH Victorious: +N% per nearby player)
                // Entity intents.
                .register(new HealthModEffect()) // §C canonical MODIFY_HEALTH (give/take/transfer); replaces HEAL
                .register(new IgniteEffect())
                .register(new LightningEffect())
                .register(new TeleportEffect())
                // Player feedback + event control.
                .register(new MessageEffect()) // §C canonical; channel chat/actionbar/title — replaces ACTIONBAR/TITLE
                .register(new RunCommandEffect())
                .register(new CancelEffect())
                // Handle-using kinds (resolved tokens, §9).
                .register(new PotionEffect())
                .register(new RemovePotionEffect())
                .register(new CureEffect())
                .register(new SoundEffect())
                .register(new ParticleEffect())
                .register(new ParticleRingEffect()) // shaped coloured dust: ring (KOTH Victorious crown aura)
                .register(new ParticleLineEffect()) // shaped coloured dust: line/tether to each target
                // Entity-state intents.
                .register(new KillEffect())
                .register(new ExtinguishEffect())
                .register(new FillOxygenEffect())
                .register(new DurabilityEffect()) // §C canonical; replaces ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR
                .register(new ExpEffect()) // §C canonical MODIFY_EXP (give/take/transfer); replaces GIVE_EXP
                .register(new FoodEffect()) // §C canonical MODIFY_FOOD (give/take); replaces FEED
                .register(new MoneyEffect()) // §C canonical MODIFY_MONEY (give/take/transfer); replaces GIVE_MONEY/TAKE_MONEY
                .register(new RemoveSoulsEffect()) // §D actor-only soul debit; charges the activator's active gem
                .register(new DisarmEffect())
                .register(new RemoveArmorEffect()) // Cosmic Enchants-style REMOVE_ARMOR: drop a random worn armour piece
                // World / spawn intents.
                .register(new ExplodeEffect())
                .register(new SpawnEntityEffect()) // §C canonical; replaces SPAWN/TNT (FIREBALL folded into PROJECTILE)
                .register(new GuardEffect()) // § combat-flags GUARD: summon mob(s) targeting the attacker
                // Movement + vitals.
                .register(new VelocityEffect()) // §C canonical; replaces THROW/LAUNCH/KNOCKBACK
                .register(new FlyEffect())
                .register(new HealthEffect())
                // §C block + item primitives.
                .register(new SetBlockEffect())
                .register(new BreakBlockEffect())
                .register(new WalkerEffect()) // §C new primitive: temporary revert-after-ticks platform
                .register(new DropItemEffect())
                .register(new GiveItemEffect())
                .register(new RemoveItemEffect())
                // §C spawn / visual primitives.
                .register(new FireworkEffectKind())
                .register(new ProjectileEffect())
                // §C temporary player-state primitives.
                .register(new MovementSpeedEffect())
                .register(new InvincibleEffect())
                // §A writable variables + § combat-flags.
                .register(new SetVarEffect()) // §A SET_VAR: per-player named var, read back as %name%
                .register(new InvertVarEffect()) // §A INVERT_VAR: numeric flip of a per-player named var
                .register(new IgnoreArmorEffect()) // § combat-flags IGNORE_ARMOR: hit bypasses armor/protection
                .register(new KnockbackControlEffect()) // § combat-flags KNOCKBACK_CONTROL: cancel/scale incoming knockback
                .register(new KeepOnDeathEffect()) // § combat-flags KEEP_ON_DEATH: keep items+levels on a death
                .register(new TeleblockEffect()) // § combat-flags TELEBLOCK: block a target from teleporting
                .register(new ImmuneEffect()) // § combat-flags IMMUNE: timed immunity to a damage cause
                .register(new SuppressEffect()) // §C SUPPRESS: disable a target's enchant/group/type (DISABLE_*)
                // Inline read-backs applied by the MINE / BOW_FIRE dispatchers (Cosmic Enchants-style SMELT/TELEPORT_DROPS/AUTO_LOCK).
                .register(new SmeltEffect())
                .register(new TeleportDropsEffect())
                .register(new SeekEffect())
                .build();
    }
}
