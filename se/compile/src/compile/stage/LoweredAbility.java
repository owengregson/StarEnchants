package compile.stage;

import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import schema.diag.Source;
import java.util.List;

/**
 * An ability after {@link LowerStage} but before {@link EraseStage}: effects and condition compiled,
 * {@link #affinity} folded — but worlds, triggers, suppression and cooldown scopes are still
 * <em>names</em>, not interned ids, and no dense id is assigned (docs/architecture.md §4.1).
 *
 * @param level          enchant level; {@code 0} otherwise
 * @param baseChance     finite non-negative activation threshold; values at or above {@code 100} are guaranteed
 * @param cooldownTicks  cooldown to arm; {@code 0} = none
 * @param soulCost       souls consumed; {@code 0} = none
 * @param condition      compiled condition AST, or {@code null} for always-true
 * @param suppressKey    {@code DISABLE_*} key (interned by erasure), or {@code null}
 * @param repeatTicks    repeating-trigger period; {@code 0} = none
 * @param repeatInitialDelayTicks first-run delay; defaults to {@code repeatTicks} for legacy callers
 * @param affinity       affinity folded MAX over {@link #effects} and the soul-failure effects
 * @param setPieces      worn-piece count that completes a {@link SourceKind#SET} bonus; {@code 0} otherwise
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
        int repeatInitialDelayTicks,
        Affinity affinity,
        Source source,
        int setPieces,
        boolean suppressImmune,
        List<CompiledEffect> noSoulEffects) {

    public LoweredAbility {
        triggers = List.copyOf(triggers);
        worldBlacklist = List.copyOf(worldBlacklist);
        effects = List.copyOf(effects);
        noSoulEffects = List.copyOf(noSoulEffects);
    }

    /** Back-compat full construction with no soul-failure effects. */
    public LoweredAbility(SourceKind sourceKind, String stableKey, int defId, int level, double baseChance,
                          int cooldownTicks, int soulCost, List<String> triggers, List<String> worldBlacklist,
                          CompiledCondition condition, List<CompiledEffect> effects, String suppressKey,
                          String cdScopeEnchant, String cdScopeGroup, String cdScopeType, int repeatTicks,
                          int repeatInitialDelayTicks, Affinity affinity, Source source, int setPieces,
                          boolean suppressImmune) {
        this(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost, triggers, worldBlacklist,
                condition, effects, suppressKey, cdScopeEnchant, cdScopeGroup, cdScopeType, repeatTicks,
                repeatInitialDelayTicks, affinity, source, setPieces, suppressImmune, List.of());
    }

    /** Back-compat construction defaulting both first-run delay to {@code repeatTicks} and suppression immunity. */
    public LoweredAbility(SourceKind sourceKind, String stableKey, int defId, int level, double baseChance,
                          int cooldownTicks, int soulCost, List<String> triggers, List<String> worldBlacklist,
                          CompiledCondition condition, List<CompiledEffect> effects, String suppressKey,
                          String cdScopeEnchant, String cdScopeGroup, String cdScopeType, int repeatTicks,
                          Affinity affinity, Source source, int setPieces) {
        this(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost, triggers, worldBlacklist,
                condition, effects, suppressKey, cdScopeEnchant, cdScopeGroup, cdScopeType, repeatTicks,
                repeatTicks, affinity, source, setPieces, false, List.of());
    }

    /** Back-compat construction defaulting the first-run delay to {@code repeatTicks}. */
    public LoweredAbility(SourceKind sourceKind, String stableKey, int defId, int level, double baseChance,
                          int cooldownTicks, int soulCost, List<String> triggers, List<String> worldBlacklist,
                          CompiledCondition condition, List<CompiledEffect> effects, String suppressKey,
                          String cdScopeEnchant, String cdScopeGroup, String cdScopeType, int repeatTicks,
                          Affinity affinity, Source source, int setPieces, boolean suppressImmune) {
        this(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost, triggers, worldBlacklist,
                condition, effects, suppressKey, cdScopeEnchant, cdScopeGroup, cdScopeType, repeatTicks,
                repeatTicks, affinity, source, setPieces, suppressImmune, List.of());
    }
}
