package compile.load;

import compile.model.Snapshot;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single published content (docs/architecture.md §0; ADR-0014): an {@link AtomicReference} to
 * the current {@link Library} that the engine and item layers read and the reload path swaps by
 * reference. Pure — no Bukkit — so lower modules depend <em>down</em> onto it. A reader always sees
 * a fully-built, immutable {@link Library}/{@link Snapshot}, never a torn state; the swap is a single
 * atomic reference write performed on the global thread by the reloader.
 */
public final class ContentHolder {

    private final AtomicReference<Library> current = new AtomicReference<>();

    public ContentHolder(Library initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    /** The live library (snapshot + catalog + the diagnostics it loaded with). */
    public Library library() {
        return current.get();
    }

    /** The live compiled snapshot the runtime walks. */
    public Snapshot snapshot() {
        return current.get().snapshot();
    }

    /** Publish a new library by reference — the transactional reload swap (§10). */
    public void publish(Library library) {
        current.set(Objects.requireNonNull(library, "library"));
    }
}
