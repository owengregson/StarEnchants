package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * {@code SOUL_MODE_DISABLE} — force a player out of active soul mode. {@code REMOVE_SOULS} is the near-miss:
 * it empties the wallet but leaves the switch on, so a trap that only drained souls would be undone the moment
 * the victim picked up a single soul. The store owns the state; this is a one-verb write into it.
 *
 * <p>Feedback is the soul system's own deactivate lines and cues — a forced exit must read to the victim
 * exactly like the toggle they know, not as a silent state change they discover mid-fight.
 */
public final class SoulModeDisableEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SOUL_MODE_DISABLE")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Force the target out of soul mode: pending spends settle to their gems, the pool is dropped "
                    + "and they are told, with the same lines and cues a manual toggle-off sends. A no-op on a "
                    + "target who is not in soul mode. Pair with REMOVE_SOULS to drain the wallet AND flip the "
                    + "switch — draining alone leaves the mode running.")
            .example("{ SOUL_MODE_DISABLE: { who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.disableSoulMode(player);
            }
        }
    }
}
