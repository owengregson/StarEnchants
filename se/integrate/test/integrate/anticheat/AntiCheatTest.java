package integrate.anticheat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

/**
 * Pins the fail-safe behaviour of {@link AntiCheat}: with no supported anti-cheat installed, the exemption
 * hook is a non-throwing no-op. The reflective NoCheatPlus path is verified out-of-matrix (docs/decisions/0027).
 */
class AntiCheatTest {

    @Test
    void noAntiCheatPresentYieldsNonThrowingNoOp() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager pm = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pm);
        when(pm.getPlugin(anyString())).thenReturn(null); // nothing installed

        Consumer<Player> exemption = AntiCheat.exemption(plugin, id -> true, System.getLogger("test"));
        assertNotNull(exemption);
        assertDoesNotThrow(() -> exemption.accept(mock(Player.class)));
    }
}
