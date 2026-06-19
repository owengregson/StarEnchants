package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import java.util.List;

/**
 * {@code @EntityInSight{r=16}} — the living entity the activator is looking at within {@code r} blocks, or
 * an empty result if nothing is in their line of sight (docs/architecture.md §7; v3.1 §A, AE parity). The
 * raytrace goes through the injected world-access seam and originates from the activator on its own (firing)
 * region thread, so it is Folia-correct. The range defaults to {@code 16}.
 */
public final class EntityInSightSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("ENTITYINSIGHT")
            .param("r", D.DOUBLE.min(0).def(16), "maximum line-of-sight distance in blocks")
            .doc("The living entity the activator is looking at within r blocks, or nothing.")
            .example("@EntityInSight{r=16}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        LivingEntity hit = ctx.entityInSight(ctx.dbl("r"));
        return hit == null || hit.equals(ctx.actor()) ? List.of() : List.of(hit);
    }
}
