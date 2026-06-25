package engine.stores;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player writable variables (docs/architecture.md §5.4; v3.1 §A) — runtime home for the
 * {@code SET_VAR} / {@code INVERT_VAR} effects. A variable is a named string value, scoped to one player
 * and optionally time-limited.
 *
 * <p>The <em>writable</em> companion to the built-in fact slots: author-named dynamic vars can't be
 * enumerated at compile time, so they live here and are read back through the {@code FactBuffer}'s
 * unknown-token seam. Resolution order for a {@code %name%}: built-in slot, then this store, then real PAPI.
 *
 * <p>Concurrent, UUID-keyed (Folia). TTL-evicting: an elapsed var is dropped lazily on the next
 * {@link #get}, so the maps stay bounded without a sweeper. Time is an explicit caller-supplied tick,
 * never wall-clock — deterministic, Folia-correct, server-free to test. Names are canonicalised to
 * lower-case so {@code SET_VAR:Rage} and a condition reading {@code %rage%} agree.
 */
public final class VarStore {

    /** A stored value and its expiry tick; {@link Long#MAX_VALUE} means "never expires". */
    private record Entry(String value, long expiry) {}

    private final Map<UUID, Map<String, Entry>> byPlayer = new ConcurrentHashMap<>();

    /** Value of {@code player}'s var {@code name}, or {@code null} if unset/expired (elapsed evicted lazily). */
    public String get(UUID player, String name, long nowTicks) {
        Map<String, Entry> vars = byPlayer.get(player);
        if (vars == null) {
            return null;
        }
        String key = canonical(name);
        Entry entry = vars.get(key);
        if (entry == null) {
            return null;
        }
        if (nowTicks >= entry.expiry()) {
            vars.remove(key, entry); // lazy eviction of an elapsed variable
            return null;
        }
        return entry.value();
    }

    /**
     * Set {@code player}'s var {@code name} to {@code value}. {@code ttlTicks <= 0} means no expiry; else
     * it elapses at {@code nowTicks + ttlTicks}. A {@code null} value is stored as "" (read returns "").
     */
    public void set(UUID player, String name, String value, long nowTicks, int ttlTicks) {
        long expiry = ttlTicks <= 0 ? Long.MAX_VALUE : nowTicks + ttlTicks;
        byPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(canonical(name), new Entry(value == null ? "" : value, expiry));
    }

    /**
     * Boolean-flip {@code player}'s var {@code name}, preserving its remaining TTL: {@code 0} (also
     * unset/non-numeric) becomes {@code "1"}, any non-zero becomes {@code "0"}. An unset var is created
     * with no expiry.
     */
    public void invert(UUID player, String name, long nowTicks) {
        Map<String, Entry> vars = byPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        String key = canonical(name);
        Entry current = vars.get(key);
        long expiry = Long.MAX_VALUE;
        double value = 0.0;
        if (current != null) {
            if (nowTicks < current.expiry()) {
                expiry = current.expiry(); // keep the remaining TTL
                value = parse(current.value());
            }
            // else: expired — treat as unset (value 0, no expiry)
        }
        vars.put(key, new Entry(value == 0.0 ? "1" : "0", expiry));
    }

    /** Forget every variable for one player (call on quit). */
    public void clear(UUID player) {
        byPlayer.remove(player);
    }

    /** Forget every variable for every player (call on disable). */
    public void clearAll() {
        byPlayer.clear();
    }

    private static String canonical(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    /** Parse a stored value as a number for {@link #invert}; non-numeric (or null) reads as {@code 0}. */
    private static double parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException notNumeric) {
            return 0.0;
        }
    }
}
