package feature.soul;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.SoulGemConfig;
import engine.interact.SoulLedger;
import engine.stores.SoulModeStore;
import item.codec.SoulCodec;
import item.codec.SoulData;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/**
 * Soul mode must auto-disable the moment the active gem is no longer usable — dropped/moved out of the
 * inventory, or drained to zero souls — and tell the player. Pins {@link SoulService#enforceActiveGem} (the
 * fix for soul mode lingering after the last gem was dropped or emptied).
 */
class SoulModeEnforcementTest {

    private record Fixture(SoulService service, SoulModeStore modes, Player player, UUID playerId) {
    }

    private static Fixture setUp(boolean gemInInventory, int souls) {
        UUID playerId = UUID.randomUUID();
        UUID gemId = UUID.randomUUID();

        ItemStack gemStack = mock(ItemStack.class);
        SoulCodec codec = mock(SoulCodec.class);
        when(codec.read(gemStack)).thenReturn(new SoulData(gemId, souls));

        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getContents()).thenReturn(gemInInventory ? new ItemStack[] {gemStack} : new ItemStack[] {null});

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inv);

        SoulLedger ledger = new SoulLedger();
        ledger.balance(gemId, seed(souls)); // seed the live authority so peek() == souls (as toggle-on does)
        SoulModeStore modes = new SoulModeStore();
        modes.activate(playerId, gemId);

        return new Fixture(new SoulService(ledger, modes, codec, SoulGemConfig::defaults), modes, player, playerId);
    }

    private static SoulLedger.Balance seed(int souls) {
        return new SoulLedger.Balance() {
            @Override
            public int souls() {
                return souls;
            }

            @Override
            public void setSouls(int next) {
            }
        };
    }

    @Test
    void keepsSoulModeWhenTheActiveGemIsPresentWithSouls() {
        Fixture f = setUp(true, 50);
        f.service().enforceActiveGem(f.player());
        assertTrue(f.modes().isActive(f.playerId()));
        verify(f.player(), never()).sendMessage(anyString());
    }

    @Test
    void disablesSoulModeWhenTheActiveGemHasLeftTheInventory() {
        Fixture f = setUp(false, 50); // dropped/moved — gone from the inventory even though it still had souls
        f.service().enforceActiveGem(f.player());
        assertFalse(f.modes().isActive(f.playerId()));
        verify(f.player(), atLeastOnce()).sendMessage(anyString()); // the "no soul gems left" banner
    }

    @Test
    void disablesSoulModeWhenTheActiveGemIsDrainedToZero() {
        Fixture f = setUp(true, 0); // present but empty
        f.service().enforceActiveGem(f.player());
        assertFalse(f.modes().isActive(f.playerId()));
    }
}
