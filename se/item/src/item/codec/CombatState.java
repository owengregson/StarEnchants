package item.codec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The combat-relevant on-item state (docs/architecture.md §4.2, §5.1): which enchant definitions an
 * item carries (by <em>stable string key</em> &rarr; level), which crystal definitions are applied
 * (a LIST of stable keys — crystals stack, fixing EA's last-of-type collapse), which armour-set the
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
 * @param setKey   the armour-set this piece belongs to (stable key), or {@code null} for none
 * @param omni     whether this is an omni wildcard set piece (§6.6)
 */
public record CombatState(Map<String, Integer> enchants, List<String> crystals, String setKey, boolean omni) {

    /** An item with no StarEnchants combat state. */
    public static final CombatState EMPTY = new CombatState(Map.of(), List.of(), null, false);

    public CombatState {
        // Defensive, order-PRESERVING copies → the record is immutable and the encoded blob (and thus
        // the content-hash cache key) is deterministic. Map.copyOf would not keep insertion order, so
        // an unmodifiable LinkedHashMap is used instead.
        enchants = Collections.unmodifiableMap(new LinkedHashMap<>(enchants));
        crystals = List.copyOf(crystals);
        setKey = (setKey == null || setKey.isBlank()) ? null : setKey;
    }

    /** Back-compat constructor for state with no set membership (enchants + crystals only). */
    public CombatState(Map<String, Integer> enchants, List<String> crystals) {
        this(enchants, crystals, null, false);
    }

    /** Whether this item carries no combat state at all (the common miss-path case). */
    public boolean isEmpty() {
        return enchants.isEmpty() && crystals.isEmpty() && setKey == null && !omni;
    }
}
