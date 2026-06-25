package platform.content;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import platform.sched.Scheduling;
import schema.diag.Diagnostic;

/**
 * The transactional content reload (docs/architecture.md §10; ADR-0014). Builds the next {@link Library}
 * OFF the main thread (the {@link LibraryLoader} is pure — file I/O + parse + compile, touching only
 * frozen Bukkit registries), then swaps the {@link ContentHolder} by reference on the global thread ONLY
 * when the build is clean, so a broken reload keeps the old content live; {@link #dryRun} never swaps.
 *
 * <p>The bootstrap supplies a CONSTANT compiler on purpose: its handle resolver is the one
 * {@code RuntimeHandles} pairs with, so a token interned at compile resolves back to its object at runtime
 * (§9) — a fresh resolver per build would break that round-trip. Single-flight makes reuse safe; only the
 * append-only handle interner is shared. (A fresh-compiler-per-call factory is also valid where runtime
 * handle resolution is not needed.)
 *
 * <p>Single-flight: one reload at a time, a concurrent one rejected with {@link ReloadResult#busy()}, so
 * builds never race or publish out of order. Each build stamps the next generation; only distinctness, not
 * contiguity, matters, so a dry-run/rejected build harmlessly consumes one. {@code onPublished} fires on
 * the global thread after a successful swap — the seam for invalidating gen-keyed runtime caches.
 */
public final class ContentReloader {

    private final ContentHolder holder;
    private final Supplier<Compiler> compilerFactory;
    private final Path contentRoot;
    private final AtomicInteger generation;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final Consumer<Library> onPublished;
    private final List<ReloadStep> steps; // §L parallel config sources swapped in the same transaction

    public ContentReloader(ContentHolder holder, Supplier<Compiler> compilerFactory,
                           Path contentRoot, int initialGeneration) {
        this(holder, compilerFactory, contentRoot, initialGeneration, library -> { });
    }

    public ContentReloader(ContentHolder holder, Supplier<Compiler> compilerFactory,
                           Path contentRoot, int initialGeneration, Consumer<Library> onPublished) {
        this(holder, compilerFactory, contentRoot, initialGeneration, onPublished, List.of());
    }

