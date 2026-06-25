package integrate.economy;

import java.util.UUID;
import java.util.function.Supplier;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import platform.economy.EconomyProvider;

/**
 * An {@link EconomyProvider} bridging a Vault economy backend (docs/decisions/0027) for the
 * {@code MODIFY_MONEY} family, delegated to whatever economy plugin is registered with Vault.
 *
 * <p>Bundled but SOFT: Vault's API is {@code compileOnly} and {@link integrate.Integrations} only loads this
 * class when Vault is present, so a renamed/removed method is a compile error here, not a silent runtime fail.
 *
 * <p>Lazy backend resolution: the concrete Vault {@code Economy} is resolved from the {@code ServicesManager}
 * on first use and cached — the backend often registers with Vault after StarEnchants enables. Until then,
 * balances read {@code 0}, withdrawals fail, deposits no-op (graceful degradation, as with no provider).
 *
 * <p>Threading: called on the global region thread (see the SPI), where Vault backends expect to be called.
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private final Supplier<Economy> backend;

    /**
     * @param backend resolves the live Vault {@code Economy} (nullable when no backend is registered yet);
     *     production passes a memoising {@code ServicesManager} lookup, tests pass a fixed economy
     */
    VaultEconomyProvider(Supplier<Economy> backend) {
        this.backend = backend;
    }

    /** Registrar factory — returns the SPI type (lazy-load safe); backend is the first Vault economy, cached. */
    public static EconomyProvider fromServices() {
        return new VaultEconomyProvider(new Supplier<>() {
            private volatile Economy cached;

            @Override
            public Economy get() {
                Economy local = cached;
                if (local == null) {
                    var reg = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
                    if (reg != null) {
                        cached = local = reg.getProvider();
                    }
                }
                return local;
            }
        });
    }

    @Override
    public String name() {
        Economy economy = backend.get();
        return economy == null ? "Vault" : "Vault(" + economy.getName() + ")";
    }

    @Override
    public double balance(UUID player) {
        Economy economy = backend.get();
        return economy == null ? 0.0 : economy.getBalance(offline(player));
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) {
            return true; // nothing to charge is trivially "charged"
        }
        Economy economy = backend.get();
        if (economy == null) {
            return false; // no backend — the effect did not land
        }
        OfflinePlayer target = offline(player);
        if (!economy.has(target, amount)) {
            return false; // cannot afford — no partial charge (the SPI contract)
        }
        EconomyResponse response = economy.withdrawPlayer(target, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public void deposit(UUID player, double amount) {
        if (amount <= 0) {
            return;
        }
        Economy economy = backend.get();
        if (economy != null) {
            economy.depositPlayer(offline(player), amount);
        }
    }

    /** {@code OfflinePlayer} by UUID — a local lookup (no web request, unlike the name form). */
    private static OfflinePlayer offline(UUID player) {
        return Bukkit.getOfflinePlayer(player);
    }
}
