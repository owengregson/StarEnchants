package compile.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns a dense {@code int} id to each distinct string so the hot path compares
 * ids and packs bitsets instead of comparing strings (docs/architecture.md §4.1,
 * §8 "Interning"). The compiler interns worlds, triggers, suppression keys and
 * cooldown scopes while assembling a {@link Snapshot}; the resulting id&harr;name
 * table is then frozen into the immutable snapshot.
 *
 * <p>Ids are assigned sequentially from {@code 0} in first-seen order. Callers
 * normalize keys (case-fold, trim) <em>before</em> interning — this stores keys
 * exactly as given, so two spellings that must share an id have to arrive
 * identical. Build-time only and single-thread-confined; a snapshot is assembled on
 * one thread and never mutated afterward.
 */
public final class Interner {

    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> names = new ArrayList<>();

    /** The id for {@code key}, assigning the next sequential id if it is unseen. */
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

    /** The name interned at {@code id}. */
    public String nameOf(int id) {
        return names.get(id);
    }

    /** Count of distinct interned strings. */
    public int size() {
        return names.size();
    }

    /** An immutable id&rarr;name table (index = id), for freezing into a snapshot. */
    public List<String> names() {
        return List.copyOf(names);
    }
}
