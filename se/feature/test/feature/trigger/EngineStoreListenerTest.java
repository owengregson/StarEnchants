package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.stores.PlayerScoped;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

/** The quit sweeper: engine combat windows are retained across a relog while module-declared extras are cleared. */
class EngineStoreListenerTest {

    /** A module-declared per-player store that records which players it was told to forget. */
    private static final class RecordingStore implements PlayerScoped {
        private final java.util.Set<UUID> cleared = new java.util.HashSet<>();
        @Override public void clear(UUID player) {
            cleared.add(player);
        }
    }

    @Test
    void onQuitRetainsLiveCombatWindowsClearsVolatileAndClearsEveryExtraStore() {
        UUID id = UUID.randomUUID();
        EngineStores stores = EngineStores.fresh();
        stores.vars().set(id, "x", "1", 0L, 1000);          // volatile
        stores.cooldowns().arm(id, CooldownStore.key(0, 1), 0L, 400); // retained combat window, live at the sweep tick

        RecordingStore extra = new RecordingStore();
        EngineStoreListener listener = new EngineStoreListener(stores, List.of(extra), () -> 100L);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onQuit(event);

        assertTrue(stores.vars().get(id, "x", 100L) == null, "volatile var cleared");
        assertFalse(stores.cooldowns().ready(id, CooldownStore.key(0, 1), 200L),
                "the live cooldown survives the relog against the supplied tick");
        assertTrue(extra.cleared.contains(id), "module-declared extra store cleared for the quitter");
    }
}
