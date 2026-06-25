package compile.load;

import compile.model.Snapshot;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single published content (ADR-0014): an {@link AtomicReference} to the live {@link Library}, swapped
 * by reference on the reload path. A reader always sees a fully-built immutable library, never a torn state.
 */
public final class ContentHolder {

    private final AtomicReference<Library> current = new AtomicReference<>();

    public ContentHolder(Library initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    public Library library() {
        return current.get();
    }

    public Snapshot snapshot() {
        return current.get().snapshot();
    }

    /** The transactional reload swap (§10). */
    public void publish(Library library) {
        current.set(Objects.requireNonNull(library, "library"));
    }
}
