package feature.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.stores.KeepOnDeathStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins the KEEP_ON_DEATH applier: an armed flag keeps the inventory + levels and clears drops; the
 * keepInventory-gamerule guard (also the "an earlier handler / holy scroll already kept" coordination)
 * short-circuits; and no flag leaves the death untouched.
 */
class KeepOnDeathListenerTest {

    private static final LongSupplier NOW = () -> 50L;

    private PlayerDeathEvent eventFor(UUID playerId, boolean alreadyKeeping, List<ItemStack> drops) {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(event.getEntity()).thenReturn(player);
        when(event.getKeepInventory()).thenReturn(alreadyKeeping);
        when(event.getDrops()).thenReturn(drops);
        return event;
    }

    @Test
    void keepsInventoryWhenArmed() {
        KeepOnDeathStore store = new KeepOnDeathStore();
        UUID player = UUID.randomUUID();
        store.keep(player, 0L, 100);

        List<ItemStack> drops = new ArrayList<>(List.of(mock(ItemStack.class)));
        PlayerDeathEvent event = eventFor(player, false, drops);
        new KeepOnDeathListener(store, NOW).onDeath(event);

        verify(event).setKeepInventory(true);
        verify(event).setKeepLevel(true);
        verify(event).setDroppedExp(0);
        assertTrue(drops.isEmpty(), "drops cleared so the kept inventory is not duplicated");
    }

    @Test
    void doesNothingWhenGameruleAlreadyKeeps() {
        KeepOnDeathStore store = new KeepOnDeathStore();
        UUID player = UUID.randomUUID();
        store.keep(player, 0L, 100); // armed, but the gamerule (or an earlier keep) already retains

        PlayerDeathEvent event = eventFor(player, true, new ArrayList<>());
        new KeepOnDeathListener(store, NOW).onDeath(event);

        verify(event, never()).setKeepInventory(anyBoolean());
        verify(event, never()).setDroppedExp(0);
    }

    @Test
    void doesNothingWhenNotArmed() {
        KeepOnDeathStore store = new KeepOnDeathStore(); // nothing armed
        PlayerDeathEvent event = eventFor(UUID.randomUUID(), false, new ArrayList<>());

        new KeepOnDeathListener(store, NOW).onDeath(event);

        verify(event, never()).setKeepInventory(anyBoolean());
    }
}
