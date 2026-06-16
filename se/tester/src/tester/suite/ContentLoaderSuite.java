package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;
import platform.content.ContentReloader;
import tester.harness.Harness;

/**
 * Live checks for the content loader + transactional reload (ADR-0014) — the things a unit test
 * cannot prove because they depend on the real server: that the loader's SnakeYAML parsing works on
 * the server's bundled SnakeYAML (1.x vs 2.x varies by version), that a version-volatile handle in an
 * authored effect resolves through the loader's production compiler on THIS version (§9), and that the
 * reloader's off-thread build + global-thread swap is correct on Folia as well as Paper.
 *
 * <ul>
 *   <li>{@code content.load.handleResolves} — an authored {@code POTION:STRENGTH} enchant loads with
 *       no errors and the per-level ability is present: the potion handle resolved at load time.</li>
 *   <li>{@code content.reload.swaps} — adding a file and reloading builds off-thread, resolves another
 *       handle there, and swaps the new snapshot in on the global thread (generation bumped).</li>
 * </ul>
 */
public final class ContentLoaderSuite implements Harness.Scenario {

    private static final String SPARK = """
            display: Spark
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:STRENGTH:1:60"] }
            """;
    private static final String BOLT = """
            display: Bolt
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:SPEED:1:60"] }
            """;

    private final Plugin plugin;

    public ContentLoaderSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("content.load.handleResolves");
        h.expect("content.reload.swaps");

        Path root;
        try {
            root = Files.createTempDirectory("se-content-suite");
            write(root, "enchants/spark.yml", SPARK);
        } catch (IOException e) {
            h.fail("content.load.handleResolves", e.toString());
            h.fail("content.reload.swaps", e.toString());
            return;
        }

        // accept() runs on the global thread, so the initial load (which resolves the STRENGTH handle
        // against the live registries) is safe here.
        Compiler compiler = ContentCompiler.production();
        Library initial = LibraryLoader.load(root, compiler, 0);
        h.guard("content.load.handleResolves", () -> {
            if (initial.hasErrors()) {
                throw new IllegalStateException("load reported errors: " + initial.diagnostics());
            }
            if (initial.snapshot().byStableKey("enchants/spark/1") == null) {
                throw new IllegalStateException("enchants/spark/1 missing after load (handle unresolved?)");
            }
        });

        ContentHolder holder = new ContentHolder(initial);
        ContentReloader reloader = new ContentReloader(holder, compiler, root, 0);
        try {
            write(root, "enchants/bolt.yml", BOLT);
        } catch (IOException e) {
            h.fail("content.reload.swaps", e.toString());
            return;
        }
        // reload() builds off-thread (resolving the SPEED handle there) and swaps on the global thread.
        reloader.reload(result -> h.guard("content.reload.swaps", () -> {
            if (!result.published()) {
                throw new IllegalStateException("reload did not publish: " + result.diagnostics());
            }
            if (holder.snapshot().byStableKey("enchants/bolt/1") == null) {
                throw new IllegalStateException("enchants/bolt/1 missing after reload");
            }
            if (holder.snapshot().generation() != 1) {
                throw new IllegalStateException("generation not bumped: " + holder.snapshot().generation());
            }
        }));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
