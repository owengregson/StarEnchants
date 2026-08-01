package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/** {@code REGENERATION} — one non-overlapping, immediate-start regeneration task per player. */
public final class RegenerationEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REGENERATION")
            .param("amount", D.DOUBLE.min(0))
            .param("period", D.TICKS.min(1))
            .param("duration-min", D.TICKS)
            .param("duration-max", D.TICKS)
            .param("particle", D.particle())
            .param("count", D.INT.min(0).def(1))
            .param("speed", D.DOUBLE.min(0).def(0))
            .param("anchor", D.enumOf("body", "feet", "eye").def("body"))
            .param("y-offset", D.DOUBLE.min(-16).max(16).def(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Start one non-overlapping regeneration window per player; heal at t=0 and every period through "
                    + "the inclusive uniformly-selected duration boundary. Particle feedback occurs only on a heal.")
            .example("{ REGENERATION: { amount: 1, period: 60, duration-min: 110, duration-max: 125, "
                    + "particle: SPELL, count: 15, speed: 0.04, anchor: eye, y-offset: 0.5 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int min = Math.min(ctx.integer("duration-min"), ctx.integer("duration-max"));
        int max = Math.max(ctx.integer("duration-min"), ctx.integer("duration-max"));
        int duration = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        int anchor = switch (ctx.str("anchor").toLowerCase(Locale.ROOT)) {
            case "feet" -> 1;
            case "eye" -> 2;
            default -> 0;
        };
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.regeneration(player, duration, ctx.integer("period"), ctx.dbl("amount"),
                        ctx.integer("particle"), ctx.integer("count"), ctx.dbl("speed"),
                        anchor, ctx.dbl("y-offset"));
            }
        }
    }
}
