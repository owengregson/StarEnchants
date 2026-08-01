package feature.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.SoulGemConfig;
import engine.interact.SoulPool;
import engine.stores.SoulModeStore;
import feature.compat.Hands;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import item.codec.SoulCodec;
import item.codec.SoulData;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

final class SoulCarriedSpendTest {

    @Test
    void spendsPhysicalCarriedSoulsWithoutSoulMode() {
        Fixture f = fixture(12);

        assertTrue(f.service().trySpendCarried(f.player(), 5));
        assertEquals(7, f.data().get().souls());
        assertEquals(7, f.service().currentTotal(f.player()));
    }

    @Test
    void insufficientBalanceDoesNotPartiallyDrain() {
        Fixture f = fixture(4);

        assertFalse(f.service().trySpendCarried(f.player(), 5));
        assertEquals(4, f.data().get().souls());
        assertEquals(4, f.service().currentTotal(f.player()));
        verify(f.codec(), never()).write(any(ItemStack.class), any(SoulData.class));
    }

    private static Fixture fixture(int souls) {
        UUID playerId = UUID.randomUUID();
        AtomicReference<SoulData> data = new AtomicReference<>(new SoulData(UUID.randomUUID(), souls));
        ItemStack gem = mock(ItemStack.class);

        SoulCodec codec = mock(SoulCodec.class);
        when(codec.read(gem)).thenAnswer(ignored -> data.get());
        doAnswer(invocation -> {
            data.set(invocation.getArgument(1));
            return null;
        }).when(codec).write(any(ItemStack.class), any(SoulData.class));

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[] {gem});
        when(inventory.getItem(0)).thenReturn(gem);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);

        SoulService service = new SoulService(new SoulPool(), new SoulModeStore(), codec,
                SoulGemConfig::defaults, () -> true, platform.lang.Messages.defaults(),
                mock(ParticleFx.class), mock(Hands.class), Sounds.NONE);
        return new Fixture(service, player, codec, data);
    }

    private record Fixture(SoulService service, Player player, SoulCodec codec,
                           AtomicReference<SoulData> data) {
    }
}