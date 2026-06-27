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
 * {@code KEEP_ON_DEATH} — keep the target's items + levels if they die within {@code duration} ticks
 * (§C combat-flags); a death listener reads the per-player flag this arms. The engine has no unequip
 * teardown, so an always-on "while worn" keep is authored on {@code REPEATING} with {@code duration} &ge; the
 * period — re-armed each tick worn, lapsing shortly after removal. Player-only.
 */
public final class KeepOnDeathEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("KEEP_ON_DEATH")
            .param("duration", D.TICKS.def(200))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Keep the target's items + levels (no drops) if they die within duration ticks (default "
                    + "200). Author on trigger REPEATING for an always-on death-keep while worn, or fire on "
                    + "a trigger for a timed grace window. A kept death never spends a holy scroll.")
            .example("{ KEEP_ON_DEATH: { duration: 200 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player player) {
                sink.keepOnDeath(player, duration);
            }
        }
    }
}
