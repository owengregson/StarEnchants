package schema.arch;

import org.junit.jupiter.api.Test;
import testfx.Purity;

/**
 * CI lock for the load-bearing purity boundary (docs/architecture.md §2.1): {@code schema} is the DSL
 * as a pure language definition and must stay server-free so the compiler is deterministically
 * unit-testable. A stray {@code org.bukkit}/NMS/Paper import silently couples the language to a server —
 * this turns that into a build failure rather than a review catch.
 */
class CorePurityArchTest {

    @Test
    void dependsOnNoServerApi() {
        Purity.assertServerFree("schema");
    }
}
