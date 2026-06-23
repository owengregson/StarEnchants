package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * The parsed, non-runtime metadata of one authored armour set (docs/architecture.md §6.6; ADR-0014).
 * A set has TWO bonuses (docs/v3-directives.md §6.6): an ARMOUR set bonus that activates once
 * {@link #armorComplete} of its armour pieces are worn, and an optional ADDITIONAL WEAPON bonus that
 * fires only while the armour set is complete AND its weapon is held. Sets have NO rarity tier.
 *
 * <p>It expands to one {@code AbilityDef} per bonus — {@code <key>} (armour, carrying
 * {@link #armorComplete} on its {@code setPieces}) and, when {@link #weapon} is present,
 * {@code <key>/weapon} (the weapon bonus, gated by the resolver, not by a piece count). The likeness
 * fields let {@code /se give set} mint each member: every armour piece has its own
 * {@link Member#name()} (sharing {@link #armorLore}), and the weapon has its own name + {@link #weaponLore}.
 * Immutable.
 *
 * @param key          the path-derived base key (e.g. {@code sets/titan}) — stamped on armour members
 * @param display      the set display name (colours intact), for lore/name render + GUI
 * @param description  a short description for {@code /se docs} + lore; never {@code null} (empty if absent)
 * @param tier         always {@code null} for sets (kept for {@link Library} uniformity)
 * @param armorComplete the number of worn ARMOUR pieces that completes the set ({@code >= 1})
 * @param armorMembers each armour member: its slot, item material, and own display name
 * @param armorLore    the lore SHARED by every armour piece (rendered from state on the worn piece)
 * @param weapon       the weapon member (its own material + name), or {@code null} for an armour-only set
 * @param weaponLore   the weapon's OWN lore (empty when there is no weapon)
 * @param appliesTo    the armour slot tokens this set covers (derived from {@link #armorMembers})
 * @param source       where this set was authored
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
        Source source) {

    public SetDef {
        armorMembers = List.copyOf(armorMembers);
        armorLore = List.copyOf(armorLore);
        weaponLore = List.copyOf(weaponLore);
        appliesTo = List.copyOf(appliesTo);
    }

    /** One member of a set: which slot it occupies ({@code helmet}/{@code weapon}/…), its material, its name. */
    public record Member(String slot, String material, String name) {
    }

    /** Whether this set declares a weapon member (so it has an additional weapon bonus). */
    public boolean hasWeapon() {
        return weapon != null;
    }
}
