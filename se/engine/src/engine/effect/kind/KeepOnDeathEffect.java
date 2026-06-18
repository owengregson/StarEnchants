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
 * {@code KEEP_ON_DEATH} — keep the target's items + levels if they die soon (docs/v3-directives.md §C
 * combat-flags). The proc arms a per-player flag through the per-event {@link Sink} for {@code duration}
 * ticks; the death listener keeps the inventory (and clears drops) if the flag is live when the player
 * dies.
 *
 * <p>The engine has no unequip teardown hook, so an always-on "while worn" death-keep is authored on
 * trigger {@code REPEATING} with {@code duration} &ge; the repeat period — the flag is re-armed each tick
 * it stays equipped and lapses shortly after it is removed. A timed grace window (e.g. on DEFENSE) is the
 * other use. Player-only and {@link Affinity#CONTEXT_LOCAL} (it writes only an in-memory flag).
 */
public final class KeepOnDeathEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("KEEP_ON_DEATH")
            .param("duration", D.TICKS.def(200))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Keep the target's items + levels (no drops) if they die within duration ticks (default "
                    + "200). Author on trigger REPEATING for an always-on death-keep while worn, or fire on "
                    + "a trigger for a timed grace window. A kept death never spends a holy scroll.")
            .example("KEEP_ON_DEATH:200")
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
