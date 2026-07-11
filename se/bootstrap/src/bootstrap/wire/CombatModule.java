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

    /**
     * The Rage enchant's stable key base. Content keys are {@code <source>/<stem>} (LibraryLoader.keyTierOf), so
     * the per-level abilities are {@code enchants/rage/1}…{@code /N} — the prefix MUST carry the source segment
     * or {@link #rageLevel} silently resolves 0 and the whole stack system (fx, fact, damage) goes inert.
     */
    static final String RAGE_KEY = "enchants/rage";

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
        return rageLevel(ws.combatAttack(), aid -> snap.stableKeys().keyOf(aid), aid -> snap.abilities()[aid].level());
    }

    /** Key/level-function core of {@link #rageLevel(WornState, Snapshot)}, split out so the key-format contract is unit-testable. */
    static int rageLevel(int[] attackIds, java.util.function.IntFunction<String> keyOf,
                         java.util.function.IntUnaryOperator levelOf) {
        int best = 0;
        for (int aid : attackIds) {
            String key = keyOf.apply(aid);
            if (key != null && key.startsWith(RAGE_KEY + "/")) {
                best = Math.max(best, levelOf.applyAsInt(aid));
            }
        }
        return best;
    }
}
