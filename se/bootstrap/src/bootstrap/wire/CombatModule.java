package bootstrap.wire;

import compile.model.Snapshot;
import feature.combat.CombatListener;
import feature.combat.RageStacksListener;
import feature.combat.RageStacksService;
import item.worn.WornState;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * Combat hot path (ADR-0047): the one listener that feeds attack/defense activations to the engine, plus the §3
 * rage-stacks feedback listener (MONITOR, after the dispatch has advanced the combo streak).
 */
final class CombatModule {

    /** The Rage enchant's base stable key; its per-level abilities are keyed {@code rage/1}…{@code rage/N}. */
    private static final String RAGE_KEY = "rage";

    private final BootCore core;
    private final RageStacksService rageStacks;

    CombatModule(BootCore core) {
        this.core = core;
        // rageLevelOf reads the attacker's active rage level from the SAME per-player worn resolution the combat
        // dispatch walks (the held weapon's rage sits in the attacker-direction ability list).
        Function<Player, Integer> rageLevelOf =
                player -> rageLevel(core.worn().get(player.getUniqueId()), core.content().snapshot());
        this.rageStacks = new RageStacksService(rageLevelOf, core.stores().combo(), core.stores().rageStacks(),
                core.messages(), core.sounds(), core.tick()::get);
    }

    FeatureModule module() {
        return FeatureModule.named("combat")
                .events(new CombatListener(core.dispatch()))
                .events(new RageStacksListener(rageStacks))
                .lang("combat", "rage")
                .build();
    }

    /** The highest active rage level among the attacker-direction abilities of {@code ws}, or 0 (no rage / no state). */
    private static int rageLevel(WornState ws, Snapshot snap) {
        if (ws == null) {
            return 0;
        }
        int best = 0;
        for (int aid : ws.combatAttack()) {
            String key = snap.stableKeys().keyOf(aid);
            if (key != null && key.startsWith(RAGE_KEY + "/")) {
                best = Math.max(best, snap.abilities()[aid].level());
            }
        }
        return best;
    }
}
