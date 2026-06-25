package integrate;

import integrate.anticheat.AntiCheat;
import integrate.combat.Mcmmo;
import integrate.economy.VaultEconomyProvider;
import integrate.entity.MythicMobs;
import integrate.item.CustomItems;
import org.bukkit.inventory.ItemStack;
import integrate.papi.PapiPassthrough;
import integrate.papi.SePlaceholderExpansion;
import integrate.protect.FactionsProvider;
import integrate.protect.LandsProvider;
import integrate.protect.SuperiorSkyblockProvider;
import integrate.protect.TownyProvider;
import integrate.protect.WorldGuardProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyProvider;
import platform.protect.ProtectionProvider;

/**
 * Selects the third-party integration bridges active on this server (docs/decisions/0027); each bridge is
 * bundled but soft, loading only when its plugin is present and not disabled in config.
 *
 * <p>Lazy classloading is load-bearing: a bridge references its plugin's API types, so the JVM must only load
 * it when that plugin is installed. {@link #active} touches only Strings + the core {@code Plugin} type, and
 * every factory returns the first-party SPI interface, so a bridge class loads only when its guarded factory
 * actually runs.
 */
public final class Integrations {

    private Integrations() {
    }

    /**
     * The protection bridges active on this server; empty when none are installed.
     *
     * @param enabled the {@code integrations.named} toggle — {@code false} only when explicitly disabled
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

    /** The bundled economy bridge, or {@code null} when Vault is absent/disabled. */
    public static EconomyProvider economyProvider(Plugin plugin, Predicate<String> enabled) {
        return active(plugin, enabled, "Vault", "vault") ? VaultEconomyProvider.fromServices() : null;
    }

    /** Register the {@code %starenchants_…%} PlaceholderAPI expansion when PlaceholderAPI is present + enabled. */
    public static void registerPlaceholders(Plugin plugin, Predicate<String> enabled,
                                            Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        if (active(plugin, enabled, "PlaceholderAPI", "placeholderapi")) {
            SePlaceholderExpansion.install(plugin.getDescription().getVersion(), soulMode, souls);
        }
    }

    /**
     * The PlaceholderAPI passthrough resolver for player-facing chat. Returns an identity resolver when
     * PlaceholderAPI is absent/disabled, so the message path can call it unconditionally.
     */
    public static BiFunction<Player, String, String> placeholderResolver(Plugin plugin, Predicate<String> enabled) {
        if (!active(plugin, enabled, "PlaceholderAPI", "placeholderapi")) {
            return (player, text) -> text;
        }
        return PapiPassthrough.resolver();
    }

    /**
     * The anti-cheat movement-exemption hook so engine-applied velocity/teleport doesn't trip movement checks,
     * or a no-op when none is actionable.
     */
    public static Consumer<Player> antiCheatExemption(Plugin plugin, Predicate<String> enabled, System.Logger log) {
        return AntiCheat.exemption(plugin, enabled, log);
    }

    /** The mcMMO friendly-fire gate, or a constant {@code false} when mcMMO is absent/disabled. */
    public static BiPredicate<Player, Player> mcmmoFriendlyFire(Plugin plugin, Predicate<String> enabled) {
        return enabled.test("mcmmo") ? Mcmmo.sameParty(plugin) : (attacker, victim) -> false;
    }

    /** The engine's {@code %victim.mobtype%} source, or a constant {@code ""} when MythicMobs is absent/disabled. */
    public static java.util.function.Function<org.bukkit.entity.Entity, String> mythicMobType(
            Plugin plugin, Predicate<String> enabled) {
        return enabled.test("mythicmobs") ? MythicMobs.mobType(plugin) : entity -> "";
    }

    /**
     * The custom-item resolver letting configs use {@code itemsadder:…} / {@code oraxen:…} materials, or a
     * constant {@code null} resolver when neither plugin is present/enabled.
     */
    public static java.util.function.Function<String, ItemStack> customItem(Plugin plugin, Predicate<String> enabled) {
        return CustomItems.resolver(plugin, enabled);
    }

    /** Present + enabled + not disabled in config. String-only, so it never loads an absent plugin's API. */
    private static boolean active(Plugin plugin, Predicate<String> enabled, String pluginName, String configKey) {
        Plugin found = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return found != null && found.isEnabled() && enabled.test(configKey);
    }
}
