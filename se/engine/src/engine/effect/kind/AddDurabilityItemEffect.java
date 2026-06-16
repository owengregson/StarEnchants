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
 * {@code ADD_DURABILITY_ITEM} — restore durability to the player target's held item
 * (docs/architecture.md §7). Stateless; emits a {@code repairHand} intent per resolved
 * player target, where {@code amount < 0} fully repairs. Only players hold an item, so
 * non-player targets are silently skipped. {@link Affinity#TARGET_ENTITY}: the repair
 * mutates the target's inventory, so on Folia the {@code Sink} routes each intent to the
 * target's region thread — declaring it here is all an author does.
 */
public final class AddDurabilityItemEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("ADD_DURABILITY_ITEM")
            .param("amount", D.INT.def(-1), "durability to restore; -1 fully repairs")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Restore durability to the player's held item.")
            .example("ADD_DURABILITY_ITEM:200")
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
