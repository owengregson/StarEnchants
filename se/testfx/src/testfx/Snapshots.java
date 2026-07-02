package testfx;

import compile.model.Ability;
import compile.model.Interner;
import compile.model.Interners;
import compile.model.Snapshot;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;
import java.util.List;
import java.util.Map;
import schema.diag.Diagnostic;

/**
 * Fluent builder for the runtime {@link Snapshot} record. Defaults produce an empty compiled world; a test
 * states only the tables it reads. {@code stableKeys(String...)} wraps the dense-id-ordered keys through a
 * real {@link StableKeyIndex}, matching {@code abilities[id].id() == id}.
 */
public final class Snapshots {

    private Snapshots() {
    }

    /** An empty {@link Snapshot} (generation 0, no abilities, empty index/interners/sourceMap/diagnostics). */
    public static Builder snapshot() {
        return new Builder();
    }

    public static final class Builder {
        private int generation = 0;
        private Ability[] abilities = new Ability[0];
        private StableKeyIndex stableKeys = new StableKeyIndex(List.of());
        private Interners interners =
                new Interners(new Interner(), new Interner(), new Interner(), new Interner());
        private SourceMap sourceMap = new SourceMap(Map.of());
        private List<Diagnostic> diagnostics = List.of();

        public Builder generation(int generation) {
            this.generation = generation;
            return this;
        }

        public Builder abilities(Ability... abilities) {
            this.abilities = abilities;
            return this;
        }

        public Builder stableKeys(StableKeyIndex stableKeys) {
            this.stableKeys = stableKeys;
            return this;
        }

        /** Wrap the keys, indexed by dense id order, in a real {@link StableKeyIndex}. */
        public Builder stableKeys(String... keysInDenseIdOrder) {
            this.stableKeys = new StableKeyIndex(List.of(keysInDenseIdOrder));
            return this;
        }

        public Builder interners(Interners interners) {
            this.interners = interners;
            return this;
        }

        public Builder sourceMap(SourceMap sourceMap) {
            this.sourceMap = sourceMap;
            return this;
        }

        public Builder diagnostics(List<Diagnostic> diagnostics) {
            this.diagnostics = diagnostics;
            return this;
        }

        public Snapshot build() {
            return new Snapshot(generation, abilities, stableKeys, interners, sourceMap, diagnostics);
        }
    }
}
