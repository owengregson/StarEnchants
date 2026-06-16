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
 * {@code ADD_DURABILITY} — restore durability to the player target's worn armor
 * (docs/architecture.md §7). Stateless; emits a {@code repairArmor} intent per resolved
 * player target, where {@code amount < 0} fully repairs. Only players wear armor, so
 * non-player targets are silently skipped. {@link Affinity#TARGET_ENTITY}: the repair
 * mutates the target's inventory, so on Folia the {@code Sink} routes each intent to the
 * target's region thread — declaring it here is all an author does.
 */
public final class AddDurabilityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("ADD_DURABILITY")
            .param("amount", D.INT.def(-1), "durability to restore; -1 fully repairs")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Restore durability to the player's worn armor.")
            .example("ADD_DURABILITY:200")
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
                sink.repairArmor(p, amount);
            }
        }
    }
}
