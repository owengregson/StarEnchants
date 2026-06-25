package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code TELEPORT_DROPS} — send the triggering MINE's block drops straight to the breaker's inventory. An
 * inline read-back like {@code IGNORE_ARMOR}: the proc sets a flag the MINE dispatcher reads after the gate
 * walk and applies to the {@code BlockBreakEvent} (add to inventory, suppress the world drop). Author on MINE.
 * {@link Affinity#CONTEXT_LOCAL}.
 */
public final class TeleportDropsEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TELEPORT_DROPS")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Send the block's drops straight to the breaker's inventory (this MINE activation).")
            .example("TELEPORT_DROPS")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.teleportDrops();
    }
}
