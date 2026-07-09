package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.TempEquip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pins the keepInventory-aware temp-equip death handling: the minted placeholder is deleted exactly once and
 * the real piece returned exactly once (never both the real piece AND a minted copy — F06/F22), with the drops
 * vs kept-inventory choice tracking the death's keep state and a MONITOR reconcile for a late keep flip.
 */
class TempEquipListenerTest {

    private static final Material PUMPKIN = Material.CARVED_PUMPKIN;
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clean() {
        TempEquip.clearAll();
    }

    private Player playerWith(ItemStack[] armor, PlayerInventory inv) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getArmorContents()).thenReturn(armor);
        when(inv.removeItem(any())).thenReturn(new HashMap<>());
        return player;
    }

    private PlayerDeathEvent deathEvent(Player player, List<ItemStack> drops, boolean... keepReads) {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(drops);
        Boolean[] rest = new Boolean[keepReads.length - 1];
        for (int i = 1; i < keepReads.length; i++) {
            rest[i - 1] = keepReads[i];
        }
        when(event.getKeepInventory()).thenReturn(keepReads[0], rest);
        return event;
    }

    private static long countOfType(List<ItemStack> drops, Material type) {
        return drops.stream().filter(d -> d != null && d.getType() == type).count();
    }

    @Test
    void keepInventoryReturnsRealToSlotAndDropsNothing() {
        ItemStack real = new ItemStack(Material.DIAMOND_HELMET);
        TempEquip.swap(id, 3, real, PUMPKIN);
        ItemStack[] armor = new ItemStack[4];
        armor[3] = new ItemStack(PUMPKIN); // the pumpkin is still worn
        PlayerInventory inv = mock(PlayerInventory.class);
        Player player = playerWith(armor, inv);
        List<ItemStack> drops = new ArrayList<>(); // keepInventory → vanilla drops list is empty
        PlayerDeathEvent event = deathEvent(player, drops, true);

        new TempEquipListener().onDeath(event);

        assertTrue(drops.isEmpty(), "keepInventory: nothing is added to the drops");
        assertSame(real, armor[3], "the real piece is put back into the worn slot the player keeps");
        // The minted placeholder is reclaimed (captor, not equals — ItemStack.equals needs a live server).
        ArgumentCaptor<ItemStack> reclaimed = ArgumentCaptor.forClass(ItemStack.class);
        verify(inv).removeItem(reclaimed.capture());
        assertEquals(PUMPKIN, reclaimed.getValue().getType());
        assertEquals(1, reclaimed.getValue().getAmount());
    }

    @Test
    void dropsContainTheRealPieceOnceAndNoPlaceholder() {
        ItemStack real = new ItemStack(Material.DIAMOND_HELMET);
        TempEquip.swap(id, 3, real, PUMPKIN);
        ItemStack[] armor = new ItemStack[4];
        armor[3] = new ItemStack(PUMPKIN);
        PlayerInventory inv = mock(PlayerInventory.class);
        Player player = playerWith(armor, inv);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(PUMPKIN))); // vanilla pre-populates from the worn slot
        PlayerDeathEvent event = deathEvent(player, drops, false);

        new TempEquipListener().onDeath(event);

        assertEquals(1, countOfType(drops, Material.DIAMOND_HELMET), "the real helmet drops exactly once");
        assertEquals(0, countOfType(drops, PUMPKIN), "the minted placeholder never drops");
    }

    @Test
    void relocatedPlaceholderIsDecrementedNotDoubleCollected() {
        // F22: the victim moved the pumpkin out of the helmet slot into a stack, then died in the window.
        ItemStack real = new ItemStack(Material.DIAMOND_HELMET);
        TempEquip.swap(id, 3, real, PUMPKIN);
        ItemStack[] armor = new ItemStack[4]; // slot 3 empty — the pumpkin was relocated
        PlayerInventory inv = mock(PlayerInventory.class);
        Player player = playerWith(armor, inv);
        ItemStack pumpkins = new ItemStack(PUMPKIN, 2); // one minted + one the victim already owned
        List<ItemStack> drops = new ArrayList<>(List.of(pumpkins));
        PlayerDeathEvent event = deathEvent(player, drops, false);

        new TempEquipListener().onDeath(event);

        assertEquals(1, countOfType(drops, Material.DIAMOND_HELMET), "the real helmet drops exactly once");
        assertEquals(1, pumpkins.getAmount(), "exactly the one minted pumpkin is removed, not the owned one");
    }

    @Test
    void lateKeepFlipPullsStagedRealBackOutOfDrops() {
        ItemStack real = new ItemStack(Material.DIAMOND_HELMET);
        TempEquip.swap(id, 3, real, PUMPKIN);
        ItemStack[] armor = new ItemStack[4];
        armor[3] = new ItemStack(PUMPKIN);
        PlayerInventory inv = mock(PlayerInventory.class);
        Player player = playerWith(armor, inv);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(PUMPKIN)));
        // LOWEST reads keep=false (stages real into drops); a later handler flips it true before MONITOR.
        PlayerDeathEvent event = deathEvent(player, drops, false, true);

        TempEquipListener listener = new TempEquipListener();
        listener.onDeath(event);
        assertEquals(1, countOfType(drops, Material.DIAMOND_HELMET), "staged into drops while keep was false");

        listener.onDeathReconcile(event);
        assertFalse(drops.stream().anyMatch(d -> d == real), "the staged real piece is pulled back out — no dupe");
        assertSame(real, armor[3], "the kept inventory still holds the real piece in the worn slot");
    }
}
