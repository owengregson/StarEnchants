package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * The parsed, non-runtime metadata of one authored enchant (docs/architecture.md §4.2; ADR-0014):
 * its key, display name, description, the item target groups it applies to, and its level range.
 * Retained in the {@link Library} catalog for the later render / apply cycles; the runtime
 * {@code Snapshot} carries only the {@code AbilityDef}s this enchant expands into. Immutable.
 *
 * @param key        the path-derived base key (e.g. {@code enchants/lifesteal})
 * @param display    the display name (colour codes intact), for lore/name render
 * @param description a short description for {@code /se docs} + lore; never {@code null} (empty if absent)
 * @param appliesTo  the item target groups this enchant may sit on (named groups, not raw materials)
 * @param maxLevel   the highest level offered (defaults to the highest declared level)
 * @param source     where this enchant was authored
 */
public record EnchantDef(
        String key,
        String display,
        String description,
        List<String> appliesTo,
        int maxLevel,
        Source source) {

    public EnchantDef {
        appliesTo = List.copyOf(appliesTo);
    }
}
