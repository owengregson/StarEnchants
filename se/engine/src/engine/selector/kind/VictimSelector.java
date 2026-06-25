package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import java.util.List;

/** {@code @Victim} — the combat victim, or no target for a non-combat activation. */
public final class VictimSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("VICTIM")
            .doc("The combat victim (the entity the activator hit).")
            .example("@Victim")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        return ctx.victim() == null ? List.of() : List.of(ctx.victim());
    }
}
