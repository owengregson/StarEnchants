package platform.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.sched.SchedulerBackend;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * Transactional reloader (ADR-0014). An inline scheduler runs the off-thread build → global-thread
 * swap synchronously so the publish/abort decision is deterministic; a blocking diagnostic anywhere
 * must keep the old content live.
 */
class ContentReloaderTest {

    private static final String GOOD = """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:2"] }
            """;

    @BeforeEach
    void setUp() {
        Scheduling.install(new InlineBackend());
    }

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build()));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    @Test
    void reloadPublishesACleanBuild(@TempDir Path root) throws IOException {
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler(), 0));
        ContentReloader reloader = new ContentReloader(holder, ContentReloaderTest::compiler, root, 0);
        write(root, "enchants/spark.yml", GOOD);

        ReloadResult[] result = new ReloadResult[1];
        reloader.reload(r -> result[0] = r);

        assertTrue(result[0].published());
        assertEquals(1, result[0].generation());
        assertNotNull(holder.snapshot().byStableKey("enchants/spark/1"));
    }

    @Test
    void reloadKeepsOldContentOnAFatalBuild(@TempDir Path root) throws IOException {
        write(root, "enchants/good.yml", GOOD);
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler(), 0));
        ContentReloader reloader = new ContentReloader(holder, ContentReloaderTest::compiler, root, 0);
        assertNotNull(holder.snapshot().byStableKey("enchants/good/1"));

        write(root, "enchants/bad.yml", "trigger: ATTACK\n"); // missing levels is a blocking diagnostic
        ReloadResult[] result = new ReloadResult[1];
        reloader.reload(r -> result[0] = r);

        assertFalse(result[0].published());
        assertTrue(result[0].errorCount() > 0);
        assertEquals(0, holder.snapshot().generation());
        assertNotNull(holder.snapshot().byStableKey("enchants/good/1"));
    }

    @Test
    void dryRunNeverPublishes(@TempDir Path root) throws IOException {
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler(), 0));
        ContentReloader reloader = new ContentReloader(holder, ContentReloaderTest::compiler, root, 0);
        write(root, "enchants/spark.yml", GOOD);

        ReloadResult[] result = new ReloadResult[1];
        reloader.dryRun(r -> result[0] = r);

        assertTrue(result[0].dryRun());
        assertFalse(result[0].published());
        assertEquals(0, holder.snapshot().abilityCount());
    }

    @Test
    void cleanStepsPublishWithContent(@TempDir Path root) throws IOException {
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler(), 0));
        write(root, "enchants/spark.yml", GOOD);
        boolean[] sourcePublished = {false};
        ReloadStep step = () -> new ReloadStep.Built(List.of(), () -> sourcePublished[0] = true);
        ContentReloader reloader = new ContentReloader(holder, ContentReloaderTest::compiler, root, 0,
                library -> { }, List.of(step));

        ReloadResult[] result = new ReloadResult[1];
        reloader.reload(r -> result[0] = r);

        assertTrue(result[0].published());
        assertTrue(sourcePublished[0], "a clean parallel source publishes with content");
        assertNotNull(holder.snapshot().byStableKey("enchants/spark/1"));
    }

    @Test
    void aStepWithABlockingDiagnosticAbortsTheWholeSwap(@TempDir Path root) throws IOException {
        write(root, "enchants/good.yml", GOOD);
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler(), 0));
        boolean[] sourcePublished = {false};
        // Clean content but a blocking diagnostic from a parallel source must still abort the transaction.
        ReloadStep brokenStep = () -> new ReloadStep.Built(
                List.of(schema.diag.Diagnostic.error("X_BAD", "broken config", schema.diag.Source.UNKNOWN)),
                () -> sourcePublished[0] = true);
        ContentReloader reloader = new ContentReloader(holder, ContentReloaderTest::compiler, root, 0,
                library -> { }, List.of(brokenStep));

        ReloadResult[] result = new ReloadResult[1];
        reloader.reload(r -> result[0] = r);

        assertFalse(result[0].published(), "a broken source aborts the whole reload");
        assertFalse(sourcePublished[0], "no source is published when the transaction aborts");
        assertEquals(0, holder.snapshot().generation());
        assertTrue(result[0].diagnostics().stream().anyMatch(d -> d.code().equals("X_BAD")),
                "the source's diagnostic is surfaced in the reload result");
    }

    @Test
    void dryRunReportsStepDiagnosticsWithoutPublishing(@TempDir Path root) throws IOException {
        write(root, "enchants/spark.yml", GOOD);
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler(), 0));
        boolean[] sourcePublished = {false};
        ReloadStep step = () -> new ReloadStep.Built(
                List.of(schema.diag.Diagnostic.warning("W_NOTE", "a note", schema.diag.Source.UNKNOWN)),
                () -> sourcePublished[0] = true);
        ContentReloader reloader = new ContentReloader(holder, ContentReloaderTest::compiler, root, 0,
                library -> { }, List.of(step));

        ReloadResult[] result = new ReloadResult[1];
        reloader.dryRun(r -> result[0] = r);

        assertFalse(result[0].published());
        assertFalse(sourcePublished[0], "dry-run never publishes a source");
        assertTrue(result[0].diagnostics().stream().anyMatch(d -> d.code().equals("W_NOTE")),
                "dry-run still surfaces a source's diagnostics");
    }

    /** Runs every scheduled task immediately on the calling thread (deterministic for tests). */
    private static final class InlineBackend implements SchedulerBackend {
        @Override public void onEntity(Entity entity, Runnable task) { task.run(); }
        @Override public void onEntityLater(Entity entity, long delayTicks, Runnable task) { task.run(); }
        @Override public TaskHandle repeatingEntity(Entity e, long i, long p, Runnable t) { t.run(); return TaskHandle.CANCELLED; }
        @Override public void onRegion(Location location, Runnable task) { task.run(); }
        @Override public void onRegionLater(Location location, long delayTicks, Runnable task) { task.run(); }
        @Override public TaskHandle repeatingRegion(Location l, long i, long p, Runnable t) { t.run(); return TaskHandle.CANCELLED; }
        @Override public void onGlobal(Runnable task) { task.run(); }
        @Override public void onGlobalLater(long delayTicks, Runnable task) { task.run(); }
        @Override public TaskHandle repeatingGlobal(long i, long p, Runnable t) { t.run(); return TaskHandle.CANCELLED; }
        @Override public void async(Runnable task) { task.run(); }
    }
}
