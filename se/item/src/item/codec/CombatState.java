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
 * @param setMemberKey the authored armour member token (helmet/chestplate/leggings/boots), or {@code null};
 *                 used only to restore that piece's exact lore when the item is recomposed.
 * @param setWeaponKey the armour-set this item is the WEAPON of (stable key), or {@code null}. A weapon
 *                 does NOT count toward completion — instead, while the set is complete AND this weapon is
 *                 held, the set's additional weapon bonus ({@code <setKey>/weapon}) fires
 *                 (docs/v3-directives.md §6.6: "hold the weapon while wearing the set for the extra bonus").
 * @param omni     whether this is an omni wildcard set piece (§6.6)
 * @param heroic   the heroic flat stats this piece carries (§6); {@link HeroicStat#NONE} for none
 * @param added    extra enchant slots purchased onto this item (slot expander / gem, §H); never negative.
 *                 Persisted so a slot increase survives — it feeds the {@code SlotLedger} at apply time
 *                 (docs/v3-directives.md §H: "persist per-item slot count to PDC").
 * @param maskKey  the mask applied onto this HELMET (stable key), or {@code null} (ADR-0053). Single-per-helmet
 *                 (mirrors {@code setKey}, not the {@code crystals} list); its abilities fire while the helmet is
 *                 worn but it never joins set/heroic/crystal accounting.
 * @param reforgeKey the reforge applied onto this WEAPON (stable key), or {@code null} (ADR-0070). Single-per-weapon
 *                 (mirrors {@code maskKey}); its abilities fire while the weapon is HELD but it never joins
 *                 set/heroic/crystal accounting.
 */
public record CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey,
                          String setMemberKey, String setWeaponKey, boolean omni, HeroicStat heroic, int added,
                          String maskKey, String reforgeKey) {

    public static final CombatState EMPTY =
            new CombatState(Map.of(), List.of(), null, null, null, false, HeroicStat.NONE, 0, null, null);

    public CombatState {
        // Order-preserving copy keeps the encoded blob (and thus the content-hash cache key) deterministic;
        // Map.copyOf would drop insertion order, hence an unmodifiable LinkedHashMap.
        enchants = Collections.unmodifiableMap(new LinkedHashMap<>(enchants));
        crystals = List.copyOf(crystals);
        setKey = (setKey == null || setKey.isBlank()) ? null : setKey;
        setMemberKey = (setMemberKey == null || setMemberKey.isBlank()) ? null : setMemberKey;
        setWeaponKey = (setWeaponKey == null || setWeaponKey.isBlank()) ? null : setWeaponKey;
        heroic = heroic == null ? HeroicStat.NONE : heroic;
        added = Math.max(0, added);
        maskKey = (maskKey == null || maskKey.isBlank()) ? null : maskKey; // mirrors setKey (ADR-0053)
        reforgeKey = (reforgeKey == null || reforgeKey.isBlank()) ? null : reforgeKey; // mirrors maskKey (ADR-0070)
    }

    /** Backward-compatible canonical shape from before per-member set lore was persisted. */
    public CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey,
                       String setWeaponKey, boolean omni, HeroicStat heroic, int added, String maskKey,
                       String reforgeKey) {
        this(enchants, crystals, setKey, null, setWeaponKey, omni, heroic, added, maskKey, reforgeKey);
    }

    public CombatState(Map<String, Integer> enchants, List<String> crystals) {
        this(enchants, crystals, null, null, null, false, HeroicStat.NONE, 0, null, null);
    }

    public CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey, boolean omni) {
        this(enchants, crystals, setKey, null, null, omni, HeroicStat.NONE, 0, null, null);
    }

    public CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey, boolean omni,
                       HeroicStat heroic) {
        this(enchants, crystals, setKey, null, null, omni, heroic, 0, null, null);
    }

    public CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey, boolean omni,
                       HeroicStat heroic, int added) {
        this(enchants, crystals, setKey, null, null, omni, heroic, added, null, null);
    }

    /** A set's WEAPON member: holding it while the set is complete fires the set's weapon bonus (§6.6). */
    public static CombatState weaponMember(String weaponSetKey) {
        return new CombatState(Map.of(), List.of(), null, null, weaponSetKey, false,
                HeroicStat.NONE, 0, null, null);
    }

    public CombatState withAdded(int added) {
        return new CombatState(enchants, crystals, setKey, setMemberKey, setWeaponKey, omni, heroic, added,
                maskKey, reforgeKey);
    }

    /** Copy with new enchants, preserving every other field — the safe mutator (the multi-arg ctors silently
     *  null {@code setWeaponKey} and {@code maskKey}, which once stripped a set weapon's membership when it was
     *  enchanted; the same trap now applies to a helmet's mask, ADR-0053, and a weapon's reforge, ADR-0070). */
    public CombatState withEnchants(Map<String, Integer> enchants) {
        return new CombatState(enchants, crystals, setKey, setMemberKey, setWeaponKey, omni, heroic, added,
                maskKey, reforgeKey);
    }

    /** Copy with new crystals, preserving every other field (incl. set membership and sockets). */
    public CombatState withCrystals(List<String> crystals) {
        return new CombatState(enchants, crystals, setKey, setMemberKey, setWeaponKey, omni, heroic, added,
                maskKey, reforgeKey);
    }

    /** Copy with new heroic stats, preserving every other field (incl. set membership and sockets). */
    public CombatState withHeroic(HeroicStat heroic) {
        return new CombatState(enchants, crystals, setKey, setMemberKey, setWeaponKey, omni, heroic, added,
                maskKey, reforgeKey);
    }

    /** Copy with a new applied mask (or {@code null} to pop it off), preserving every other field (ADR-0053). */
    public CombatState withMask(String maskKey) {
        return new CombatState(enchants, crystals, setKey, setMemberKey, setWeaponKey, omni, heroic, added,
                maskKey, reforgeKey);
    }

    /** Copy with a new applied reforge (or {@code null} to pop it off), preserving every other field (ADR-0070). */
    public CombatState withReforge(String reforgeKey) {
        return new CombatState(enchants, crystals, setKey, setMemberKey, setWeaponKey, omni, heroic, added,
                maskKey, reforgeKey);
    }

    public boolean isEmpty() {
        return enchants.isEmpty() && crystals.isEmpty() && setKey == null && setMemberKey == null
                && setWeaponKey == null && !omni && heroic.isZero() && added == 0
                && maskKey == null && reforgeKey == null;
    }
}
