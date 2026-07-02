package bootstrap.compat;

import integrate.Integrations;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyProvider;
import platform.protect.ProtectionProvider;

/**
 * Modern composition access to the third-party integration bridges (ADR-0027) — a thin delegate to
 * {@link Integrations}. Same-FQN counterpart to the {@code overlay/legacy} impl: {@code :integrate} is
 * EXCLUDED from the 1.8 tree (its bridged plugin APIs are modern-Bukkit-typed and cannot dual-compile on
 * 1.8), so the composition root reaches those bridges only through this seam, and the legacy impl returns the
 * same neutral defaults {@code Integrations} itself yields when a plugin is absent
 * (docs/legacy-1.8.9-codeshare-design.md §6, gate list).
 */
public final class Bridges {

    private Bridges() {
    }

    public static BiFunction<Player, String, String> placeholderResolver(Plugin plugin, Predicate<String> enabled) {
        return Integrations.placeholderResolver(plugin, enabled);
    }

    public static void registerPlaceholders(Plugin plugin, Predicate<String> enabled,
                                            Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        Integrations.registerPlaceholders(plugin, enabled, soulMode, souls);
    }

    public static List<ProtectionProvider> protectionProviders(Plugin plugin, Predicate<String> enabled) {
        return Integrations.protectionProviders(plugin, enabled);
    }

    public static EconomyProvider economyProvider(Plugin plugin, Predicate<String> enabled) {
        return Integrations.economyProvider(plugin, enabled);
    }

    public static Consumer<Player> antiCheatExemption(Plugin plugin, Predicate<String> enabled, System.Logger log) {
        return Integrations.antiCheatExemption(plugin, enabled, log);
    }

    public static BiPredicate<Player, Player> mcmmoFriendlyFire(Plugin plugin, Predicate<String> enabled) {
        return Integrations.mcmmoFriendlyFire(plugin, enabled);
    }

    public static Function<Entity, String> mythicMobType(Plugin plugin, Predicate<String> enabled) {
        return Integrations.mythicMobType(plugin, enabled);
    }

    public static Function<String, ItemStack> customItem(Plugin plugin, Predicate<String> enabled) {
        return Integrations.customItem(plugin, enabled);
    }
}
