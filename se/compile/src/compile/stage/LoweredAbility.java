package compile.stage;

import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import compile.model.cond.NumExpr;
import schema.diag.Source;
import java.util.List;

/**
 * An ability after {@link LowerStage} but before {@link EraseStage}: effects and condition compiled,
 * {@link #affinity} folded — but worlds, triggers, suppression and cooldown scopes are still
 * <em>names</em>, not interned ids, and no dense id is assigned (docs/architecture.md §4.1).
 *
 * @param level          enchant level; {@code 0} otherwise
 * @param baseChance     activation chance in {@code [0,100)}
 * @param cooldownTicks  cooldown to arm; {@code 0} = none
 * @param soulCost       souls consumed; {@code 0} = none
 * @param condition      compiled condition AST, or {@code null} for always-true
 * @param suppressKey    {@code DISABLE_*} key (interned by erasure), or {@code null}
 * @param repeatTicks    repeating-trigger period; {@code 0} = none
 * @param affinity       affinity folded MAX over {@link #effects} (CONTEXT_LOCAL if none)
 * @param setPieces      worn-piece count that completes a {@link SourceKind#SET} bonus; {@code 0} otherwise
 * @param chanceExpr     evaluated at the chance gate instead of {@link #baseChance}; {@code null} = constant
 * @param noSoulsMessage line shown to the actor when {@link #soulCost} cannot be paid; {@code null}/blank = none
 * @param soulCostCarried whether {@link #soulCost} may be charged against the actor's CARRIED gems with no gem active
 * @param noSoulsSound    interned sound id played with {@link #noSoulsMessage}; {@code -1} = none
 * @param noSoulsParticle interned particle id spawned with {@link #noSoulsMessage}; {@code -1} = none
 */
public record LoweredAbility(
        SourceKind sourceKind,
        String stableKey,
        int defId,
        int level,
        double baseChance,
        int cooldownTicks,
        int soulCost,
        List<String> triggers,
        List<String> worldBlacklist,
        CompiledCondition condition,
        List<CompiledEffect> effects,
        String suppressKey,
        String cdScopeEnchant,
        String cdScopeGroup,
        String cdScopeType,
        int repeatTicks,
        Affinity affinity,
        Source source,
        int setPieces,
        boolean suppressImmune,
        NumExpr chanceExpr,
        String noSoulsMessage,
        boolean soulCostCarried,
        int noSoulsSound,
        int noSoulsParticle) {

    public LoweredAbility {
        triggers = List.copyOf(triggers);
        worldBlacklist = List.copyOf(worldBlacklist);
        effects = List.copyOf(effects);
    }

    /** Back-compat construction for a constant {@code chance:} — the overwhelmingly common case. */
    public LoweredAbility(SourceKind sourceKind, String stableKey, int defId, int level, double baseChance,
                          int cooldownTicks, int soulCost, List<String> triggers, List<String> worldBlacklist,
                          CompiledCondition condition, List<CompiledEffect> effects, String suppressKey,
                          String cdScopeEnchant, String cdScopeGroup, String cdScopeType, int repeatTicks,
                          Affinity affinity, Source source, int setPieces, boolean suppressImmune) {
        this(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost, triggers, worldBlacklist,
                condition, effects, suppressKey, cdScopeEnchant, cdScopeGroup, cdScopeType, repeatTicks, affinity,
                source, setPieces, suppressImmune, null, null, false, -1, -1);
    }

    /** Back-compat construction defaulting {@code suppressImmune=false} — most callers never set it. */
    public LoweredAbility(SourceKind sourceKind, String stableKey, int defId, int level, double baseChance,
                          int cooldownTicks, int soulCost, List<String> triggers, List<String> worldBlacklist,
                          CompiledCondition condition, List<CompiledEffect> effects, String suppressKey,
                          String cdScopeEnchant, String cdScopeGroup, String cdScopeType, int repeatTicks,
                          Affinity affinity, Source source, int setPieces) {
        this(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost, triggers, worldBlacklist,
                condition, effects, suppressKey, cdScopeEnchant, cdScopeGroup, cdScopeType, repeatTicks, affinity,
                source, setPieces, false, null, null, false, -1, -1);
    }
}
