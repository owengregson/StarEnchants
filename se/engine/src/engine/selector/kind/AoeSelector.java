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
 * {@code @Aoe{r=4}} — every living entity within {@code r} of the centre, except the activator. Centre is
 * the activation location, else the victim's, else the actor's. The {@code filter}/{@code limit} params
 * express Cosmic Enchants-style area targeting without a bespoke selector per case.
 */
public final class AoeSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("AOE")
            .param("r", D.DOUBLE.min(0).def(4), "radius in blocks")
            .param("filter", D.enumOf("ALL", "PLAYERS", "MONSTERS", "MOBS", "ENEMIES", "ALLIES").def("ALL"),
                    "which entities to include")
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
            if (!e.equals(ctx.actor()) && filter.accepts(ctx.actor(), e)) {
                matched.add(e);
            }
        }
        if (limit > 0 && matched.size() > limit) {
            // Scan order is unspecified; sort by distance so "nearest N" is well-defined.
            matched.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
            return new ArrayList<>(matched.subList(0, limit));
        }
        return matched;
    }
}
