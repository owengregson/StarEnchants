package compile.model;

import schema.diag.Source;
import java.util.Map;

/**
 * Maps an {@link Ability#defId()} back to where it was authored, so a runtime fault is reported op-visibly
 * and the misbehaving ability auto-quarantined rather than aborting the activation (docs/architecture.md §4.1, §10).
 */
public record SourceMap(Map<Integer, Entry> byDefId) {

    public SourceMap {
        byDefId = Map.copyOf(byDefId);
    }

    public record Entry(SourceKind sourceKind, String stableKey, Source source) {
    }

    /** The origin of {@code defId}, or {@code null} if unknown. */
    public Entry lookup(int defId) {
        return byDefId.get(defId);
    }
}
