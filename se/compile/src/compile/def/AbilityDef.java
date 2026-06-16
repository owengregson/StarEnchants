package compile.def;

import compile.model.SourceKind;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.List;

/**
 * One authored ability, in its <em>pre-compilation</em> form — the uniform input the
 * compiler erases into an {@link compile.model.Ability} (docs/architecture.md §4.1).
 *
 * <p>Source erasure is realized here: rather than five parallel def hierarchies
 * (enchant / set / weapon / crystal / heroic), every source is loaded into this one
 * shape tagged with its {@link #sourceKind}. Fields are still authored-text-shaped —
 * effect lines are lexed {@link EffectLine}s (not yet validated against a
 * {@code ParamSpec}), the condition is a raw expression string, and worlds / triggers
 * / suppression / cooldown scopes are names (not yet interned). The lower and erase
 * stages turn those into typed, interned, id-assigned runtime data.
 *
 * <p>The YAML loader produces these; unit tests build them by hand.
 *
 * @param sourceKind      which source this came from
 * @param stableKey       the reorder-proof identity stored in PDC (§4.2, §5.3); unique per snapshot
 * @param defId           a stable authoring id used for the {@link compile.model.SourceMap}
 * @param level           enchant level; {@code 0} for non-enchant sources
 * @param baseChance      activation chance in {@code [0,100)}
 * @param cooldownTicks   cooldown to arm on activation; {@code 0} = none
 * @param soulCost        souls consumed on activation; {@code 0} = none
 * @param triggers        trigger names this ability fires on
 * @param worldBlacklist  world names in which this ability is disabled
 * @param conditionExpr   raw condition expression, or {@code null}/blank for "always true"
 * @param effects         lexed effect lines in authored order ({@code WAIT:n} lines control timing)
 * @param suppressKey     the key by which a {@code DISABLE_*} cancels this ability (§6.2), or {@code null}
 * @param cdScopeEnchant  cooldown-scope name (enchant scope), or {@code null}
 * @param cdScopeGroup    cooldown-scope name (group scope), or {@code null}
 * @param cdScopeType     cooldown-scope name (type scope), or {@code null}
 * @param repeatTicks     period for a repeating-trigger ability; {@code 0} = none
 * @param source          where this ability was authored, for diagnostics
 * @param setPieces       for a {@link SourceKind#SET} bonus, the worn-piece count that completes the
 *                        set; {@code 0} for every non-set source
 */
public record AbilityDef(
        SourceKind sourceKind,
        String stableKey,
        int defId,
        int level,
        double baseChance,
        int cooldownTicks,
        int soulCost,
        List<String> triggers,
        List<String> worldBlacklist,
        String conditionExpr,
        List<EffectLine> effects,
        String suppressKey,
        String cdScopeEnchant,
        String cdScopeGroup,
        String cdScopeType,
        int repeatTicks,
        Source source,
        int setPieces) {

    public AbilityDef {
        triggers = List.copyOf(triggers);
        worldBlacklist = List.copyOf(worldBlacklist);
        effects = List.copyOf(effects);
    }
}
