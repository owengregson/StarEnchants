package compile.def;

import compile.model.SourceKind;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.List;

/**
 * One authored ability in its pre-compilation form — the uniform input the compiler erases into an
 * {@link compile.model.Ability} (docs/architecture.md §4.1). Source erasure starts here: all five sources
 * (enchant / set / weapon / crystal / heroic) load into this one shape tagged with {@link #sourceKind},
 * rather than five parallel hierarchies. Fields are authored-text-shaped (effect lines lexed not validated,
 * names not interned) until the lower and erase stages type, intern, and id-assign them.
 *
 * @param stableKey       reorder-proof identity stored in PDC (§4.2, §5.3); unique per snapshot
 * @param defId           stable authoring id for the {@link compile.model.SourceMap}
 * @param level           enchant level; {@code 0} for non-enchant sources
 * @param baseChance      activation chance in {@code [0,100)}
 * @param cooldownTicks   cooldown to arm on activation; {@code 0} = none
 * @param soulCost        souls consumed on activation; {@code 0} = none
 * @param conditionExpr   raw condition expression, or {@code null}/blank for "always true"
 * @param effects         lexed effect lines in authored order ({@code WAIT:n} lines control timing)
 * @param suppressKey     the key by which a {@code DISABLE_*} cancels this ability (§6.2), or {@code null}
 * @param repeatTicks     period for a repeating-trigger ability; {@code 0} = none
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
