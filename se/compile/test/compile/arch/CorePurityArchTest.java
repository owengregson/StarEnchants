package compile.arch;

import org.junit.jupiter.api.Test;
import testfx.Purity;

/**
 * CI lock for the load-bearing purity boundary (docs/architecture.md §2.1): {@code compile} turns
 * authored YAML+DSL into an immutable Snapshot and must stay server-free so the compiler is
 * deterministically unit-testable. A stray {@code org.bukkit}/NMS/Paper import is a build failure.
 */
class CorePurityArchTest {

    @Test
    void dependsOnNoServerApi() {
        Purity.assertServerFree("compile");
    }
}
