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
 * @param armorEnchants enchants every armour piece is minted with ({@code ref → roll}, insertion order):
 *                      a {@code enchants/<id>} ref is a custom plugin enchant (stamped into the piece's
 *                      combat state, validated at compile), any other key is a vanilla enchant NAME applied
 *                      cross-version at mint (§6.6, author-configurable). A member's own
 *                      {@link Member#enchants()} extend this shared roster per slot
 * @param weaponEnchants enchants the set weapon is minted with (same {@code ref → roll} model)
 * @param announce      send the player a chat line when the set transitions complete/incomplete (off by default)
 * @param equipMessage  the line sent when the set becomes complete (authored verbatim, no tokens; may be empty)
 * @param removeMessage the line sent when a complete set drops below its threshold (verbatim; may be empty)
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
        Map<String, EnchantRoll> armorEnchants,
        Map<String, EnchantRoll> weaponEnchants,
        boolean announce,
        String equipMessage,
        String removeMessage,
        Source source) {

    public SetDef {
        armorMembers = List.copyOf(armorMembers);
        armorLore = List.copyOf(armorLore);
        weaponLore = List.copyOf(weaponLore);
        appliesTo = List.copyOf(appliesTo);
        equipMessage = equipMessage == null ? "" : equipMessage;
        removeMessage = removeMessage == null ? "" : removeMessage;
        // Unmodifiable LinkedHashMap (not Map.copyOf) so the authored enchant order is preserved — it
        // determines the lore order of custom set-piece enchants.
        armorEnchants = Collections.unmodifiableMap(new LinkedHashMap<>(armorEnchants));
        weaponEnchants = Collections.unmodifiableMap(new LinkedHashMap<>(weaponEnchants));
    }

    /**
     * One physical member of a set — a declared armour slot, or the {@code weapon} pseudo-slot. {@link #lore}
     * and {@link #enchants} REFINE the set-wide {@code armor.lore} / {@code armor.enchants} rather than
     * replace them: a piece's own flavour lines print above the shared block, and its own roster entries are
     * stamped after the shared ones. Both are empty for a set that says nothing per piece, which is what
     * every pack authored before this surface existed — so an untouched set def renders and mints unchanged.
     *
     * @param slot     the armour slot token ({@code helmet}/{@code chestplate}/{@code leggings}/{@code boots})
     *                 or {@code weapon}; matched case-insensitively against a rendered item's gear kind
     * @param material the Bukkit material token this piece mints as
     * @param name     the piece's display name, or {@code null} to fall back to the set display
     * @param lore     this piece's own lore lines, printed ABOVE the shared {@code armor.lore}
     * @param enchants this piece's own mint roster, stamped AFTER the shared {@code armor.enchants}
     * @param color    a leather dye for this piece ({@code #RRGGBB} or a Bukkit colour name), or {@code null}
     * @param heroic   mint this piece ALREADY heroic (the pack's {@code items/heroic.yml} tier), so it counts
     *                 toward {@code %victim.heroicpieces%} without consuming an upgrade
     */
    public record Member(String slot, String material, String name, List<String> lore,
                         Map<String, EnchantRoll> enchants, String color, boolean heroic) {

        public Member {
            lore = List.copyOf(lore);
            enchants = Collections.unmodifiableMap(new LinkedHashMap<>(enchants));
        }

        /** The pre-per-piece form: material + name only, saying nothing of its own. */
        public Member(String slot, String material, String name) {
            this(slot, material, name, List.of(), Map.of(), null, false);
        }
    }

    public boolean hasWeapon() {
        return weapon != null;
    }

    /**
     * The armour member for a gear-kind token ({@code HELMET}, {@code boots}, …), or {@code null} when this
     * set declares no such slot. The token is a rendered item's material kind, which IS the slot name for
     * every piece a set can mint — so lore rendering needs no member discriminator on the item itself.
     */
    public Member armorMember(String slotToken) {
        if (slotToken == null) {
            return null;
        }
        for (Member member : armorMembers) {
            if (member.slot().equalsIgnoreCase(slotToken)) {
                return member;
            }
        }
        return null;
    }

    /**
     * The full armour lore for one slot: that piece's own lines followed by the lore every piece shares. A
     * slot with nothing of its own (or an unknown token) yields exactly {@link #armorLore()}, so this is the
     * drop-in widening of the old set-key-only read.
     */
    public List<String> armorLoreFor(String slotToken) {
        Member member = armorMember(slotToken);
        if (member == null || member.lore().isEmpty()) {
            return armorLore;
        }
        List<String> out = new java.util.ArrayList<>(member.lore().size() + armorLore.size());
        out.addAll(member.lore());
        out.addAll(armorLore);
        return List.copyOf(out);
    }

    /**
     * The full mint roster for one slot: the shared {@code armor.enchants} then that piece's own. A piece may
     * re-state a shared ref to override its roll (last wins), which is how a set says "everyone gets Protection
     * IV, but the boots roll Gears at max".
     */
    public Map<String, EnchantRoll> armorEnchantsFor(String slotToken) {
        Member member = armorMember(slotToken);
        if (member == null || member.enchants().isEmpty()) {
            return armorEnchants;
        }
        Map<String, EnchantRoll> out = new LinkedHashMap<>(armorEnchants);
        out.putAll(member.enchants());
        return Collections.unmodifiableMap(out);
    }
}
