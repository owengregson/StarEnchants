package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;
import java.util.List;

/**
 * {@code @NearestPlayer{r=16}} — the single closest player within {@code r} blocks of the activation
 * centre, excluding the activator (docs/architecture.md §7; v3.1 §A, Cosmic Enchants-style parity). A named alias for
 * {@code @Nearest{filter=PLAYERS}} (clearer in content and the auto-doc), implemented as a player-filtered
 * nearest scan over the injected area-scan seam (Folia-correct). The radius defaults to {@code 16}.
 */
public final class NearestPlayerSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("NEARESTPLAYER")
            .param("r", D.DOUBLE.min(0).def(16), "search radius in blocks")
            .doc("The single nearest player within r blocks, except the activator.")
            .example("@NearestPlayer{r=16}")
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
            if (!(e instanceof Player) || e.equals(ctx.actor())) {
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
