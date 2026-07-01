package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored crystal (ADR-0014, ADR-0034). A crystal stacks (an item carries a LIST
 * of crystal keys) and has no levels; it expands to one or more {@code AbilityDef}s ({@code <key>}, then
 * {@code <key>/a1}, {@code /a2}, … like an armour set's bonuses) so a single crystal can carry independent
 * bonuses across triggers.
 *
 * <p>The likeness (material/name/lore) is NO LONGER per-crystal — it is the ONE global {@link CrystalConfig}
 * ({@code items/crystal.yml}). A crystal file declares only its identity: the styled {@code display} name (whose
 * colour is carried inline, so a merge reads each name in its own colour), the {@code description} block rendered
 * verbatim into the crystal item's {@code {DESCRIPTION}}, and the item kinds it {@code appliesTo}.
 *
 * @param display     styled display name (colour carried inline); the {@code {CRYSTAL}} token renders it
 * @param description the authored bonus block, verbatim, stacked into the crystal lore's {@code {DESCRIPTION}}
 * @param tier        rarity tier (ADR-0016); may be {@code null}
 * @param appliesTo   named item target groups, not raw materials
 */
public record CrystalDef(
        String key,
        String display,
        List<String> description,
        String tier,
        List<String> appliesTo,
        Source source) {

    public CrystalDef {
        description = List.copyOf(description);
        appliesTo = List.copyOf(appliesTo);
    }
}
