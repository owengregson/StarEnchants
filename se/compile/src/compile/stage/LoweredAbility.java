package compile.stage;

import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import schema.diag.Source;
import java.util.List;

/**
 * An ability after {@link LowerStage} but before {@link EraseStage}: its effect
 * lines are compiled to flyweight {@link CompiledEffect}s, its condition to a
 * {@link CompiledCondition}, and its {@link #affinity} folded — but worlds,
 * triggers, suppression and cooldown scopes are still <em>names</em>, not interned
 * ids, and no dense {@link compile.model.Ability#id()} has been assigned yet
 * (docs/architecture.md §4.1). Erasure does the interning, bit-packing and id
 * assignment that turn this into the final {@code Ability}.
 *
 * @param sourceKind     which source this came from
 * @param stableKey      reorder-proof identity (§5.3)
 * @param defId          authoring id for the source map
 * @param level          enchant level; {@code 0} otherwise
 * @param baseChance     activation chance in {@code [0,100)}
 * @param cooldownTicks  cooldown to arm; {@code 0} = none
 * @param soulCost       souls consumed; {@code 0} = none
 * @param triggers       trigger names (interned by erasure into the trigger mask)
 * @param worldBlacklist world names (interned by erasure into the world bitset)
 * @param condition      compiled condition AST, or {@code null} for always-true
 * @param effects        compiled effects in run order, each carrying its cumulative WAIT
 * @param suppressKey    {@code DISABLE_*} key (interned by erasure), or {@code null}
 * @param cdScopeEnchant cooldown-scope name (enchant), or {@code null}
 * @param cdScopeGroup   cooldown-scope name (group), or {@code null}
 * @param cdScopeType    cooldown-scope name (type), or {@code null}
 * @param repeatTicks    repeating-trigger period; {@code 0} = none
 * @param affinity       affinity folded MAX over {@link #effects} (CONTEXT_LOCAL if none)
 * @param source         authored origin, for diagnostics
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
        Affinity affinity,
        Source source,
        int setPieces) {

    public LoweredAbility {
        triggers = List.copyOf(triggers);
        worldBlacklist = List.copyOf(worldBlacklist);
        effects = List.copyOf(effects);
    }
}
