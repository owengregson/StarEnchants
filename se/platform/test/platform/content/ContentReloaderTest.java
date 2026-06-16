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
 * Unit tests for the transactional reloader (ADR-0014), with an inline scheduler backend so the
 * off-thread build → global-thread swap runs deterministically and synchronously. They pin the
 * contract: a clean build is published (generation bumped), a build with a blocking diagnostic keeps
 * the old content live, and a dry run never swaps.
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
        Compiler compiler = compiler();
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler, 0)); // empty initially
        ContentReloader reloader = new ContentReloader(holder, compiler, root, 0);
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
        Compiler compiler = compiler();
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler, 0));
        ContentReloader reloader = new ContentReloader(holder, compiler, root, 0);
        assertNotNull(holder.snapshot().byStableKey("enchants/good/1"));

        write(root, "enchants/bad.yml", "trigger: ATTACK\n"); // no levels -> blocking diagnostic
        ReloadResult[] result = new ReloadResult[1];
        reloader.reload(r -> result[0] = r);

        assertFalse(result[0].published());
        assertTrue(result[0].errorCount() > 0);
        assertEquals(0, holder.snapshot().generation()); // still the original, un-swapped snapshot
        assertNotNull(holder.snapshot().byStableKey("enchants/good/1"));
    }

    @Test
    void dryRunNeverPublishes(@TempDir Path root) throws IOException {
        Compiler compiler = compiler();
        ContentHolder holder = new ContentHolder(LibraryLoader.load(root, compiler, 0)); // empty
        ContentReloader reloader = new ContentReloader(holder, compiler, root, 0);
        write(root, "enchants/spark.yml", GOOD);

        ReloadResult[] result = new ReloadResult[1];
        reloader.dryRun(r -> result[0] = r);

        assertTrue(result[0].dryRun());
        assertFalse(result[0].published());
        assertEquals(0, holder.snapshot().abilityCount()); // holder still the empty initial library
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
