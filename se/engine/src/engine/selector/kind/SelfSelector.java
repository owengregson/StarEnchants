package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import java.util.List;

/**
 * {@code @Self} — the activating player themself (docs/architecture.md §7). The
 * implicit default for self-directed effects (heal, self-potion, message).
 */
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
        // Explicit witness: actor() is a Player, and List.of(player) would infer
        // List<Player>, which is not a List<LivingEntity> (generics are invariant).
        return ctx.actor() == null ? List.of() : List.<LivingEntity>of(ctx.actor());
    }
}
