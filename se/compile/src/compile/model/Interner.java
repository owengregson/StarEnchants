package compile.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns a dense {@code int} id to each distinct string so the hot path compares ids instead of strings
 * (docs/architecture.md §4.1, §8). Ids run sequentially from {@code 0} in first-seen order.
 *
 * <p>Callers must normalize keys (case-fold, trim) <em>before</em> interning — keys are stored
 * verbatim, so spellings that must share an id have to arrive identical. Build-time only and
 * single-thread-confined.
 */
public final class Interner {

    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> names = new ArrayList<>();

    /** The id for {@code key}, assigning the next sequential id if unseen. */
    public int intern(String key) {
        Integer existing = ids.get(key);
        if (existing != null) {
            return existing;
        }
        int id = names.size();
        ids.put(key, id);
        names.add(key);
        return id;
    }

    /** The id for {@code key} if already interned, else {@code -1} (no assignment). */
    public int idOf(String key) {
        Integer id = ids.get(key);
        return id == null ? -1 : id;
    }

    public String nameOf(int id) {
        return names.get(id);
    }

    public int size() {
        return names.size();
    }

    /** An immutable id&rarr;name table (index = id), for freezing into a snapshot. */
    public List<String> names() {
        return List.copyOf(names);
    }
}
