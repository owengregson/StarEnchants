package addonworldguard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.junit.jupiter.api.Test;

/**
 * Pins the BUILD-flag wiring of {@link WorldGuardProvider} against a mocked WorldGuard {@link RegionQuery}
 * — and, by compiling against the real WorldGuard API, proves the add-on's API usage is correct. The
 * static-singleton plumbing (actor resolution, bypass) is exercised end-to-end on a WorldGuard server,
 * not here.
 */
class WorldGuardProviderTest {

    @Test
    void buildAllowedPassesThroughWorldGuardsVerdict() {
        RegionQuery query = mock(RegionQuery.class);
        com.sk89q.worldedit.util.Location at = mock(com.sk89q.worldedit.util.Location.class);
        LocalPlayer subject = mock(LocalPlayer.class); // the resolved online actor (the only production path)

        // The provider returns exactly WorldGuard's BUILD verdict for that player at that location.
        when(query.testState(at, subject, Flags.BUILD)).thenReturn(true);
        assertTrue(WorldGuardProvider.buildAllowed(query, at, subject), "BUILD allowed → provider allows");

        when(query.testState(at, subject, Flags.BUILD)).thenReturn(false);
        assertFalse(WorldGuardProvider.buildAllowed(query, at, subject), "BUILD denied → provider denies");
    }
}
