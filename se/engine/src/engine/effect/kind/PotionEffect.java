package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code POTION} — apply a potion effect to the target(s) for a duration in ticks
 * (docs/architecture.md §7). Stateless; emits one {@code potion} intent per resolved
 * target and never touches an entity directly. The {@code effect} arg is a
 * version-volatile handle: it is authored as a name (e.g. {@code STRENGTH}) and
 * resolved to an interned id at compile time, so the runtime never sees a renamed
 * constant (§9). {@link Affinity#TARGET_ENTITY}: the {@code Sink} routes each intent
 * to the owning entity's thread.
 *
 * <p>As the canonical maintained buff, {@code POTION} is also lifecycle-aware (§B, ADR-0022): on a
 * HELD/PASSIVE source's deactivation the engine calls {@link #stop}, which clears the very effect this
 * applied (the exact inverse — a {@code removePotion} of the same handle). So a passive "Speed while worn"
 * is authored as one {@code POTION:SPEED:…} line whose buff turns on at equip and off at unequip, with no
 * separate teardown enchant. Author a long {@code duration} (or pair with {@code REPEATING}) so the buff
 * does not lapse mid-wear; {@code stop} guarantees it never outlives the item.
 */
public final class PotionEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("POTION")
            .param("effect", D.potionEffect())
            .param("level", D.INT.min(1))
            .param("duration", D.TICKS)
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Apply a potion effect to the target(s) at the given LEVEL (1-based: level 1 = the I tier),"
                    + " for a duration in ticks. The effect name is resolved to a handle at compile time. On a"
                    + " HELD/PASSIVE source it is removed again when the item is unequipped (§B lifecycle).")
            .example("POTION:STRENGTH:1:100")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        int amplifier = ctx.integer("level") - 1; // §C: authored level is 1-based; Bukkit amplifier is 0-based
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            sink.potion(target, effect, amplifier, duration);
        }
    }

    /**
     * §B lifecycle teardown: a HELD/PASSIVE potion buff is removed when its source unequips, so a worn
     * "Strength while held" never lingers after the sword is put away. The exact inverse of {@link #run} —
     * {@code removePotion} of the same compile-resolved handle (amplifier/duration are irrelevant to a clear).
     */
    @Override
    public void stop(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        for (LivingEntity target : ctx.targets("who")) {
            sink.removePotion(target, effect);
        }
    }
}
