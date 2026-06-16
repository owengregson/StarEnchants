package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * The parsed, non-runtime metadata of one authored crystal (docs/architecture.md §6.5; ADR-0014):
 * its key, display name, description, and the item target groups it applies to. A crystal is a
 * first-class source that stacks (an item carries a LIST of crystal keys), so — unlike an enchant —
 * it has no levels: it expands to exactly ONE {@code AbilityDef} keyed by its base key. Retained in
 * the {@link Library} catalog for the render / apply cycles. Immutable.
 *
 * @param key        the path-derived base key (e.g. {@code crystals/jolt}) — the key stored on items
 * @param display    the display name (colour codes intact), for lore/name render
 * @param description a short description for {@code /se docs} + lore; never {@code null} (empty if absent)
 * @param appliesTo  the item target groups this crystal may sit on (named groups, not raw materials)
 * @param source     where this crystal was authored
 */
public record CrystalDef(
        String key,
        String display,
        String description,
        List<String> appliesTo,
        Source source) {

    public CrystalDef {
        appliesTo = List.copyOf(appliesTo);
    }
}
