package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.ItemsHolder;
import compile.load.ItemsLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.plugin.Plugin;
import platform.content.ContentReloader;
import platform.content.ReloadStep;
import schema.diag.Diagnostic;
import schema.diag.Source;
import tester.harness.Harness;

/**
 * Content loader + transactional reload (ADR-0014): parsing on the server's bundled SnakeYAML (1.x vs 2.x
 * varies by version), handle resolution through the production compiler (§9), and the reloader's off-thread
 * build + global-thread swap on Folia.
 */
public final class ContentLoaderSuite implements Harness.Scenario {

    private static final String SPARK = """
            display: Spark
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ POTION: { effect: STRENGTH, level: 2, duration: 60 } }] }
            """;
    private static final String BOLT = """
            display: Bolt
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ POTION: { effect: SPEED, level: 2, duration: 60 } }] }
            """;

    private final Plugin plugin;

    public ContentLoaderSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    private static final String SOUL_GEM = """
            type: soul-gem
            material: EMERALD
            souls-per-kill: 7
            """;

    @Override
    public void accept(Harness h) {
        h.expect("content.load.handleResolves");
        h.expect("content.reload.swaps");
        h.expect("content.reload.transaction"); // §L-4: a parallel source swaps with content
        h.expect("content.reload.aborts");      // §L-4: a broken source aborts the whole transaction

        Path root;
        try {
            root = Files.createTempDirectory("se-content-suite");
            write(root, "enchants/spark.yml", SPARK);
        } catch (IOException e) {
            h.fail("content.load.handleResolves", e.toString());
            h.fail("content.reload.swaps", e.toString());
            return;
        }

        // accept() runs on the global thread, so resolving STRENGTH against the live registries is safe.
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
        // Fresh compiler per build (clean interners) — never reuse the initial-load one.
        ContentReloader reloader = new ContentReloader(holder, ContentCompiler::production, root, 0);
        try {
            write(root, "enchants/bolt.yml", BOLT);
        } catch (IOException e) {
            h.fail("content.reload.swaps", e.toString());
            return;
        }
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

        transactionChecks(h);
    }

    /**
     * §L-4: the reload is a transaction over content + parallel config sources, beyond the inline-backend unit
     * test's reach — {@link ReloadStep#build()}s run off-thread and publish on the global thread only when clean.
     */
    private void transactionChecks(Harness h) {
        Path croot;
        Path items;
        try {
            croot = Files.createTempDirectory("se-txn-content");
            write(croot, "enchants/spark.yml", SPARK); // clean content so the transaction can commit
            items = Files.createTempDirectory("se-txn-items"); // starts EMPTY → soulGem absent
        } catch (IOException e) {
            h.fail("content.reload.transaction", e.toString());
            h.fail("content.reload.aborts", e.toString());
            return;
        }

        // OK case: a clean items step swaps alongside content. Holder starts empty; after the staged
        // soul-gem.yml reloads, the source must publish with souls-per-kill = 7.
        ItemsHolder itemsHolder = new ItemsHolder(ItemsLoader.load(items));
        ReloadStep itemsStep = () -> {
            var cfg = ItemsLoader.load(items);
            return new ReloadStep.Built(cfg.diagnostics(), () -> itemsHolder.publish(cfg));
        };
        ContentHolder okHolder = new ContentHolder(LibraryLoader.load(croot, ContentCompiler.production(), 0));
        ContentReloader okReloader = new ContentReloader(okHolder, ContentCompiler::production, croot, 0,
                lib -> { }, List.of(itemsStep));
        try {
            write(items, "soul-gem.yml", SOUL_GEM); // stage content for the source to swap in
        } catch (IOException e) {
            h.fail("content.reload.transaction", e.toString());
        }
        okReloader.reload(result -> h.guard("content.reload.transaction", () -> {
            if (!result.published()) {
                throw new IllegalStateException("transactional reload did not publish: " + result.diagnostics());
            }
            if (itemsHolder.config().soulGem().isEmpty() || itemsHolder.config().soulGemOrDefault().soulsPerKill() != 7) {
                throw new IllegalStateException("items source was not swapped with content (soul gem absent/stale)");
            }
        }));

        // Abort case: a source reporting a blocking diagnostic must keep all prior state (content not bumped,
        // source not published) and surface the fault.
        ContentHolder abHolder = new ContentHolder(LibraryLoader.load(croot, ContentCompiler.production(), 0));
        boolean[] sourcePublished = {false};
        ReloadStep brokenStep = () -> new ReloadStep.Built(
                List.of(Diagnostic.error("X_BAD_SOURCE", "broken config", Source.UNKNOWN)),
                () -> sourcePublished[0] = true);
        ContentReloader abReloader = new ContentReloader(abHolder, ContentCompiler::production, croot, 0,
                lib -> { }, List.of(brokenStep));
        abReloader.reload(result -> h.guard("content.reload.aborts", () -> {
            if (result.published()) {
                throw new IllegalStateException("a broken source did NOT abort the reload");
            }
            if (sourcePublished[0]) {
                throw new IllegalStateException("a source published despite the transaction aborting");
            }
            if (abHolder.snapshot().generation() != 0) {
                throw new IllegalStateException("content was swapped despite the abort: gen "
                        + abHolder.snapshot().generation());
            }
            if (result.diagnostics().stream().noneMatch(d -> d.code().equals("X_BAD_SOURCE"))) {
                throw new IllegalStateException("the source's diagnostic was not surfaced in the result");
            }
        }));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
