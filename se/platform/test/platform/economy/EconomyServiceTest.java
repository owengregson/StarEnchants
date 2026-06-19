package platform.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link EconomyService#balance} — delegates to the provider, and is fault/absence-isolated to 0.0. */
class EconomyServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void balanceDelegatesToTheProvider() {
        EconomyService economy = new EconomyService(new FixedBalance(123.0));
        assertEquals(123.0, economy.balance(ID));
    }

    @Test
    void absentEconomyReportsZero() {
        assertEquals(0.0, EconomyService.NONE.balance(ID));
        assertEquals(0.0, new EconomyService(null).balance(ID));
    }

    @Test
    void aThrowingProviderIsIsolatedToZero() {
        EconomyService economy = new EconomyService(new EconomyProvider() {
            @Override
            public double balance(UUID player) {
                throw new RuntimeException("boom");
            }

            @Override
            public boolean withdraw(UUID player, double amount) {
                return false;
            }

            @Override
            public void deposit(UUID player, double amount) {
            }
        });
        assertEquals(0.0, economy.balance(ID)); // warns once, never propagates
    }

    /** A trivial provider returning a fixed balance, charging nothing. */
    private record FixedBalance(double amount) implements EconomyProvider {
        @Override
        public double balance(UUID player) {
            return amount;
        }

        @Override
        public boolean withdraw(UUID player, double withdrawn) {
            return true;
        }

        @Override
        public void deposit(UUID player, double deposited) {
        }
    }
}
