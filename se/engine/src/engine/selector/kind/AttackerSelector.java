package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import java.util.List;

/**
 * {@code @Attacker} — the entity that damaged the activator, or no target unless this is an incoming hit
 * (docs/architecture.md §7).
 */
public final class AttackerSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("ATTACKER")
            .doc("The entity that damaged the activator (for defensive effects).")
            .example("@Attacker")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        return ctx.attacker() == null ? List.of() : List.of(ctx.attacker());
    }
}
