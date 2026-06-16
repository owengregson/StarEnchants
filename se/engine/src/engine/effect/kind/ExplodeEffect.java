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
 * {@code EXPLODE} — create an explosion at the target(s) (docs/architecture.md §7).
 * Stateless; emits an {@code explode} intent at each resolved target's location and
 * never touches the world directly. {@link Affinity#REGION}: an explosion mutates the
 * world (and optionally breaks blocks), so on Folia the {@code Sink} routes each
 * intent to the owning region's thread — declaring it here is all an author does.
 */
public final class ExplodeEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("EXPLODE")
            .param("power", D.DOUBLE.min(0))
            .param("breakBlocks", D.BOOL.def(false))
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Create an explosion at the target.")
            .example("EXPLODE:4:false")
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
            sink.explode(target.getLocation(), power, breakBlocks);
        }
    }
}
