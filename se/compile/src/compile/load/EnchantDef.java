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
