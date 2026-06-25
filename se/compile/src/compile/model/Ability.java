package compile.model;

/**
 * The source-erased, compiled unit of behavior all five content sources lower to (docs/architecture.md §4.1);
 * the source is the {@link #sourceKind} tag, not a subtype. Hot-path fields are primitives/interned ids/bitsets
 * so gates are integer compares, never string compares.
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

    public boolean firesOn(int triggerId) {
        return (triggerMask & (1 << triggerId)) != 0;
    }

    public boolean blockedInWorld(int worldId) {
        // worldId -1 (never blacklisted) must short-circuit: 1L << -1 wraps to bit 63 in Java.
        return worldId >= 0 && (worldBlacklist & (1L << worldId)) != 0;
    }
}
