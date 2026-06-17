package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * The parsed, non-runtime metadata of one authored armour set (docs/architecture.md §6.6; ADR-0014):
 * its key, display name, description, the piece count that completes it, and the item groups its
 * pieces may sit on. A set expands to exactly ONE {@link compile.def.AbilityDef} (its bonus, tagged
 * {@link compile.model.SourceKind#SET}, keyed by the base key with the completion threshold carried
 * on the ability) — the bonus fires once the worn-piece count reaches {@link #pieces}. Retained in
 * the {@link Library} catalog for the render / apply cycles. Immutable.
 *
 * @param key        the path-derived base key (e.g. {@code sets/yeti}) — the key stamped on member items
 * @param display    the display name (colour codes intact), for lore/name render
 * @param description a short description for {@code /se docs} + lore; never {@code null} (empty if absent)
 * @param tier       the rarity tier (ADR-0016) for lore colour/glint/GUI sort; may be {@code null}
 * @param pieces     the number of worn pieces that completes the set ({@code >= 1})
 * @param appliesTo  the item target groups this set's pieces may sit on
 * @param source     where this set was authored
 */
public record SetDef(
        String key,
        String display,
        String description,
        String tier,
        int pieces,
        List<String> appliesTo,
        Source source) {

    public SetDef {
        appliesTo = List.copyOf(appliesTo);
    }
}
