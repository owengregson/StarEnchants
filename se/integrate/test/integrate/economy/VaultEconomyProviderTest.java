package integrate.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Pins {@link VaultEconomyProvider}'s wiring against a mocked backend. End-to-end with a real Vault economy is
 * verified out-of-matrix (docs/decisions/0027).
 */
class VaultEconomyProviderTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Test
    void withdrawFailsAndDoesNotChargeWhenUnaffordable() {
        Economy economy = mock(Economy.class);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(PLAYER)).thenReturn(offline);
            when(economy.has(offline, 100.0)).thenReturn(false);

            VaultEconomyProvider provider = new VaultEconomyProvider(() -> economy);
            assertFalse(provider.withdraw(PLAYER, 100.0), "cannot afford ⇒ withdraw fails");
            verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
        }
    }

    @Test
    void withdrawChargesWhenAffordable() {
        Economy economy = mock(Economy.class);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(PLAYER)).thenReturn(offline);
            when(economy.has(offline, 50.0)).thenReturn(true);
            when(economy.withdrawPlayer(offline, 50.0))
                    .thenReturn(new EconomyResponse(50.0, 150.0, ResponseType.SUCCESS, null));

            VaultEconomyProvider provider = new VaultEconomyProvider(() -> economy);
            assertTrue(provider.withdraw(PLAYER, 50.0));
            verify(economy).withdrawPlayer(offline, 50.0);
        }
    }

    @Test
    void depositDelegatesToBackend() {
        Economy economy = mock(Economy.class);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(PLAYER)).thenReturn(offline);

            new VaultEconomyProvider(() -> economy).deposit(PLAYER, 25.0);
            verify(economy).depositPlayer(offline, 25.0);
        }
    }

    @Test
    void absentBackendDegradesGracefully() {
        VaultEconomyProvider provider = new VaultEconomyProvider(() -> null);
        assertEquals(0.0, provider.balance(PLAYER));
        assertFalse(provider.withdraw(PLAYER, 10.0));
        provider.deposit(PLAYER, 10.0); // must not throw
        assertEquals("Vault", provider.name());
    }

    @Test
    void nonPositiveWithdrawIsTriviallyCharged() {
        VaultEconomyProvider provider = new VaultEconomyProvider(() -> mock(Economy.class));
        assertTrue(provider.withdraw(PLAYER, 0.0));
        assertTrue(provider.withdraw(PLAYER, -5.0));
    }
}
