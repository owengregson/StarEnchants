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
 * @param key        the path-derived base key (e.g. {@code crystals/jolt}) — the key stored on items
 * @param display    the display name (colour codes intact), for lore/name render + GUI
 * @param description a short description for {@code /se docs} + lore; never {@code null} (empty if absent)
 * @param tier       the rarity tier (ADR-0016) for lore colour/glint/GUI sort; may be {@code null}
 * @param material   this crystal item's material token (cross-version-resolved at mint), or {@code null}
 * @param name       this crystal item's display name ({@code &} colours, {@code {CRYSTAL}}), or {@code null}
 * @param lore       this crystal item's lore lines ({@code &} colours, {@code {CRYSTAL}}); empty if absent
 * @param appliesTo  the item target groups this crystal may sit on (named groups, not raw materials)
 * @param source     where this crystal was authored
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
