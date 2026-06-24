package integrate.protect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.junit.jupiter.api.Test;

/**
 * Pins the BUILD-flag wiring of {@link WorldGuardProvider} against a mocked {@code RegionQuery} (proving the
 * add-on uses the real WorldGuard API too). End-to-end with WorldGuard installed is verified on a WorldGuard
 * server out-of-matrix (docs/decisions/0027).
 */
class WorldGuardProviderTest {

    @Test
    void allowsWhenBuildFlagAllows() {
        RegionQuery query = mock(RegionQuery.class);
        Location at = mock(Location.class);
        LocalPlayer subject = mock(LocalPlayer.class);
        when(query.testState(at, subject, Flags.BUILD)).thenReturn(true);
        assertTrue(WorldGuardProvider.buildAllowed(query, at, subject));
    }

    @Test
    void deniesWhenBuildFlagDenies() {
        RegionQuery query = mock(RegionQuery.class);
        Location at = mock(Location.class);
        LocalPlayer subject = mock(LocalPlayer.class);
        when(query.testState(at, subject, Flags.BUILD)).thenReturn(false);
        assertFalse(WorldGuardProvider.buildAllowed(query, at, subject));
    }
}