    /**
     * As above, plus parallel {@link ReloadStep config sources} reloaded in the SAME transaction as the
     * Library (§L): the commit swaps content AND every source only when all are clean (true all-or-nothing).
     */
    public ContentReloader(ContentHolder holder, Supplier<Compiler> compilerFactory,
                           Path contentRoot, int initialGeneration, Consumer<Library> onPublished,
                           List<ReloadStep> steps) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.compilerFactory = Objects.requireNonNull(compilerFactory, "compilerFactory");
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot");
        this.generation = new AtomicInteger(initialGeneration);
        this.onPublished = Objects.requireNonNull(onPublished, "onPublished");
        this.steps = List.copyOf(steps);
    }

    /** Build the next library synchronously with a fresh compiler (clean interners). Does not publish. */
    public Library build() {
        return LibraryLoader.load(contentRoot, compilerFactory.get(), generation.incrementAndGet());
    }

    /**
     * Validate a single candidate content file ({@code relativePath}, e.g. {@code enchants/x.yml}) against
     * the LIVE tree WITHOUT touching disk or publishing — the "would this reload cleanly?" check {@code /se
     * import} runs before it commits anything (ADR-0029). The live tree is shallow-copied into a throwaway
     * directory, the candidate overlaid there, and the whole thing run through the real {@link LibraryLoader}
     * — so the candidate is validated in full context (duplicate-key, {@code requires}/{@code blacklist}
     * referential integrity, grammar), exactly as the eventual reload would see it. Synchronous; the caller
     * runs it off-thread. The throwaway tree is deleted before returning.
     *
     * @return the candidate's diagnostics as a never-published {@link ReloadResult} (errors → keep disk untouched)
     */
    public ReloadResult validateCandidate(String relativePath, String yaml) {
        Path scratch = null;
        try {
            scratch = java.nio.file.Files.createTempDirectory("se-import-validate");
            Path overlayRoot = scratch.resolve("content");
            copyTree(contentRoot, overlayRoot);
            Path candidate = overlayRoot.resolve(relativePath);
            java.nio.file.Files.createDirectories(candidate.getParent());
            java.nio.file.Files.writeString(candidate, yaml, java.nio.charset.StandardCharsets.UTF_8);
            Library library = LibraryLoader.load(overlayRoot, compilerFactory.get(), generation.incrementAndGet());
            return new ReloadResult(false, true, library.snapshot().generation(),
                    library.snapshot().abilityCount(), library.diagnostics());
        } catch (java.io.IOException io) {
            return ReloadResult.failure(io);
        } finally {
            if (scratch != null) {
                deleteTree(scratch);
            }
        }
    }

    /** Copy the live content tree into {@code dst} so the candidate can be validated alongside it. */
    private static void copyTree(Path src, Path dst) throws java.io.IOException {
        java.nio.file.Files.createDirectories(dst);
        if (!java.nio.file.Files.isDirectory(src)) {
            return; // first import on a server with no content/ yet — validate the candidate alone
        }
        try (var walk = java.nio.file.Files.walk(src)) {
            for (Path path : walk.toList()) {
                Path target = dst.resolve(src.relativize(path).toString());
                if (java.nio.file.Files.isDirectory(path)) {
                    java.nio.file.Files.createDirectories(target);
                } else {
                    java.nio.file.Files.createDirectories(target.getParent());
                    java.nio.file.Files.copy(path, target);
                }
            }
        }
    }

    /** Best-effort delete of the throwaway validation tree; a stranded temp dir is harmless. */
    private static void deleteTree(Path root) {
        try (var walk = java.nio.file.Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    java.nio.file.Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                    // leave it for the OS temp sweep
                }
            });
        } catch (java.io.IOException ignored) {
            // nothing to clean up
        }
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
            onDone.accept(ReloadResult.busy()); // single-flight
            return;
        }
        Scheduling.async(() -> {
            try {
                Library library = build();
                // Build every parallel source off-thread too (pure parse), so the swap can be all-or-nothing.
                List<ReloadStep.Built> built = new ArrayList<>(steps.size());
                for (ReloadStep step : steps) {
                    built.add(step.build());
                }
                boolean clean = !library.hasErrors() && built.stream().noneMatch(ReloadStep.Built::hasErrors);
                boolean publish = !dryRun && clean;
                Scheduling.onGlobal(() -> finish(library, built, publish, dryRun, onDone));
            } catch (Throwable buildFailure) {
                // build() threw (e.g. I/O fault walking the tree) — report + release the guard on the
                // global thread, so the operator never waits forever on a stranded reload.
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

    private void finish(Library library, List<ReloadStep.Built> built, boolean publish, boolean dryRun,
                        Consumer<ReloadResult> onDone) {
        try {
            if (publish) {
                try {
                    holder.publish(library);
                    for (ReloadStep.Built b : built) {
                        b.publish().run(); // swap each source — by-reference set, all already validated clean
                    }
                    onPublished.accept(library);
                } catch (Throwable swapFailure) {
                    // Holder publishes are non-throwing reference swaps; a realistic thrower is a third-party
                    // reload listener or the onPublished re-resolve, both of which run AFTER every swap (so
                    // state stays consistent). Report it anyway so /se reload never hangs without a result.
                    onDone.accept(ReloadResult.failure(swapFailure));
                    return;
                }
            }
            onDone.accept(new ReloadResult(publish, dryRun, library.snapshot().generation(),
                    library.snapshot().abilityCount(), mergeDiagnostics(library, built)));
        } finally {
            inFlight.set(false); // released only after the swap + report complete, so single-flight holds
        }
    }

    /** Content diagnostics first, then each parallel source's — so {@code /se reload} reports every fault. */
    private static List<Diagnostic> mergeDiagnostics(Library library, List<ReloadStep.Built> built) {
        List<Diagnostic> all = new ArrayList<>(library.diagnostics());
        for (ReloadStep.Built b : built) {
            all.addAll(b.diagnostics());
        }
        return all;
    }
}
