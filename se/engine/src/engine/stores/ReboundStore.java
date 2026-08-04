package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The armed {@code PROC_REBOUND} rules (Enchant Reflect): a worn marker the combat dispatch consults when
 * someone hits the wearer, armed on equip and lifted on unequip by the HELD/PASSIVE lifecycle (ADR-0022).
 *
 * <p>ONE rule per (player, defId), so three worn grades are three entries and growth is bounded by the number
 * of authored {@code PROC_REBOUND} defs — never by hits or by worn pieces.
 *
 * <p>Self-derived worn state, so it is swept VOLATILE on quit: the equip walk re-arms it on relog, which makes
 * retention state that can only go wrong. Hot-path package rules apply (concurrent map only).
 */
public final class ReboundStore implements PlayerScoped {

    /**
     * One armed grade. {@code tierMin}/{@code tierMax} bound the INCOMING enchant's rarity-tier weight this
     * grade answers for; {@code level} is the rebound ability's own level (the incoming enchant's level may
     * not exceed it); {@code chancePercent} is rolled per claimed activation.
     */
    public record Rule(int defId, int level, double chancePercent, int tierMin, int tierMax) {

        /** Whether an incoming enchant of rarity weight {@code tier} falls in this grade's band. */
        public boolean covers(int tier) {
            return tier >= tierMin && tier <= tierMax;
        }
    }

    private final Map<UUID, Map<Integer, Rule>> armed = new ConcurrentHashMap<>();

    /** Arm (or replace) {@code holder}'s grade for {@code defId}. A non-positive chance arms nothing. */
    public void arm(UUID holder, int defId, int level, double chancePercent, int tierMin, int tierMax) {
        if (holder == null || chancePercent <= 0 || tierMax < tierMin) {
            return;
        }
        armed.computeIfAbsent(holder, id -> new ConcurrentHashMap<>())
                .put(defId, new Rule(defId, level, chancePercent, tierMin, tierMax));
    }

    /** Lift {@code holder}'s grade for {@code defId} (the unequip half); forgets the player once empty. */
    public void disarm(UUID holder, int defId) {
        if (holder == null) {
            return;
        }
        armed.computeIfPresent(holder, (id, grades) -> {
            grades.remove(defId);
            return grades.isEmpty() ? null : grades;
        });
    }

    /** Whether {@code player} has ANY grade armed — the one probe every hit on a player pays. */
    public boolean armed(UUID player) {
        return player != null && armed.containsKey(player);
    }

    /**
     * The grade that answers an incoming enchant of rarity weight {@code tier}, or {@code null}. Precedence is
     * the GREATEST {@code tierMin} among the bands containing {@code tier} — which is exactly the matrix's
     * exclusive chain "mastery (tier 8 only) → heroic (&le; 7) → normal (&le; 5), first match wins": the
     * narrowest band that reaches this tier is the highest grade authored for it, so a wearer carrying all
     * three uses exactly one branch even when a lower grade sits at a higher level.
     */
    public Rule strongestFor(UUID player, int tier) {
        Map<Integer, Rule> grades = player == null ? null : armed.get(player);
        if (grades == null) {
            return null;
        }
        Rule best = null;
        for (Rule rule : grades.values()) {
            if (rule.covers(tier) && (best == null || rule.tierMin() > best.tierMin())) {
                best = rule;
            }
        }
        return best;
    }

    @Override
    public void clear(UUID player) {
        armed.remove(player);
    }

    /** Forget every player's grades (call on disable). */
    public void clearAll() {
        armed.clear();
    }
}
