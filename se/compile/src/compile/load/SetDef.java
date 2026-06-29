package compile.load;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored armour set (ADR-0014) — its PHYSICAL members only. A set is tierless;
 * its pieces ({@link #armorMembers}) and optional {@link #weapon} are what get minted, with their shared lore
 * and per-piece enchants. Its BEHAVIOUR is any number of bonus abilities, read separately from the unified
 * {@code bonuses:} list: each is {@code on: armor} (fires once {@link #armorComplete} pieces are worn) or
 * {@code on: weapon} (fires while complete AND its weapon is held). The first armour bonus expands to
 * {@code <key>} (its completion count on {@code setPieces}); further armour bonuses to {@code <key>/aN} and
 * weapon bonuses to {@code <key>/wN}, all resolver-gated (not a piece count).
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
