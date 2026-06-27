package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code TELEBLOCK} — block the target from teleporting (ender-pearl / chorus) for a duration (§ combat-flags).
 * Writes a per-player timed flag; a SEPARATE launch/teleport event reads it back and cancels. Player-only.
 */
public final class TeleblockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TELEBLOCK")
            .param("duration", D.TICKS.def(400))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Block the target player(s) from teleporting (ender pearl / chorus fruit) for duration ticks.")
            .example("{ TELEBLOCK: { duration: 400 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.teleblock(player, duration);
            }
        }
    }
}
