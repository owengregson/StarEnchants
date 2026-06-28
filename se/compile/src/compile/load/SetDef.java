package compile.load;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored armour set (ADR-0014). A set is tierless and has TWO bonuses:
 * an ARMOUR bonus active once {@link #armorComplete} pieces are worn, and an optional WEAPON bonus that
 * fires only while the armour set is complete AND its weapon is held. It expands to {@code <key>} (armour)
 * and, when a {@link #weapon} is present, {@code <key>/weapon} (gated by the resolver, not a piece count).
 *
 * @param tier          always {@code null} for sets (kept for {@link Library} uniformity)
 * @param armorComplete worn-piece count that completes the set ({@code >= 1})
 * @param armorLore     lore SHARED by every armour piece, rendered from state on the worn piece
 * @param weapon        the weapon member, or {@code null} for an armour-only set
 * @param weaponLore    the weapon's own lore (empty when there is no weapon)
 * @param appliesTo     armour slot tokens this set covers, derived from {@link #armorMembers}
 * @param armorEnchants enchants every armour piece is minted with ({@code ref → level}, insertion order):
 *                      a {@code enchants/<id>} ref is a custom plugin enchant (stamped into the piece's
 *                      combat state, validated at compile), any other key is a vanilla enchant NAME applied
 *                      cross-version at mint (§6.6, author-configurable)
 * @param weaponEnchants enchants the set weapon is minted with (same {@code ref → level} model)
 */
public record SetDef(
        String key,
        String display,
        String description,
        String tier,
        int armorComplete,
        List<Member> armorMembers,
        List<String> armorLore,
        Member weapon,
        List<String> weaponLore,
        List<String> appliesTo,
        Map<String, Integer> armorEnchants,
        Map<String, Integer> weaponEnchants,
        Source source) {

    public SetDef {
        armorMembers = List.copyOf(armorMembers);
        armorLore = List.copyOf(armorLore);
        weaponLore = List.copyOf(weaponLore);
        appliesTo = List.copyOf(appliesTo);
        // Unmodifiable LinkedHashMap (not Map.copyOf) so the authored enchant order is preserved — it
        // determines the lore order of custom set-piece enchants.
        armorEnchants = Collections.unmodifiableMap(new LinkedHashMap<>(armorEnchants));
        weaponEnchants = Collections.unmodifiableMap(new LinkedHashMap<>(weaponEnchants));
    }

    public record Member(String slot, String material, String name) {
    }

    public boolean hasWeapon() {
        return weapon != null;
    }
}
