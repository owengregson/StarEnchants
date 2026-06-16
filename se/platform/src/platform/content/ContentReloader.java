package platform.content;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import platform.sched.Scheduling;

/**
 * The transactional content reload (docs/architecture.md §10; ADR-0014). It builds the next
 * {@link Library} OFF the main thread (the {@link LibraryLoader} is pure — file I/O + parse + compile,
 * no Bukkit), then on the global thread swaps the {@link ContentHolder} by reference ONLY when the
 * build produced no blocking diagnostic. A fatal edit keeps the old content live, so a broken reload
 * never takes the server down; {@link #dryRun} reports without ever swapping.
 *
 * <p>Each build stamps the next generation, so the published {@code Snapshot}'s generation (and thus
 * every {@code ItemView}/{@code WornState} keyed by it) changes on a successful reload. The initial
 * load is done directly by the caller via {@link LibraryLoader} (with generation 0) to seed the holder;
 * this reloader owns generations 1+.
 */
public final class ContentReloader {

    private final ContentHolder holder;
    private final Compiler compiler;
    private final Path contentRoot;
    private final AtomicInteger generation;

    public ContentReloader(ContentHolder holder, Compiler compiler, Path contentRoot, int initialGeneration) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot");
        this.generation = new AtomicInteger(initialGeneration);
    }

    /** Build the next library synchronously (pure; the next generation). Does not publish. */
    public Library build() {
        return LibraryLoader.load(contentRoot, compiler, generation.incrementAndGet());
    }

    /** Reload off-thread; on the global thread publish the build iff it is clean, then report to {@code onDone}. */
    public void reload(Consumer<ReloadResult> onDone) {
        apply(false, onDone);
    }

    /** Build + report on the global thread, but never publish — for checking an edit before committing to it. */
    public void dryRun(Consumer<ReloadResult> onDone) {
        apply(true, onDone);
    }

    private void apply(boolean dryRun, Consumer<ReloadResult> onDone) {
        Objects.requireNonNull(onDone, "onDone");
        Scheduling.async(() -> {
            Library library = build();
            Scheduling.onGlobal(() -> {
                boolean publish = !dryRun && !library.hasErrors();
                if (publish) {
                    holder.publish(library);
                }
                onDone.accept(new ReloadResult(publish, dryRun,
                        library.snapshot().generation(), library.snapshot().abilityCount(),
                        library.diagnostics()));
            });
        });
    }
}
