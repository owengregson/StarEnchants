package compile.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The persistent stable-key &harr; dense-id indirection (docs/architecture.md §5.3).
 * Items store <em>stable string keys</em> in PDC; the dense {@link Ability#id()} is a
 * per-snapshot array accelerator only. On reload/restart ids are reassigned freely,
 * yet an item authored years ago still resolves by its stable key — this is what
 * makes the content library hot-swappable and items forward-compatible across the
 * nine-year version range. An unknown key resolves to {@code -1} (rendered as
 * "unknown" and skipped, never a crash).
 *
 * <p>Immutable: built once during erasure from the dense-id-ordered key list.
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
     * The stable key assigned to dense id {@code id}, or {@code null} if {@code id} is out of this
     * index's range. Symmetric with {@link #idOf} ("unknown, never a crash"): a dense id resolved
     * against a DIFFERENT (e.g. reloaded) snapshot may fall outside this one's range, so callers that
     * might cross a snapshot boundary get {@code null} rather than an {@link IndexOutOfBoundsException}.
     * Callers resolving an id produced by THIS snapshot's {@code abilities[]} always get a non-null key.
     */
    public String keyOf(int id) {
        return id < 0 || id >= keysByDenseId.size() ? null : keysByDenseId.get(id);
    }

    /** The number of indexed abilities. */
    public int size() {
        return keysByDenseId.size();
    }
}
