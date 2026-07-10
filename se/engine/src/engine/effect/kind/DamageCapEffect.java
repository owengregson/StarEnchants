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
 * {@code DAMAGE_CAP} — arm a one-shot cap so the wearer's NEXT incoming hit is capped at {@code factor} × their
 * last-taken damage (ADR-0049 Diminish); with {@code reflect}, the overflow above the cap is dealt back to the
 * attacker (Vengeful Diminish). Self-only. The cap value is computed at ARM time from the last-taken history, so
 * no history arms nothing.
 */
public final class DamageCapEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DAMAGE_CAP")
            .param("factor", D.DOUBLE.min(0).def(0.5))
            .param("reflect", D.BOOL.def(false))
            .param("duration", D.TICKS.def(100))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Cap the wearer's next incoming hit at factor times the last damage they took, for a duration "
                    + "in ticks; with reflect, the overflow above the cap is dealt back to the attacker (Diminish). "
                    + "Self-only; no cap is armed until at least one hit has been taken.")
            .example("{ DAMAGE_CAP: { factor: 0.5, reflect: true, duration: 100 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double factor = ctx.dbl("factor");
        boolean reflect = ctx.bool("reflect");
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.armDamageCap(p, factor, reflect, duration);
            }
        }
    }
}
