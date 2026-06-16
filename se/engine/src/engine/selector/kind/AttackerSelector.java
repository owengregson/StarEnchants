package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import java.util.List;

/**
 * {@code @Attacker} — the entity that damaged the activator (the aggressor on an
 * incoming hit), or no target when the activation is not an incoming hit
 * (docs/architecture.md §7). The defender's counter-attack target.
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
