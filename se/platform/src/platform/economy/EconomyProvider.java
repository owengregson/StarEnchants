package platform.economy;

import java.util.UUID;

/**
 * A first-party economy SPI (docs/architecture.md §2) for money enchants (GIVE_MONEY / TAKE_MONEY, a
 * Cosmic Enchants-style "trade"/"tax" family). One implementation bridges one backend (Vault, or a
 * server's own); register through Bukkit's {@code ServicesManager}. Invoked on the GLOBAL region thread
 * and must complete promptly (no blocking I/O); a provider needing off-thread work schedules it itself.
 */
public interface EconomyProvider {

    double balance(UUID player);

    /**
     * {@code true} iff the full amount was charged; {@code false} if unaffordable (no partial charge).
     * A non-positive amount is a no-op that returns {@code true}.
     */
    boolean withdraw(UUID player, double amount);

    /** Deposit {@code amount} into {@code player}'s account. A non-positive amount is a no-op. */
    void deposit(UUID player, double amount);

    /** A short id for logging/diagnostics (e.g. {@code "Vault"}). */
    default String name() {
        return getClass().getSimpleName();
    }
}
