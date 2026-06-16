package platform.caps;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapabilitiesTest {

    @Test
    void parsesStandardBukkitVersions() {
        assertArrayEquals(new int[] {1, 20, 6}, Capabilities.parseVersion("1.20.6-R0.1-SNAPSHOT"));
        assertArrayEquals(new int[] {1, 17, 1}, Capabilities.parseVersion("1.17.1-R0.1-SNAPSHOT"));
        assertArrayEquals(new int[] {26, 1, 2}, Capabilities.parseVersion("26.1.2-R0.1-SNAPSHOT"));
    }

    @Test
    void toleratesMissingPatchAndJunk() {
        // Mojang omits the patch on x.y.0 releases (e.g. "1.21").
        assertArrayEquals(new int[] {1, 21, 0}, Capabilities.parseVersion("1.21-R0.1-SNAPSHOT"));
        // Garbage never throws — it degrades to zeroes.
        assertArrayEquals(new int[] {0, 0, 0}, Capabilities.parseVersion("not-a-version"));
        assertArrayEquals(new int[] {0, 0, 0}, Capabilities.parseVersion(""));
        assertArrayEquals(new int[] {0, 0, 0}, Capabilities.parseVersion(null));
    }

    @Test
    void atLeastOrdersAcrossComponents() {
        Capabilities v1_20_6 = Capabilities.probe("1.20.6-R0.1-SNAPSHOT", false);
        assertTrue(v1_20_6.atLeast(1, 20, 6));   // equal
        assertTrue(v1_20_6.atLeast(1, 20, 5));   // patch below
        assertTrue(v1_20_6.atLeast(1, 17, 1));   // minor below
        assertFalse(v1_20_6.atLeast(1, 20, 7));  // patch above
        assertFalse(v1_20_6.atLeast(1, 21, 0));  // minor above
        assertFalse(v1_20_6.atLeast(2, 0, 0));   // major above
    }

    @Test
    void mojangMappedTracksTheFlip() {
        assertFalse(Capabilities.probe("1.20.4-R0.1-SNAPSHOT", false).mojangMapped());
        assertTrue(Capabilities.probe("1.20.6-R0.1-SNAPSHOT", false).mojangMapped());
        assertTrue(Capabilities.probe("1.21.4-R0.1-SNAPSHOT", false).mojangMapped());
        assertTrue(Capabilities.probe("26.1.2-R0.1-SNAPSHOT", false).mojangMapped());
    }

    @Test
    void foliaFlagIsCarried() {
        assertTrue(Capabilities.probe("1.20.6-R0.1-SNAPSHOT", true).folia());
        assertFalse(Capabilities.probe("1.20.6-R0.1-SNAPSHOT", false).folia());
    }
}
