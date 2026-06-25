package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import java.util.List;

/** {@code @Self} — the activating player; the default for self-directed effects. */
public final class SelfSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("SELF")
            .doc("The activating player themself.")
            .example("@Self")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        // Explicit witness: List.of(player) infers List<Player>, not List<LivingEntity> (invariant generics).
        return ctx.actor() == null ? List.of() : List.<LivingEntity>of(ctx.actor());
    }
}
