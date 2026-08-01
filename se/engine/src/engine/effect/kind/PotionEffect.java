package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code POTION} — apply a potion effect to the target(s) for a duration in ticks (§7); {@code effect} interned
 * at compile (§9). The canonical maintained buff: lifecycle-aware (§B, ADR-0022), so a HELD/PASSIVE source's
 * deactivation calls {@link #stop} to clear exactly what it applied — no teardown enchant.
 */
public final class PotionEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("POTION")
            .param("effect", D.potionEffect())
            .param("level", D.INT.min(1))
            .param("duration", D.TICKS)
            .param("duration-min", D.TICKS.optional())
            .param("duration-max", D.TICKS.optional())
            .param("duration-step", D.INT.min(1).def(1))
            .param("duration-random-base", D.DOUBLE.min(0).optional(),
                    "when paired with duration-random-range, floor(random*range+base)*scale")
            .param("duration-random-range", D.DOUBLE.min(0).optional())
            .param("duration-random-scale", D.INT.min(1).def(1))
            .param("unless-present", D.BOOL.def(false),
                    "skip the target when it already has this potion type")
            .param("force", D.BOOL.def(false), "replace the active potion using Bukkit's force-add path")
            .param("chance", D.DOUBLE.min(0).max(100).def(100), "independent roll per resolved target")
            .param("particle", D.particle().optional(), "particle emitted at a target only when its roll succeeds")
            .param("particle-count", D.INT.min(0).def(1))
            .param("particle-speed", D.DOUBLE.min(0).def(0))
            .param("particle-anchor", D.enumOf("body", "feet", "eye").def("body"))
            .param("particle-y-offset", D.DOUBLE.min(-16).max(16).def(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Apply a potion effect to the target(s) at the given LEVEL (1-based: level 1 = the I tier),"
                    + " for a duration in ticks. The effect name is resolved to a handle at compile time. On a"
                    + " HELD/PASSIVE source it is removed again when the item is unequipped (§B lifecycle).")
            .example("{ POTION: { effect: STRENGTH, level: 1, duration: 100 } }")
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
        if (ctx.args().has("duration-random-base") && ctx.args().has("duration-random-range")) {
            duration = (int) (ThreadLocalRandom.current().nextDouble() * ctx.dbl("duration-random-range")
                    + ctx.dbl("duration-random-base")) * ctx.integer("duration-random-scale");
        } else if (ctx.args().has("duration-min") && ctx.args().has("duration-max")) {
            int min = Math.min(ctx.integer("duration-min"), ctx.integer("duration-max"));
            int max = Math.max(ctx.integer("duration-min"), ctx.integer("duration-max"));
            int step = ctx.integer("duration-step");
            int slots = (max - min) / step + 1;
            duration = min + ThreadLocalRandom.current().nextInt(slots) * step;
        }
        for (LivingEntity target : ctx.targets("who")) {
            if (ThreadLocalRandom.current().nextDouble(100.0) >= ctx.dbl("chance")) {
                continue;
            }
            if (ctx.bool("unless-present")) {
                sink.potionIfAbsent(target, effect, amplifier, duration);
            } else if (ctx.bool("force")) {
                sink.potionForce(target, effect, amplifier, duration);
            } else {
                sink.potion(target, effect, amplifier, duration);
            }
            if (ctx.args().has("particle")) {
                int anchor = switch (ctx.str("particle-anchor").toLowerCase(java.util.Locale.ROOT)) {
                    case "feet" -> 1;
                    case "eye" -> 2;
                    default -> 0;
                };
                sink.particle(target, ctx.integer("particle"), ctx.integer("particle-count"), -1,
                        0.0, 0.0, 0.0, ctx.dbl("particle-speed"), anchor,
                        ctx.dbl("particle-y-offset"));
            }
        }
    }

    /** §B teardown: the inverse of {@link #run} — {@code removePotion} of the same handle (amplifier/duration irrelevant to a clear). */
    @Override
    public void stop(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        for (LivingEntity target : ctx.targets("who")) {
            sink.removePotion(target, effect);
        }
    }
}
