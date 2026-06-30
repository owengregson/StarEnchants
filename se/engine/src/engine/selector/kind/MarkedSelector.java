package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.sink.DamageMarks;
import engine.spec.SelectorSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code @Marked{r=32}} — every nearby living entity the activator currently has an active {@code MARK} on
 * (reaper's continuous tether: a {@code REPEATING} {@code PARTICLE_LINE} redraws a beam to each still-marked
 * victim every 0.5s). A radius scan of the marked set, like {@link AllPlayersSelector}: the marks live by UUID,
 * so a near-by filter resolves them to entities without a forbidden cross-region {@code getEntity} hop.
 */
public final class MarkedSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("MARKED")
            .param("r", D.DOUBLE.min(0).def(32), "search radius in blocks")
            .doc("Every nearby living entity the activator currently has an active MARK on.")
            .example("@Marked{r=32}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        if (ctx.actor() == null) {
            return List.of();
        }
        Set<UUID> marked = DamageMarks.marked(ctx.actor().getUniqueId());
        if (marked.isEmpty()) {
            return List.of();
        }
        Location center = Centers.of(ctx);
        if (center == null) {
            return List.of();
        }
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (marked.contains(e.getUniqueId())) {
                out.add(e);
            }
        }
        return out;
    }
}
