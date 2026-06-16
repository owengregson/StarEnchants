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
 * {@code FEED} — restore food points to the player target(s) (docs/architecture.md §7).
 * Stateless; emits a {@code feed} intent per resolved player target. Only players have a
 * hunger bar, so non-player targets are silently skipped. {@link Affinity#TARGET_ENTITY}:
 * feeding mutates the target, so on Folia the {@code Sink} routes each intent to the
 * target's region thread — declaring it here is all an author does.
 */
public final class FeedEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FEED")
            .param("amount", D.INT.min(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Restore food points to the player target.")
            .example("FEED:6")
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
                sink.feed(p, amount);
            }
        }
    }
}
