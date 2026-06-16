package platform.economy;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * The economy facade the runtime calls for money effects (docs/architecture.md §2). Wraps the single
 * registered {@link EconomyProvider} (economy is singular per server) and isolates its faults: a
 * provider that throws is logged once and treated as "no money moved", so a misbehaving economy bridge
 * never aborts a hit. With no provider present, withdraw fails and deposit is a no-op — a money effect
 * simply does nothing on a server without an economy, rather than erroring.
 *
 * <p>Calls arrive on the global region thread (see {@link EconomyProvider}); this class itself holds no
 * mutable per-call state and is safe to share.
 */
public final class EconomyService {

    /** The absent economy — no provider; withdraw always fails, deposit no-ops. */
    public static final EconomyService NONE = new EconomyService(null);

    private final EconomyProvider provider; // nullable = absent
    private final System.Logger log = System.getLogger("StarEnchants.Economy");
    private final java.util.Set<String> warned = ConcurrentHashMap.newKeySet();

    public EconomyService(EconomyProvider provider) {
        this.provider = provider;
    }

    /** Whether an economy provider is wired (a money effect is a no-op when false). */
    public boolean present() {
        return provider != null;
    }

    /** Withdraw {@code amount} from {@code player}; {@code true} iff fully charged. Absent economy ⇒ false. */
    public boolean withdraw(UUID player, double amount) {
        if (provider == null || player == null || amount <= 0) {
            return amount <= 0; // nothing to charge is trivially "charged"
        }
        try {
            return provider.withdraw(player, amount);
        } catch (Throwable failed) {
            warnOnce(provider.name(), failed);
            return false;
        }
    }

    /** Deposit {@code amount} into {@code player}'s account; absent economy ⇒ no-op. */
    public void deposit(UUID player, double amount) {
        if (provider == null || player == null || amount <= 0) {
            return;
        }
        try {
            provider.deposit(player, amount);
        } catch (Throwable failed) {
            warnOnce(provider.name(), failed);
        }
    }

    /** Log the first failure from a provider (by name), then stay quiet so a broken bridge can't spam. */
    private void warnOnce(String providerName, Throwable failed) {
        if (warned.add(providerName)) {
            log.log(System.Logger.Level.WARNING,
                    "economy provider '" + providerName + "' threw; the money operation was skipped", failed);
        }
    }

    /**
     * The economy wrapping the first {@link EconomyProvider} registered via the server's
     * {@code ServicesManager}, or {@link #NONE} when none is registered (a region/economy plugin
     * registers one; it must do so before StarEnchants enables).
     */
    public static EconomyService discover(Server server, System.Logger log) {
        RegisteredServiceProvider<EconomyProvider> reg =
                server.getServicesManager().getRegistration(EconomyProvider.class);
        if (reg == null || reg.getProvider() == null) {
            return NONE;
        }
        log.log(System.Logger.Level.INFO, "economy: provider '" + reg.getProvider().name()
                + "' registered by " + reg.getPlugin().getName());
        return new EconomyService(reg.getProvider());
    }
}
