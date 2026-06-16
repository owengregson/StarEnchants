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
 * {@code REPAIR} — restore durability to the player's held item
 * (docs/architecture.md §7). Stateless; emits a {@code repairHand} intent per
 * resolved target that is a player, and never touches an item directly — a
 * non-player target simply has no hand to repair and is skipped.
 * {@link Affinity#TARGET_ENTITY}: repairing mutates the target's inventory, so on
 * Folia the {@code Sink} routes each intent to the target's region thread.
 */
public final class RepairEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REPAIR")
            .param("amount", D.INT.def(-1), "durability to restore; -1 fully repairs")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Repair the player's held item.")
            .example("REPAIR")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.repairHand(p, amount);
            }
        }
    }
}
