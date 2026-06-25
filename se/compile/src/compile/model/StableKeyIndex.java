package compile.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The persistent stable-key &harr; dense-id indirection (docs/architecture.md §5.3). Items store stable
 * string keys in PDC; the dense {@link Ability#id()} is a per-snapshot accelerator reassigned freely on
 * reload — so an old item still resolves by its stable key, keeping content hot-swappable. An unknown key
 * resolves to {@code -1} (skipped, never a crash).
 */
public final class StableKeyIndex {

    private final List<String> keysByDenseId;
    private final Map<String, Integer> idByKey;

    /**
     * @param keysByDenseId stable keys in dense-id order (index = dense id); must
     *                      contain no duplicates (the eraser enforces uniqueness)
     */
    public StableKeyIndex(List<String> keysByDenseId) {
        this.keysByDenseId = List.copyOf(keysByDenseId);
        Map<String, Integer> map = new HashMap<>();
        for (int id = 0; id < this.keysByDenseId.size(); id++) {
            map.put(this.keysByDenseId.get(id), id);
        }
        this.idByKey = Map.copyOf(map);
    }

    /** The dense id for {@code stableKey}, or {@code -1} if no ability carries it. */
    public int idOf(String stableKey) {
        Integer id = idByKey.get(stableKey);
        return id == null ? -1 : id;
    }

    /**
     * The stable key for dense id {@code id}, or {@code null} if out of range. A dense id from a DIFFERENT
     * (reloaded) snapshot may fall outside this one's range, so cross-snapshot callers get {@code null}
     * rather than an {@link IndexOutOfBoundsException}; an id from THIS snapshot always resolves.
     */
    public String keyOf(int id) {
        return id < 0 || id >= keysByDenseId.size() ? null : keysByDenseId.get(id);
    }

    public int size() {
        return keysByDenseId.size();
    }
}
