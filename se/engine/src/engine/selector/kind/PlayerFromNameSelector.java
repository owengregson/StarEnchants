package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;
import java.util.List;

/**
 * {@code @PlayerFromName{name=Steve}} — the named online player, or an empty result if no player by that
 * exact name is online (docs/architecture.md §7; v3.1 §A, Cosmic Enchants-style parity). The roster lookup goes through the
 * injected world-access seam (so the kind stays pure and unit-testable); the effect that acts on the
 * returned player routes to that player's region thread via its own affinity.
 */
public final class PlayerFromNameSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("PLAYERFROMNAME")
            .param("name", D.STRING, "the exact name of the online player to target")
            .doc("The online player with the given exact name, or nothing if they are not online.")
            .example("@PlayerFromName{name=Steve}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        Player player = ctx.playerByName(ctx.args().str("name"));
        return player == null ? List.of() : List.<LivingEntity>of(player);
    }
}
