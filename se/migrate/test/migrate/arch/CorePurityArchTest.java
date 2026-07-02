package migrate.arch;

import org.junit.jupiter.api.Test;
import testfx.Purity;

/**
 * CI lock for the load-bearing purity boundary (docs/architecture.md §2.1): {@code migrate} is the
 * legacy-NBT reader + EE/EA/AE config importer and must stay server-free (it transforms config text into
 * the pure schema, never touching a live server). A stray {@code org.bukkit}/NMS/Paper import in
 * production code is a build failure. (Only production classes are scanned — the engine dependency on
 * the test classpath is intentionally excluded.)
 */
class CorePurityArchTest {

    @Test
    void dependsOnNoServerApi() {
        Purity.assertServerFree("migrate");
    }
}
