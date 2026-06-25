package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * Parsed, non-runtime metadata of one authored enchant (docs/architecture.md §4.2; ADR-0014). Retained in
 * the {@link Library} catalog for render/apply cycles; the runtime {@code Snapshot} carries only the
 * {@code AbilityDef}s this enchant expands into. Immutable.
 *
 * <p>The relationship fields are the general apply-time mechanism of docs/v3-directives.md §G — they gate
 * player apply paths (book/menu/carrier) but never admin force-give. See per-{@code @param} contracts below.
 *
 * @param key        the path-derived base key (e.g. {@code enchants/lifesteal})
 * @param description short description for {@code /se docs} + lore; never {@code null} (empty if absent)
 * @param tier       rarity tier (ADR-0016) for lore colour/glint/GUI sort; may be {@code null}
 * @param appliesTo  item target groups this enchant may sit on (named groups, not raw materials)
 * @param maxLevel   highest level offered (defaults to the highest declared level)
 * @param requires   prerequisite enchant base keys that must be present at a level &ge; this one's (§G)
 * @param blacklist  enchant base keys this one cannot coexist with — bidirectional at apply (§G)
 * @param removesRequired whether a successful apply removes all {@code requires} (net-zero slots, §G)
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
        Source source) {

    public EnchantDef {
        appliesTo = List.copyOf(appliesTo);
        requires = List.copyOf(requires);
        blacklist = List.copyOf(blacklist);
    }
}
