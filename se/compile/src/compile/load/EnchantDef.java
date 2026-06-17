package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * The parsed, non-runtime metadata of one authored enchant (docs/architecture.md §4.2; ADR-0014):
 * its key, display name, description, the item target groups it applies to, and its level range.
 * Retained in the {@link Library} catalog for the later render / apply cycles; the runtime
 * {@code Snapshot} carries only the {@code AbilityDef}s this enchant expands into. Immutable.
 *
 * <p>The relationship fields ({@code requires} / {@code blacklist} / {@code removesRequired}) are the
 * general apply-time mechanism of docs/v3-directives.md §G (not "heroic-tier"-specific): {@code requires}
 * are prerequisite enchants that must already be present at a level &ge; this one's; {@code blacklist}
 * are enchants this one cannot coexist with (evaluated <em>bidirectionally</em> at apply); and when
 * {@code removesRequired} is set a successful apply removes all {@code requires} (the superior enchant
 * supersedes its prerequisites, so its net slot cost is zero). They gate player apply paths (book/menu/
 * carrier) but never admin force-give.
 *
 * @param key        the path-derived base key (e.g. {@code enchants/lifesteal})
 * @param display    the display name (colour codes intact), for lore/name render
 * @param description a short description for {@code /se docs} + lore; never {@code null} (empty if absent)
 * @param tier       the rarity tier (ADR-0016) for lore colour/glint/GUI sort; may be {@code null}
 * @param appliesTo  the item target groups this enchant may sit on (named groups, not raw materials)
 * @param maxLevel   the highest level offered (defaults to the highest declared level)
 * @param requires   prerequisite enchant base keys that must be present at a level &ge; this one's (§G)
 * @param blacklist  enchant base keys this one cannot coexist with — bidirectional (§G)
 * @param removesRequired whether a successful apply removes all {@code requires} (net-zero slots, §G)
 * @param source     where this enchant was authored
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
