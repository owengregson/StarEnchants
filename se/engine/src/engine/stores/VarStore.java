package engine.stores;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player writable variables (docs/architecture.md §5.4; v3.1 §A) — the runtime home for the
 * {@code SET_VAR} / {@code INVERT_VAR} effects (AE's parameterized dynamic variables). A variable is a
 * named string value, scoped to one player and optionally time-limited.
 *
 * <p>This is the <em>writable</em> companion to the read-only built-in fact system: built-in facts
 * ({@code actor.health}, {@code sneaking}, …) are dense compile-time slots in a {@link
 * engine.condition.FactBuffer}; author-named dynamic vars are runtime strings that cannot have been
 * enumerated at compile time, so they live here and are read back through the {@code FactBuffer}'s
 * unknown-token (PlaceholderAPI) seam — a condition's {@code %name%} resolves built-in slot first, then
 * this store, then real PAPI. The two var spaces coexist without a compiler or IR change.
 *
 * <p>Concurrent and UUID-keyed for Folia (any region thread may set or read a player's vars), and
 * TTL-evicting: an elapsed var is dropped lazily on the next {@link #get}, so the maps stay bounded
 * without a sweeper (mirrors {@link CooldownStore}/{@link SuppressionStore}). Cleared on quit
 * ({@link #clear}) and on disable ({@link #clearAll}).
 *
 * <p>Time is an explicit tick count supplied by the caller (the current server/region tick), never
 * wall-clock — so behaviour is deterministic and Folia-correct, and the store is unit-testable without a
 * server. Variable names are canonicalised to lower-case (matching the condition var-name convention),
 * so {@code SET_VAR:Rage} and a condition reading {@code %rage%} agree.
 */
public final class VarStore {

    /** A stored value and its expiry tick; {@link Long#MAX_VALUE} means "never expires". */
    private record Entry(String value, long expiry) {}

    private final Map<UUID, Map<String, Entry>> byPlayer = new ConcurrentHashMap<>();

    /**
     * The current value of {@code player}'s variable {@code name} at {@code nowTicks}, or {@code null} if
     * it is unset or has expired (an elapsed var is evicted lazily here, like a cooldown).
     */
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
     * Set {@code player}'s variable {@code name} to {@code value}. A {@code ttlTicks <= 0} means the
     * variable does not expire; otherwise it elapses at {@code nowTicks + ttlTicks}. A {@code null} value
     * is stored as the empty string (so a read returns "" rather than "unset").
     */
    public void set(UUID player, String name, String value, long nowTicks, int ttlTicks) {
        long expiry = ttlTicks <= 0 ? Long.MAX_VALUE : nowTicks + ttlTicks;
        byPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(canonical(name), new Entry(value == null ? "" : value, expiry));
    }

    /**
     * Numerically invert {@code player}'s variable {@code name}, preserving its remaining TTL: the current
     * value is parsed as a number (an unset/non-numeric var counts as {@code 0}); {@code 0} becomes
     * {@code "1"} and any non-zero value becomes {@code "0"} (a boolean flip). An unset var is created with
     * no expiry.
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
