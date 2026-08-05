package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code @AllPlayers{r=32}} — every player within {@code r} of the centre, except the activator
 * (Cosmic Enchants-style parity). A radius scan, not the server roster: server-wide would need cross-region
 * hops the selector contract forbids (Folia: the scan runs on the centre's region thread).
 *
 * <p>Allied players are skipped (R-QC17), the same alliance predicate every other targeting surface reads —
 * a set aura or a field must not land on a party-mate. {@code allies: true} takes them back, for the one
 * shape that is an AUDIENCE rather than a target: a broadcast is meant to reach the whole area.
 */
public final class AllPlayersSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("ALLPLAYERS")
            .param("r", D.DOUBLE.min(0).def(32), "search radius in blocks")
            .param("allies", D.BOOL.def(false),
                    "include allied players — set it for a broadcast AUDIENCE; a target list wants the default")
            .doc("Every player within r blocks of the target, except the activator and except allies. "
                    + "allies: true takes allied players back, for a broadcast audience rather than a target list.")
            .example("@AllPlayers{r=32}")
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
        List<LivingEntity> players = new ArrayList<>();
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (e instanceof Player player && !player.equals(ctx.actor())
                    && (allies || !Allies.allied(ctx.actor(), player))) {
                players.add(player);
            }
        }
        return players;
    }
}
