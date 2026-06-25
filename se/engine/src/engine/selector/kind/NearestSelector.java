package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import java.util.List;

/**
 * {@code @Nearest{r=5}} — the single closest living entity within {@code r} of the centre, except the
 * activator (docs/architecture.md §7); at most one target. Optional {@code filter}
 * ({@code ALL}/{@code PLAYERS}/{@code MONSTERS}/{@code MOBS}) restricts candidates, e.g.
 * {@code @Nearest{filter=PLAYERS}} (v3.1 §A).
 */
public final class NearestSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("NEAREST")
            .param("r", D.DOUBLE.min(0).def(5), "search radius in blocks")
            .param("filter", D.enumOf("ALL", "PLAYERS", "MONSTERS", "MOBS").def("ALL"), "which entities to consider")
            .doc("The single nearest living entity within r blocks (optionally filtered), except the activator.")
            .example("@Nearest{r=5, filter=PLAYERS}")
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
        Targets.Filter filter = Targets.of(ctx);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (e.equals(ctx.actor()) || !filter.accepts(e)) {
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
