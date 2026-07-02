package engine.run;

import compile.model.SourceMap;
import compile.model.StableKeyIndex;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-snapshot fault quarantine (docs/architecture.md §10): after {@link #threshold} execution failures of
 * the SAME ability (dense-id keyed) it is disabled for the life of this snapshot, so one broken content unit
 * can neither spam the log nor burn cycles on every hit. Bound fresh per snapshot, so a reload with the edit
 * fixed clears the block automatically — no persistent penalty box. Concurrent: any region thread may record
 * a fault (Folia).
 */
public final class AbilityQuarantine {

    private static final Logger LOG = System.getLogger("StarEnchants.Quarantine");

    /** An inert quarantine for an unbound executor (unit tests, pre-boot): never disables, never resolves a key. */
    public static final AbilityQuarantine NONE = new AbilityQuarantine(null, null, 0);

    private final SourceMap sourceMap;
    private final StableKeyIndex stableKeys;
    private final int threshold;
    private final ConcurrentHashMap<Integer, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final Set<Integer> disabled = ConcurrentHashMap.newKeySet();

    /** @param threshold failures before an ability is disabled; {@code <= 0} makes an inert quarantine. */
    public AbilityQuarantine(SourceMap sourceMap, StableKeyIndex stableKeys, int threshold) {
        this.sourceMap = sourceMap;
        this.stableKeys = stableKeys;
        this.threshold = threshold;
    }

    /** True once {@code denseId} has been quarantined for this snapshot — the executor skips it before running effects. */
    public boolean isDisabled(int denseId) {
        return !disabled.isEmpty() && disabled.contains(denseId);
    }

    /**
     * Count one execution fault for {@code denseId}. On reaching the threshold it is disabled and ONE line is
     * logged at the SEVERE tier ({@link Level#ERROR}) naming the stable key + authored source; subsequent
     * faults on an already-disabled ability are silent (it is skipped before it can fault again).
     */
    public void recordFailure(int denseId, int defId) {
        if (threshold <= 0) {
            return; // inert (NONE)
        }
        int count = failures.computeIfAbsent(denseId, k -> new AtomicInteger()).incrementAndGet();
        if (count >= threshold && disabled.add(denseId)) {
            LOG.log(Level.ERROR, "quarantined " + describe(defId) + " after " + count + " failures");
        }
    }

    /** The stable key + {@code file:line} for {@code defId} (for a fault log), or a bare id when unbound/unknown. */
    public String describe(int defId) {
        SourceMap.Entry entry = sourceMap == null ? null : sourceMap.lookup(defId);
        if (entry == null) {
            return "def#" + defId;
        }
        return entry.stableKey() + " (" + entry.source() + ")";
    }

    /** The stable keys currently quarantined in this snapshot, sorted — the read surface a command can query. */
    public List<String> quarantinedKeys() {
        List<String> keys = new ArrayList<>(disabled.size());
        for (int denseId : disabled) {
            String key = stableKeys == null ? null : stableKeys.keyOf(denseId);
            keys.add(key == null ? "#" + denseId : key);
        }
        keys.sort(null);
        return keys;
    }
}
