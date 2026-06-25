package platform.economy;

import java.util.UUID;

/**
 * A first-party economy SPI (docs/architecture.md §1, §2): the narrow contract StarEnchants uses to
 * move money for money enchants (GIVE_MONEY / TAKE_MONEY, a Cosmic Enchants-style "trade"/"tax" family). One
 * implementation bridges one economy backend (Vault, or a server's own); register it through Bukkit's
 * {@code ServicesManager}.
 *
 * <p><b>Threading.</b> StarEnchants calls a provider on the server's GLOBAL region thread (the main
 * thread on Paper) — the thread most economy backends expect, and the Folia-consistent analog — never
 * inline on a combat region thread. Calls must complete promptly (do no blocking I/O on the calling
 * thread); a provider that needs off-thread work schedules it itself.
 */
public interface EconomyProvider {

    /** The player's current balance. */
    double balance(UUID player);

    /**
     * Withdraw {@code amount} from {@code player}. Returns {@code true} if the full amount was charged,
     * {@code false} if the player could not afford it (no partial charge) — so a TAKE_MONEY effect only
     * "lands" what was actually taken. A non-positive amount is a no-op that returns {@code true}.
     */
    boolean withdraw(UUID player, double amount);

    /** Deposit {@code amount} into {@code player}'s account. A non-positive amount is a no-op. */
    void deposit(UUID player, double amount);

    /** A short id for logging/diagnostics (e.g. {@code "Vault"}). */
    default String name() {
        return getClass().getSimpleName();
    }
}
