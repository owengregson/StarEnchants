package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code @Aoe{r=4}} — every living entity within {@code r} blocks of the activation
 * centre, excluding the activator (docs/architecture.md §7, the {@code SMITE}
 * example). The centre is the activation's location, falling back to the victim's
 * then the actor's location. The radius defaults to {@code 4} so the no-argument form
 * is a valid default target.
 *
 * <p>Two optional refinements (v3.1 §A): {@code filter} restricts the result set
 * ({@code ALL}/{@code PLAYERS}/{@code MONSTERS}/{@code MOBS}; default {@code ALL}) and
 * {@code limit} caps it to the nearest N targets ({@code 0} = unlimited). Together they
 * express AE-style area targeting like {@code @Aoe{r=6, filter=MONSTERS}} (a BUTCHER)
 * without a bespoke selector per case.
 */
public final class AoeSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("AOE")
            .param("r", D.DOUBLE.min(0).def(4), "radius in blocks")
            .param("filter", D.enumOf("ALL", "PLAYERS", "MONSTERS", "MOBS").def("ALL"), "which entities to include")
            .param("limit", D.INT.min(0).def(0), "max targets, nearest first (0 = unlimited)")
            .doc("Living entities within r blocks of the target, except the activator; optionally filtered and capped.")
            .example("@Aoe{r=6, filter=MONSTERS}")
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
        int limit = ctx.integer("limit");
        List<LivingEntity> matched = new ArrayList<>();
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (!e.equals(ctx.actor()) && filter.accepts(e)) {
                matched.add(e);
            }
        }
        if (limit > 0 && matched.size() > limit) {
            // Keep the nearest `limit` — the scan order is unspecified, so sort by distance to the centre.
            matched.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
            return new ArrayList<>(matched.subList(0, limit));
        }
        return matched;
    }
}
