package bootstrap.wire;

import compile.model.Snapshot;
import feature.combat.CombatListener;
import feature.combat.ComboDotSyncListener;
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
     * or {@link #rageAbilityId} silently resolves nothing and the whole stack system (fx, fact, damage) goes inert.
     */
    static final String RAGE_KEY = "enchants/rage";

    private final BootCore core;
    private final RageStacksService rageStacks;

    CombatModule(BootCore core) {
        this.core = core;
        // rageLevelOf reads the attacker's active rage level from the SAME per-player worn resolution the combat
        // dispatch walks (the held weapon's rage sits in the attacker-direction ability list), gated by the SAME
        // suppression window gate 5 applies to the rage ability: feedback must equal effect — a Silence/Solitude
        // window that zeroes the rage DAMAGE_MOD must freeze the stack readout too, or the action bar climbs
        // while the fold reads nothing (ADR-0050 R4).
        Function<Player, Integer> rageLevelOf = player -> {
            Snapshot snap = core.content().snapshot();
            int aid = rageAbilityId(core.worn().get(player.getUniqueId()), snap);
            if (aid < 0) {
                return 0;
            }
            if (core.stores().suppression().suppressesAny(snap.abilities()[aid], player.getUniqueId(),
                    core.tick().get())) {
                return 0;
            }
            return snap.abilities()[aid].level();
        };
        this.rageStacks = new RageStacksService(rageLevelOf, core.stores().combo(), core.stores().rageStacks(),
                core.messages(), core.sounds(), core.tick()::get);
    }

    FeatureModule module() {
        return FeatureModule.named("combat")
                .events(new CombatListener(core.dispatch()))
                .events(new RageStacksListener(rageStacks))
                // ADR-0069 combo-DoT sync: MONITOR confirm (re-park a cancelled flush) + death clear; the park
                // ledger joins the structural quit sweep as a module player store (clear drops buckets, combo
                // state AND the release in-flight flag — the Folia mid-release-quit cleanup path).
                .events(new ComboDotSyncListener(core.sinkEnv().dotPark()))
                // VANISH: the two halves the sink cannot own — the landed-hit break and the join re-sync. The
                // era seam is asked for a second instance; it is stateless, and both hide against the SAME
                // plugin handle, so the sink's hide and this one's re-sync share one hidden set.
                .events(new feature.combat.VanishListener(core.stores().vanish(),
                        core.bindings().playerVisibility(core.plugin()), core.tick()::get))
                .store(core.sinkEnv().dotPark())
                .lang("combat", "rage")
                .build();
    }

    /** The highest-level active rage ability among the attacker-direction abilities of {@code ws}, or {@code -1}. */
    private static int rageAbilityId(WornState ws, Snapshot snap) {
        if (ws == null) {
            return -1;
        }
        return rageAbilityId(ws.combatAttack(), aid -> snap.stableKeys().keyOf(aid),
                aid -> snap.abilities()[aid].level());
    }

    /** Key/level-function core of {@link #rageAbilityId(WornState, Snapshot)}, split out so the key-format contract is unit-testable. */
    static int rageAbilityId(int[] attackIds, java.util.function.IntFunction<String> keyOf,
                             java.util.function.IntUnaryOperator levelOf) {
        int best = -1;
        int bestLevel = 0;
        for (int aid : attackIds) {
            String key = keyOf.apply(aid);
            if (key != null && key.startsWith(RAGE_KEY + "/")) {
                int level = levelOf.applyAsInt(aid);
                if (level > bestLevel) {
                    bestLevel = level;
                    best = aid;
                }
            }
        }
        return best;
    }

    /** The highest active rage LEVEL for {@code attackIds} — the pre-R4 contract, kept for the key-format test. */
    static int rageLevel(int[] attackIds, java.util.function.IntFunction<String> keyOf,
                         java.util.function.IntUnaryOperator levelOf) {
        int aid = rageAbilityId(attackIds, keyOf, levelOf);
        return aid < 0 ? 0 : levelOf.applyAsInt(aid);
    }
}
