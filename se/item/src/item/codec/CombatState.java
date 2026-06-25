package item.codec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The combat-relevant on-item state (docs/architecture.md §4.2, §5.1): which enchant definitions an
 * item carries (by <em>stable string key</em> &rarr; level), which crystal definitions are applied
 * (a LIST of stable keys — crystals stack, fixing a Cosmic Enchants-style last-of-type collapse), which armour-set the
 * item belongs to ({@code setKey}, or {@code null}), and whether it is an <em>omni</em> wildcard
 * piece (counts toward any partially-worn set, §6.6). This is the record decoded on the combat hot
 * path; identity/economy state (scrolls, dust, crates) lives in a separate record.
 *
 * <p><strong>State only, never behavior.</strong> The item names <em>which</em> definitions apply;
 * the compiled programs live in the {@code Snapshot}. Stable keys (not dense ids) are stored so an
 * item authored years ago still resolves after any reload reassigns dense ids (§5.3). Enchant order
 * is preserved (authoring/rarity order) so the encoded blob — and thus the content-hash cache key —
 * is deterministic.
 *
 * @param enchants stable-key &rarr; level, in insertion order; never {@code null}
 * @param crystals applied crystal stable keys, in order; never {@code null}
 * @param setKey   the armour-set this piece belongs to as an ARMOUR member (stable key), or {@code null}.
 *                 An armour member counts toward set completion (§6.6).
 * @param setWeaponKey the armour-set this item is the WEAPON of (stable key), or {@code null}. A weapon
 *                 does NOT count toward completion — instead, while the set is complete AND this weapon is
 *                 held, the set's additional weapon bonus ({@code <setKey>/weapon}) fires
 *                 (docs/v3-directives.md §6.6: "hold the weapon while wearing the set for the extra bonus").
 * @param omni     whether this is an omni wildcard set piece (§6.6)
 * @param heroic   the heroic flat stats this piece carries (§6); {@link HeroicStat#NONE} for none
 * @param added    extra enchant slots purchased onto this item (slot expander / gem, §H); never negative.
 *                 Persisted so a slot increase survives — it feeds the {@code SlotLedger} at apply time
 *                 (docs/v3-directives.md §H: "persist per-item slot count to PDC").
 */
public record CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey,
                          String setWeaponKey, boolean omni, HeroicStat heroic, int added) {

    /** An item with no StarEnchants combat state. */
    public static final CombatState EMPTY =
            new CombatState(Map.of(), List.of(), null, null, false, HeroicStat.NONE, 0);

    public CombatState {
        // Defensive, order-PRESERVING copies → the record is immutable and the encoded blob (and thus
        // the content-hash cache key) is deterministic. Map.copyOf would not keep insertion order, so
        // an unmodifiable LinkedHashMap is used instead.
        enchants = Collections.unmodifiableMap(new LinkedHashMap<>(enchants));
        crystals = List.copyOf(crystals);
        setKey = (setKey == null || setKey.isBlank()) ? null : setKey;
        setWeaponKey = (setWeaponKey == null || setWeaponKey.isBlank()) ? null : setWeaponKey;
        heroic = heroic == null ? HeroicStat.NONE : heroic;
        added = Math.max(0, added);
    }

    /** Back-compat constructor for state with no set membership or heroic stats (enchants + crystals only). */
    public CombatState(Map<String, Integer> enchants, List<String> crystals) {
        this(enchants, crystals, null, null, false, HeroicStat.NONE, 0);
    }

    /** Constructor for state with armour-set membership but no heroic stats. */
    public CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey, boolean omni) {
        this(enchants, crystals, setKey, null, omni, HeroicStat.NONE, 0);
    }

    /** Constructor for state with armour-set membership + heroic but no purchased slots (the common case). */
    public CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey, boolean omni,
                       HeroicStat heroic) {
        this(enchants, crystals, setKey, null, omni, heroic, 0);
    }

    /** Back-compat constructor (no weapon-set membership): the pre-weapon-bonus 6-field shape. */
    public CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey, boolean omni,
                       HeroicStat heroic, int added) {
        this(enchants, crystals, setKey, null, omni, heroic, added);
    }

    /** A set's WEAPON member: holding it while the set is complete fires the set's weapon bonus (§6.6). */
    public static CombatState weaponMember(String weaponSetKey) {
        return new CombatState(Map.of(), List.of(), null, weaponSetKey, false, HeroicStat.NONE, 0);
    }

    /** This state with {@code added} purchased enchant slots (slot expander / gem, §H). */
    public CombatState withAdded(int added) {
        return new CombatState(enchants, crystals, setKey, setWeaponKey, omni, heroic, added);
    }

    /** Whether this item carries no combat state at all (the common miss-path case). */
    public boolean isEmpty() {
        return enchants.isEmpty() && crystals.isEmpty() && setKey == null && setWeaponKey == null
                && !omni && heroic.isZero() && added == 0;
    }
}
