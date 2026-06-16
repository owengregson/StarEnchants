package compile.model;

/**
 * The source-erased, compiled unit of behavior — the central data structure of the
 * whole engine (docs/architecture.md §4.1). All five content sources lower to this
 * one immutable record; "this came from a crystal" is the {@link #sourceKind} tag
 * on an otherwise-identical struct. There is deliberately no {@code Effect} class
 * hierarchy: uniform handling is the only thing representable.
 *
 * <p>Everything the hot path touches is a primitive, an interned id, or a bitset, so
 * gates are integer/bitset comparisons rather than string compares
 * (docs/architecture.md §3.3). The architecture spec types several of these fields
 * as {@code byte}/{@code short} for density; we use {@code int} for ergonomics — the
 * memory cost is negligible at catalog scale (thousands of abilities, not millions),
 * and §8 makes steady-state allocation, not field width, the bench-gated contract.
 *
 * @param id             dense per-snapshot array index (NOT persisted; items resolve by stable key, §5.3)
 * @param defId          back-reference into the {@link SourceMap} for op-visible diagnostics
 * @param sourceKind     which source this was erased from (a tag, §4.1)
 * @param triggerMask    bitset of interned trigger ids: fires on trigger {@code t} iff {@code (triggerMask & (1<<t)) != 0}
 * @param level          enchant level; {@code 0} for non-enchant sources
 * @param baseChance     activation chance, normalized to {@code [0,100)} (fixes the {@code nextDouble(100)+1} quirk)
 * @param cooldownTicks  cooldown to arm on activation; {@code 0} = none
 * @param soulCost       souls consumed at gate 10; {@code 0} = none
 * @param worldBlacklist interned world-id bitset; an ability is blocked in world {@code w} iff {@code (worldBlacklist & (1L<<w)) != 0}; {@code 0L} = allowed everywhere
 * @param condition      pre-built condition AST; {@code null} = always true
 * @param effects        the flyweight effects to run, in authored order
 * @param repeatTicks    period for a repeating-trigger ability; {@code 0} = none
 * @param affinity       dispatch affinity folded MAX over {@link #effects} (§3.6)
 * @param cdScopeEnchant interned cooldown-scope id (enchant scope), or {@code -1}
 * @param cdScopeGroup   interned cooldown-scope id (group scope), or {@code -1}
 * @param cdScopeType    interned cooldown-scope id (type scope), or {@code -1}
 * @param suppressKey    interned key (enchant id | group id | type) by which a {@code DISABLE_*} cancels this ability (§6.2), or {@code -1}
 * @param setPieces      for a {@link SourceKind#SET} bonus, the worn-piece count that completes the set (§6.6); {@code 0} for every non-set source
 */
public record Ability(
        int id,
        int defId,
        SourceKind sourceKind,
        int triggerMask,
        int level,
        double baseChance,
        int cooldownTicks,
        int soulCost,
        long worldBlacklist,
        CompiledCondition condition,
        CompiledEffect[] effects,
        int repeatTicks,
        Affinity affinity,
        int cdScopeEnchant,
        int cdScopeGroup,
        int cdScopeType,
        int suppressKey,
        int setPieces) {

    /** @return {@code true} if this ability fires on the interned trigger id {@code triggerId}. */
    public boolean firesOn(int triggerId) {
        return (triggerMask & (1 << triggerId)) != 0;
    }

    /** @return {@code true} if this ability is blocked in the interned world id {@code worldId}. */
    public boolean blockedInWorld(int worldId) {
        // A world named in no blacklist interns to -1 at runtime — it is blocked by no ability.
        // Guarding it also avoids the undefined {@code 1L << -1} (which Java masks to bit 63).
        return worldId >= 0 && (worldBlacklist & (1L << worldId)) != 0;
    }
}
