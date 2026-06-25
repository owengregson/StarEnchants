package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * The parsed, non-runtime metadata of one authored crystal (docs/architecture.md §6.5; ADR-0014):
 * its key, display name, description, the item target groups it applies to, and its own PHYSICAL
 * likeness (material/name/lore) so the minted crystal item self-explains its effect (docs/v3-directives.md
 * §E — "the lore is different per crystal"). A crystal is a first-class source that stacks (an item
 * carries a LIST of crystal keys), so — unlike an enchant — it has no levels: it expands to exactly ONE
 * {@code AbilityDef} keyed by its base key. Retained in the {@link Library} catalog for the render /
 * apply / mint cycles. Immutable.
 *
 * <p>The likeness fields are per-crystal overrides; a blank {@code material}/{@code name} or empty
 * {@code lore} falls back to the shared {@code items/crystal.yml} {@link CrystalConfig} at mint time
 * (which is also the likeness of a merged multi-crystal, whose two effects can't share one material).
 *
 * @param key        path-derived base key (e.g. {@code crystals/jolt}) — the key stored on items
 * @param description never {@code null} (empty if absent)
 * @param tier       rarity tier (ADR-0016); may be {@code null}
 * @param material   per-crystal override material (cross-version-resolved at mint), or {@code null}
 * @param name       per-crystal override name, or {@code null}
 * @param lore       per-crystal override lore; empty if absent
 * @param appliesTo  named item target groups (not raw materials)
 */
public record CrystalDef(
        String key,
        String display,
        String description,
        String tier,
        String material,
        String name,
        List<String> lore,
        List<String> appliesTo,
        Source source) {

    public CrystalDef {
        lore = List.copyOf(lore);
        appliesTo = List.copyOf(appliesTo);
    }
}
