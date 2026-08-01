package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored enchant (ADR-0014); the runtime {@code Snapshot} carries only the
 * {@code AbilityDef}s it expands into. The relationship fields gate player apply paths (book/menu/carrier)
 * but never admin force-give (docs/v3-directives.md §G).
 *
 * @param tier            rarity tier (ADR-0016); may be {@code null}
 * @param appliesTo       named item target groups, not raw materials
 * @param requires        prerequisite enchant keys, each present at a level &ge; this one's (§G)
 * @param blacklist       enchant keys this one cannot coexist with — bidirectional at apply (§G)
 * @param removesRequired whether a successful apply removes all {@code requires} (net-zero slots, §G)
 * @param stacking        how duplicate equipped copies resolve: HIGHEST runs one highest-level copy, EACH preserves multiplicity
 */
public record EnchantDef(
        String key,
        String display,
        String description,
        String tier,
        List<String> appliesTo,
        int maxLevel,
        List<String> requires,
        List<String> blacklist,
        boolean removesRequired,
        Stacking stacking,
        Source source) {

    public enum Stacking {
        HIGHEST,
        EACH;

        static Stacking parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return EACH;
            }
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public EnchantDef {
        appliesTo = List.copyOf(appliesTo);
        requires = List.copyOf(requires);
        blacklist = List.copyOf(blacklist);
        stacking = stacking == null ? Stacking.EACH : stacking;
    }

    /** Compatibility shape for catalogs/tests authored before explicit stacking existed. */
    public EnchantDef(String key, String display, String description, String tier, List<String> appliesTo,
                      int maxLevel, List<String> requires, List<String> blacklist, boolean removesRequired,
                      Source source) {
        this(key, display, description, tier, appliesTo, maxLevel, requires, blacklist, removesRequired,
                Stacking.EACH, source);
    }

    public boolean stackable() {
        return stacking == Stacking.EACH;
    }
}
