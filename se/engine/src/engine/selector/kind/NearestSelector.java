package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import java.util.List;

/**
 * {@code @Nearest{r=5}} — the single closest living entity within {@code r} blocks of
 * the activation centre, excluding the activator (docs/architecture.md §7). Returns at
 * most one target; an empty list when nothing is in range. The radius defaults to
 * {@code 5} so the no-argument form is a valid default target.
 */
public final class NearestSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("NEAREST")
            .param("r", D.DOUBLE.min(0).def(5), "search radius in blocks")
            .doc("The single nearest living entity within r blocks, except the activator.")
            .example("@Nearest{r=5}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        Location center = Centers.of(ctx);
        if (center == null) {
            return List.of();
        }
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (e.equals(ctx.actor())) {
                continue;
            }
            double d = e.getLocation().distanceSquared(center);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        return nearest == null ? List.of() : List.of(nearest);
    }
}
