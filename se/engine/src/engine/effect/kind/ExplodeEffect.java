package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;
import schema.spec.D;

/** {@code EXPLODE} — create an explosion at the target(s) (§7). */
public final class ExplodeEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("EXPLODE")
            .param("power", D.DOUBLE.min(0))
            .param("breakBlocks", D.BOOL.def(false))
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Create an explosion at the target.")
            .example("{ EXPLODE: { power: 4, breakBlocks: false } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double power = ctx.dbl("power");
        boolean breakBlocks = ctx.bool("breakBlocks");
        for (LivingEntity target : ctx.targets("who")) {
            Location at;
            try {
                at = target.getLocation(); // T.VICTIM, but @Attacker on a DEFENSE trigger can be a cross-region shooter (ADR-0043)
            } catch (RuntimeException unreadable) {
                Regions.swallowed("ExplodeEffect.target", unreadable);
                continue;
            }
            sink.explode(at, power, breakBlocks);
        }
    }
}
