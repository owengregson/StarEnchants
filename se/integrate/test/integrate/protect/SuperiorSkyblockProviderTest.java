package integrate.protect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.junit.jupiter.api.Test;

/**
 * Pins the gate decision of {@link SuperiorSkyblockProvider} against a mocked {@code Island}. End-to-end with
 * real SuperiorSkyblock is verified out-of-matrix (docs/decisions/0027).
 */
class SuperiorSkyblockProviderTest {

    @Test
    void offIslandAllows() {
        assertTrue(SuperiorSkyblockProvider.buildAllowed(
                null, mock(SuperiorPlayer.class), mock(IslandPrivilege.class)));
    }

    @Test
    void unresolvableBuildPrivilegeAllows() {
        assertTrue(SuperiorSkyblockProvider.buildAllowed(mock(Island.class), mock(SuperiorPlayer.class), null));
    }

    @Test
    void onIslandDefersToBuildPrivilege() {
        Island island = mock(Island.class);
        SuperiorPlayer player = mock(SuperiorPlayer.class);
        IslandPrivilege build = mock(IslandPrivilege.class);

        when(island.hasPermission(player, build)).thenReturn(true);
        assertTrue(SuperiorSkyblockProvider.buildAllowed(island, player, build));

        when(island.hasPermission(player, build)).thenReturn(false);
        assertFalse(SuperiorSkyblockProvider.buildAllowed(island, player, build));
    }
}
