package integrate.economy;

import java.util.UUID;
import java.util.function.Supplier;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import platform.economy.EconomyProvider;

/**
 * An {@link EconomyProvider} bridging a Vault economy backend (docs/decisions/0027): the narrow money
 * contract StarEnchants uses for the {@code MODIFY_MONEY} family, delegated to whatever economy plugin
 * (EssentialsX, CMI, …) is registered with Vault.
 *
 * <p>Bundled but SOFT: Vault's API is {@code compileOnly} and {@link integrate.Integrations} only loads this
 * class when Vault is present. Compiling against the real Vault API (not reflection) means a renamed/removed
 * method is a compile error here, not a silent runtime failure.
 *
 * <p><b>Lazy backend resolution.</b> The concrete Vault {@code Economy} is resolved from Bukkit's
 * {@code ServicesManager} on first use and cached — the economy backend often registers with Vault after
 * StarEnchants enables. Until a backend is present, balances read {@code 0}, withdrawals fail, deposits
 * no-op (the same graceful degradation {@link platform.economy.EconomyService} applies with no provider).
 *
 * <p><b>Threading.</b> StarEnchants calls an {@link EconomyProvider} on the global region thread (see the
 * SPI), which is where Vault economy backends expect to be called.
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

    /**
     * Factory used by the registrar — returns the SPI type (lazy-load safe) whose backend is the first
     * {@code Economy} registered with Vault, resolved once and cached.
     */
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
