package compile.model;

import schema.diag.Diagnostic;
import java.util.List;

/**
 * The immutable compiled world: every {@link Ability} plus the tables to interpret and diagnose them
 * (docs/architecture.md §4.5, §5.2). Exactly one {@code Snapshot} is live, swapped by a single
 * {@code AtomicReference} after a transactional reload — a bad edit never reaches the hot path (§10).
 *
 * @param generation  monotonic build counter; with a content hash it keys the item-view cache and stamps
 *                    a {@code WornState} to detect a stale equip snapshot (§5.2)
 * @param abilities   the dense ability array; {@code abilities[id].id() == id}
 * @param stableKeys  stable-key &harr; dense-id index (§5.3)
 * @param interners   the frozen name&harr;id tables (§4.1)
 * @param sourceMap   defId &rarr; authored origin, for op-visible diagnostics (§10)
 * @param diagnostics every diagnostic produced while building this snapshot, frozen
 */
public record Snapshot(
        int generation,
        Ability[] abilities,
        StableKeyIndex stableKeys,
        Interners interners,
        SourceMap sourceMap,
        List<Diagnostic> diagnostics) {

    public Snapshot {
        diagnostics = List.copyOf(diagnostics);
    }

    public int abilityCount() {
        return abilities.length;
    }

    /** The ability carrying {@code stableKey}, or {@code null} if none does. */
    public Ability byStableKey(String stableKey) {
        int id = stableKeys.idOf(stableKey);
        return id < 0 ? null : abilities[id];
    }
}
