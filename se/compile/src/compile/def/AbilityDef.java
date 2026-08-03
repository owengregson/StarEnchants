package compile.def;

import compile.model.SourceKind;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.List;

/**
 * One authored ability in its pre-compilation form — the uniform input the compiler erases into an
 * {@link compile.model.Ability} (docs/architecture.md §4.1). Source erasure starts here: every source
 * (enchant / set / weapon / crystal / heroic / use-item) loads into this one shape tagged with
 * {@link #sourceKind}, rather than one hierarchy per source. Fields are authored-text-shaped (effect lines
 * lexed not validated, names not interned) until the lower and erase stages type, intern, and id-assign them.
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
 * @param chanceExpr      raw expression text when {@code chance:} was authored as an expression rather than
 *                        a number, else {@code null}; lowered to a {@code NumExpr} like {@link #conditionExpr}
 * @param noSoulsMessage  line shown to the actor when {@link #soulCost} cannot be paid; {@code null}/blank = none
 * @param soulCostCarried whether {@link #soulCost} may be charged against the actor's CARRIED gems with no gem
 *                        active; {@code false} = the default active-gem-only rule
 * @param noSoulsSound    sound token played alongside {@link #noSoulsMessage}; {@code null}/blank = none
 * @param noSoulsParticle particle token spawned alongside {@link #noSoulsMessage}; {@code null}/blank = none
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
        int setPieces,
        boolean suppressImmune,
        String chanceExpr,
        String noSoulsMessage,
        boolean soulCostCarried,
        String noSoulsSound,
        String noSoulsParticle) {

    public AbilityDef {
        triggers = List.copyOf(triggers);
        worldBlacklist = List.copyOf(worldBlacklist);
        effects = List.copyOf(effects);
    }

    /** Back-compat construction for a constant {@code chance:} — the overwhelmingly common case. */
    public AbilityDef(SourceKind sourceKind, String stableKey, int defId, int level, double baseChance,
                      int cooldownTicks, int soulCost, List<String> triggers, List<String> worldBlacklist,
                      String conditionExpr, List<EffectLine> effects, String suppressKey, String cdScopeEnchant,
                      String cdScopeGroup, String cdScopeType, int repeatTicks, Source source, int setPieces,
                      boolean suppressImmune) {
        this(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost, triggers, worldBlacklist,
                conditionExpr, effects, suppressKey, cdScopeEnchant, cdScopeGroup, cdScopeType, repeatTicks,
                source, setPieces, suppressImmune, null, null, false, null, null);
    }

    /** Back-compat construction defaulting {@code suppressImmune=false} — only enchants author it today. */
    public AbilityDef(SourceKind sourceKind, String stableKey, int defId, int level, double baseChance,
                      int cooldownTicks, int soulCost, List<String> triggers, List<String> worldBlacklist,
                      String conditionExpr, List<EffectLine> effects, String suppressKey, String cdScopeEnchant,
                      String cdScopeGroup, String cdScopeType, int repeatTicks, Source source, int setPieces) {
        this(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost, triggers, worldBlacklist,
                conditionExpr, effects, suppressKey, cdScopeEnchant, cdScopeGroup, cdScopeType, repeatTicks,
                source, setPieces, false, null, null, false, null, null);
    }
}
