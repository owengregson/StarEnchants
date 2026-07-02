package pack.arch;

import org.junit.jupiter.api.Test;
import testfx.Purity;

/**
 * CI lock for the load-bearing purity boundary (docs/architecture.md §2.1): {@code pack} is the config-
 * pack ZIP snapshot surface and must stay server-free (it operates on config bytes, not a live server).
 * A stray {@code org.bukkit}/NMS/Paper import is a build failure.
 */
class CorePurityArchTest {

    @Test
    void dependsOnNoServerApi() {
        Purity.assertServerFree("pack");
    }
}
