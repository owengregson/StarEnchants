package compile.model;

import compile.model.cond.NumExpr;

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
 * @param repeatDelayTicks ticks before the FIRST run; {@code -1} = one full period (R-QC35b)
 * @param affinity       dispatch affinity folded MAX over {@link #effects} (§3.6)
 * @param cdScopeEnchant interned cooldown-scope id (enchant scope), or {@code -1}
 * @param cdScopeGroup   interned cooldown-scope id (group scope), or {@code -1} — the FAMILY match key a
 *                       {@code SUPPRESS}/{@code SUPPRESS_INCOMING} {@code scope: GROUP} window matches on
 * @param sourceGroup    interned IMPACT source-scope id (ADR-0074 §4, amended): which deferred payloads a cast
 *                       armed by this ability may fire, defaulting to {@link #cdScopeGroup} and narrowed by a
 *                       per-ability {@code group:} (R-QC40). SEPARATE from the match key on purpose — narrowing
 *                       a payload's scope must not drop its enchant out of a family-wide negation
 * @param cdScopeType    interned cooldown-scope id (type scope), or {@code -1}
 * @param suppressKey    interned key (enchant id | group id | type) by which a {@code DISABLE_*} cancels this ability (§6.2), or {@code -1}
 * @param setPieces      for a {@link SourceKind#SET} bonus, the worn-piece count that completes the set (§6.6); {@code 0} for every non-set source
 * @param suppressImmune when {@code true} this ability can never be suppressed (DISABLE_ENCHANT/GROUP/TYPE/KIND no-op against it), so a permanent buff survives Silence &amp; its derivatives while the wearer's OTHER enchants are still silenced (per-enchant {@code suppress-immune: true})
 * @param factMask       the {@code FactBuffer} slots this ability reads (ADR-0039), unioned per trigger in the {@code WornState} so the populator computes only referenced facts; {@link FactMask#ALL} for hand-built abilities (populate everything)
 * @param chanceExpr     evaluated at the chance gate in place of {@link #baseChance} and clamped to {@code [0,100]}; {@code null} for a constant chance, so the hot path pays one null check
 * @param noSoulsMessage line shown to the actor when gate 10 aborts because {@link #soulCost} cannot be paid; {@code null}/blank = none
 * @param soulCostCarried whether gate 10 may charge {@link #soulCost} against the actor's CARRIED gems with no gem active ({@code soul-cost-carried: true}); {@code false} = the default rule, where a soul-cost ability never fires outside soul mode
 * @param noSoulsSound   interned sound id played with {@link #noSoulsMessage} on that abort; {@code -1} = none
 * @param noSoulsParticle interned particle id spawned with {@link #noSoulsMessage} on that abort; {@code -1} = none
 * @param soulCostGrowth factor {@link #soulCost} compounds by per successful charge; {@code 1.0} = static, and the hot path short-circuits on it
 * @param soulCostCap    ceiling on the escalated cost; {@code 0} = uncapped
 * @param soulCostDecayPeriod ticks per escalation step shed since the actor's last charge of THIS ability; {@code 0} = never decays
 * @param cooldownPerVictim when {@code true} gate 6 keys the cooldown on the activation's victim rather than the coarse player/mob target bucket, so one target's window never throttles a strike on another ({@code cooldown-per-victim: true}); {@code false} = today's shared bucket
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
        int setPieces,
        boolean suppressImmune,
        FactMask factMask,
        NumExpr chanceExpr,
        String noSoulsMessage,
        boolean soulCostCarried,
        int noSoulsSound,
        int noSoulsParticle,
        double soulCostGrowth,
        int soulCostCap,
        int soulCostDecayPeriod,
        boolean cooldownPerVictim,
        int repeatDelayTicks,
        int sourceGroup) {

    /** Construction where the IMPACT source group IS the family group — every ability with no per-ability override. */
    public Ability(int id, int defId, SourceKind sourceKind, int triggerMask, int level, double baseChance,
                   int cooldownTicks, int soulCost, long worldBlacklist, CompiledCondition condition,
                   CompiledEffect[] effects, int repeatTicks, Affinity affinity, int cdScopeEnchant,
                   int cdScopeGroup, int cdScopeType, int suppressKey, int setPieces, boolean suppressImmune,
                   FactMask factMask, NumExpr chanceExpr, String noSoulsMessage, boolean soulCostCarried,
                   int noSoulsSound, int noSoulsParticle, double soulCostGrowth, int soulCostCap,
                   int soulCostDecayPeriod, boolean cooldownPerVictim, int repeatDelayTicks) {
        this(id, defId, sourceKind, triggerMask, level, baseChance, cooldownTicks, soulCost, worldBlacklist,
                condition, effects, repeatTicks, affinity, cdScopeEnchant, cdScopeGroup, cdScopeType,
                suppressKey, setPieces, suppressImmune, factMask, chanceExpr, noSoulsMessage, soulCostCarried,
                noSoulsSound, noSoulsParticle, soulCostGrowth, soulCostCap, soulCostDecayPeriod,
                cooldownPerVictim, repeatDelayTicks, cdScopeGroup);
    }

    /** Back-compat construction for a constant {@code chance:} — the hot-path fast case. */
    public Ability(int id, int defId, SourceKind sourceKind, int triggerMask, int level, double baseChance,
                   int cooldownTicks, int soulCost, long worldBlacklist, CompiledCondition condition,
                   CompiledEffect[] effects, int repeatTicks, Affinity affinity, int cdScopeEnchant,
                   int cdScopeGroup, int cdScopeType, int suppressKey, int setPieces, boolean suppressImmune,
                   FactMask factMask) {
        this(id, defId, sourceKind, triggerMask, level, baseChance, cooldownTicks, soulCost, worldBlacklist,
                condition, effects, repeatTicks, affinity, cdScopeEnchant, cdScopeGroup, cdScopeType,
                suppressKey, setPieces, suppressImmune, factMask, null, null, false, -1, -1, 1.0, 0, 0, false,
                -1, cdScopeGroup);
    }

    /** No derived fact mask — populate everything (the safe default for hand-built test abilities). */
    public Ability(int id, int defId, SourceKind sourceKind, int triggerMask, int level, double baseChance,
                   int cooldownTicks, int soulCost, long worldBlacklist, CompiledCondition condition,
                   CompiledEffect[] effects, int repeatTicks, Affinity affinity, int cdScopeEnchant,
                   int cdScopeGroup, int cdScopeType, int suppressKey, int setPieces) {
        this(id, defId, sourceKind, triggerMask, level, baseChance, cooldownTicks, soulCost, worldBlacklist,
                condition, effects, repeatTicks, affinity, cdScopeEnchant, cdScopeGroup, cdScopeType,
                suppressKey, setPieces, false, FactMask.ALL, null, null, false, -1, -1, 1.0, 0, 0, false, -1,
                cdScopeGroup);
    }

    public boolean firesOn(int triggerId) {
        return (triggerMask & (1 << triggerId)) != 0;
    }

    public boolean blockedInWorld(int worldId) {
        // worldId -1 (never blacklisted) must short-circuit: 1L << -1 wraps to bit 63 in Java.
        return worldId >= 0 && (worldBlacklist & (1L << worldId)) != 0;
    }
}
