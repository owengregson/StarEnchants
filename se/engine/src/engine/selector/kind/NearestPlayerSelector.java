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
 * {@code @NearestPlayer{r=16}} — closest player within {@code r} of the centre, except the activator
 * (Cosmic Enchants-style parity). A named alias for {@code @Nearest{filter=PLAYERS}}, clearer in content.
 *
 * <p>Allied players are skipped (R-QC17): this selector names the body a cage, a web or a strike lands on,
 * and the alliance predicate that spares a party-mate from the damage gate must spare them here too.
 */
public final class NearestPlayerSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("NEARESTPLAYER")
            .param("r", D.DOUBLE.min(0).def(16), "search radius in blocks")
            .param("allies", D.BOOL.def(false), "include allied players; the default skips them")
            .doc("The single nearest player within r blocks, except the activator and except allies.")
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
        boolean allies = ctx.args().bool("allies");
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (!(e instanceof Player player) || player.equals(ctx.actor())) {
                continue;
            }
            if (!allies && Allies.allied(ctx.actor(), player)) {
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
