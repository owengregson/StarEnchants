package platform.content;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import platform.sched.Scheduling;

/**
 * The transactional content reload (docs/architecture.md §10; ADR-0014). It builds the next
 * {@link Library} OFF the main thread (the {@link LibraryLoader} is pure — file I/O + parse + compile,
 * touching only frozen Bukkit registries, never regionized state), then on the global thread swaps the
 * {@link ContentHolder} by reference ONLY when the build produced no blocking diagnostic. A fatal edit
 * keeps the old content live, so a broken reload never takes the server down; {@link #dryRun} reports
 * without ever swapping.
 *
 * <p><strong>A fresh compiler per build.</strong> Each build constructs its own {@link Compiler} via
 * the injected factory, so the compiler's interner state is clean per build — never shared between
 * concurrent builds (which would corrupt its unsynchronized maps) and never accumulated across
 * sequential reloads (which would make ids a non-deterministic function of reload history).
 *
 * <p><strong>Single-flight.</strong> Only one reload runs at a time; a concurrent {@code /se reload}
 * is rejected with {@link ReloadResult#busy()}, so two builds can never race or publish out of order.
 * Each build stamps the next generation, so a successful reload always publishes a strictly higher
 * generation than the last (every {@code ItemView}/{@code WornState} keyed by it sees a fresh value);
 * a dry-run or a rejected build also consumes a generation number — harmless, since only distinctness
 * (not contiguity) matters.
 *
 * <p>The initial load is done by the caller via {@link LibraryLoader} (generation 0) to seed the
 * holder; this reloader owns generations 1+. An optional {@code onPublished} hook fires on the global
 * thread after a successful swap — the seam a later cycle uses to invalidate gen-keyed runtime caches
 * (e.g. {@code ItemViewCache.reload}) once the engine is wired to the holder.
 */
public final class ContentReloader {

    private final ContentHolder holder;
    private final Supplier<Compiler> compilerFactory;
    private final Path contentRoot;
    private final AtomicInteger generation;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final Consumer<Library> onPublished;

    public ContentReloader(ContentHolder holder, Supplier<Compiler> compilerFactory,
                           Path contentRoot, int initialGeneration) {
        this(holder, compilerFactory, contentRoot, initialGeneration, library -> { });
    }

    public ContentReloader(ContentHolder holder, Supplier<Compiler> compilerFactory,
                           Path contentRoot, int initialGeneration, Consumer<Library> onPublished) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.compilerFactory = Objects.requireNonNull(compilerFactory, "compilerFactory");
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot");
        this.generation = new AtomicInteger(initialGeneration);
        this.onPublished = Objects.requireNonNull(onPublished, "onPublished");
    }

    /** Build the next library synchronously with a fresh compiler (clean interners). Does not publish. */
    public Library build() {
        return LibraryLoader.load(contentRoot, compilerFactory.get(), generation.incrementAndGet());
    }

    /** Reload off-thread; on the global thread publish the build iff it is clean, then report to {@code onDone}. */
    public void reload(Consumer<ReloadResult> onDone) {
        apply(false, onDone);
    }

    /** Build + report on the global thread, but never publish — to check an edit before committing to it. */
    public void dryRun(Consumer<ReloadResult> onDone) {
        apply(true, onDone);
    }

    private void apply(boolean dryRun, Consumer<ReloadResult> onDone) {
        Objects.requireNonNull(onDone, "onDone");
        if (!inFlight.compareAndSet(false, true)) {
            onDone.accept(ReloadResult.busy()); // single-flight: a reload is already running
            return;
        }
        Scheduling.async(() -> {
            try {
                Library library = build();
                boolean publish = !dryRun && !library.hasErrors();
                Scheduling.onGlobal(() -> finish(library, publish, dryRun, onDone));
            } catch (Throwable buildFailure) {
                // build() threw (e.g. an I/O fault walking the tree) — report on the global thread and
                // release the guard, so the operator never waits forever on a stranded reload.
                Scheduling.onGlobal(() -> {
                    try {
                        onDone.accept(ReloadResult.failure(buildFailure));
                    } finally {
                        inFlight.set(false);
                    }
                });
            }
        });
    }

    private void finish(Library library, boolean publish, boolean dryRun, Consumer<ReloadResult> onDone) {
        try {
            if (publish) {
                holder.publish(library);
                onPublished.accept(library);
            }
            onDone.accept(new ReloadResult(publish, dryRun,
                    library.snapshot().generation(), library.snapshot().abilityCount(), library.diagnostics()));
        } finally {
            inFlight.set(false); // released only after the swap + report complete, so single-flight holds
        }
    }
}
