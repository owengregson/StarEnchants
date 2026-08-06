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
 * {@code HIT_TEMPO} — arm the holder's Quickening window (ADR-0071): for {@code duration} ticks the
 * combat dispatcher halves the effective hit-immunity window of the holder's melee victims (model
 * MENTAL: the 1.8-combat max/2 gate; VANILLA: the 1.9+ full-window cadence, paired with the
 * attack-speed modifier) and taxes each such hit to {@code damage-percent}% of its committed value
 * through the fold's self-malus channel.
 */
public final class HitTempoEffect implements EffectKind {

    public static final String HEAD = "HIT_TEMPO";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            // The three numbers are read INSIDE the target loop, so each armed body prices itself (ADR-0076);
            // declared, because PerTargetParamTest counts the reads and a hoist would otherwise pass silently.
            .param("duration", D.TICKS.def(100).perTarget())
            .param("model", D.enumOf("VANILLA", "MENTAL").def("VANILLA"))
            .param("damage-percent", D.DOUBLE.range(0, 100).def(33.3).perTarget())
            .param("attack-speed", D.DOUBLE.min(0).def(1.0).perTarget())
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Arm a hit-tempo window on the wearer for `duration` ticks: their melee victims' "
                    + "damage-immunity window is halved for the wearer's hits only (model MENTAL = "
                    + "the 1.8-combat half-window gate; VANILLA = the 1.9+ full-window cadence), "
                    + "each such hit deals `damage-percent`% of its normal damage, and on 1.9+ the "
                    + "wearer gains `attack-speed` (+1.0 = doubled) swing speed for the window. "
                    + "Third-party attackers are unaffected — their hits keep natural immunity.")
            .example("{ HIT_TEMPO: { duration: 100, model: MENTAL, damage-percent: 33.3, attack-speed: 1.0 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int model = "MENTAL".equals(ctx.str("model")) ? 0 : 1;
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.hitTempo(p, ctx.integer("duration"), model,
                        ctx.dbl("damage-percent") / 100.0, ctx.dbl("attack-speed"));
            }
        }
    }
}
