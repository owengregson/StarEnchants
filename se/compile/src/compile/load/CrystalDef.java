package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored crystal (ADR-0014). A crystal stacks (an item carries a LIST of
 * crystal keys) and has no levels — it expands to exactly ONE {@code AbilityDef} keyed by its base key.
 * The likeness fields are per-crystal overrides; a blank one falls back to the shared
 * {@code items/crystal.yml} {@link CrystalConfig} at mint (also the likeness of a merged multi-crystal,
 * whose two effects can't share one material).
 *
 * @param tier      rarity tier (ADR-0016); may be {@code null}
 * @param material  override material (cross-version-resolved at mint), or {@code null}
 * @param appliesTo named item target groups, not raw materials
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
