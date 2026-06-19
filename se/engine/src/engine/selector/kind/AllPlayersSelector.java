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
 * {@code @AllPlayers{r=32}} — every player within {@code r} blocks of the activation centre, excluding the
 * activator (docs/architecture.md §7; v3.1 §A, AE parity). Implemented as a player-filtered region scan
 * over the injected area-scan seam so it stays Folia-correct (the scan runs on the centre's region thread)
 * and never reaches the whole server roster — a server-wide broadcast would need cross-region hops the
 * selector contract does not provide. The radius defaults to {@code 32} so the no-argument form is valid.
 */
public final class AllPlayersSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("ALLPLAYERS")
            .param("r", D.DOUBLE.min(0).def(32), "search radius in blocks")
            .doc("Every player within r blocks of the target, except the activator.")
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
        List<LivingEntity> players = new ArrayList<>();
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (e instanceof Player && !e.equals(ctx.actor())) {
                players.add(e);
            }
        }
        return players;
    }
}
