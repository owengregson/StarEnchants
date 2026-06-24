package integrate;

import integrate.economy.VaultEconomyProvider;
import integrate.protect.FactionsProvider;
import integrate.protect.LandsProvider;
import integrate.protect.SuperiorSkyblockProvider;
import integrate.protect.TownyProvider;
import integrate.protect.WorldGuardProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyProvider;
import platform.protect.ProtectionProvider;

/**
 * Selects the third-party integration bridges that are active on THIS server (docs/decisions/0027). All
 * bridges are bundled in the core jar, but each is SOFT: it loads only when its plugin is present and is not
 * disabled in config. The composition root calls this at boot and feeds the results to the protection/economy
 * services.
 *
 * <p><b>Lazy classloading is load-bearing.</b> Each bridge references its plugin's API types, which exist on
 * the classpath only when that plugin is installed. The guard in {@link #active} touches only Strings + the
 * core {@code Plugin} type, and every bridge factory ({@code create()}/{@code fromServices()}) is declared to
 * return the first-party SPI <em>interface</em> — so the JVM never needs to load a bridge class (and thus its
 * absent plugin's API) to verify or run this registrar. A bridge class loads only when its guarded factory
 * call actually executes, i.e. only when its plugin is present. This is why the bundled-but-optional model
 * works from one jar with no hard dependency on any integration plugin.
 *
 * <p>External {@code ProtectionProvider}/{@code EconomyProvider}s registered through Bukkit's
 * {@code ServicesManager} still compose alongside these — the composition root unions both.
 */
public final class Integrations {

    private Integrations() {
    }

    /**
     * The protection bridges active on this server: a present, enabled land/region plugin contributes its
     * {@link ProtectionProvider}. Empty when none are installed.
     *
     * @param plugin  the StarEnchants plugin (for the {@code PluginManager} + Lands' API handle)
     * @param enabled the {@code integrations.named} toggle — {@code enabled.test(id)} is {@code false} only
     *     when that integration is explicitly disabled in config
     */
    public static List<ProtectionProvider> protectionProviders(Plugin plugin, Predicate<String> enabled) {
        List<ProtectionProvider> out = new ArrayList<>();
        if (active(plugin, enabled, "WorldGuard", "worldguard")) {
            out.add(WorldGuardProvider.create());
        }
        if (active(plugin, enabled, "Towny", "towny")) {
            out.add(TownyProvider.create());
        }
        if (active(plugin, enabled, "Lands", "lands")) {
            out.add(LandsProvider.create(plugin));
        }
        if (active(plugin, enabled, "SuperiorSkyblock2", "superiorskyblock")) {
            out.add(SuperiorSkyblockProvider.create());
        }
        if (active(plugin, enabled, "Factions", "factions")) {
            out.add(FactionsProvider.create());
        }
        return out;
    }

    /**
     * The bundled economy bridge, or {@code null} when Vault is absent/disabled (the composition root then
     * falls back to any externally-registered {@code EconomyProvider}). Economy is singular per server.
     */
    public static EconomyProvider economyProvider(Plugin plugin, Predicate<String> enabled) {
        return active(plugin, enabled, "Vault", "vault") ? VaultEconomyProvider.fromServices() : null;
    }

    /** Present + enabled + not disabled in config. String-only, so it never loads an absent plugin's API. */
    private static boolean active(Plugin plugin, Predicate<String> enabled, String pluginName, String configKey) {
        Plugin found = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return found != null && found.isEnabled() && enabled.test(configKey);
    }
}
