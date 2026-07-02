package bootstrap.compat;

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
 * Legacy (1.8.9) composition access to the integration bridges — same-FQN counterpart to the
 * {@code overlay/modern} impl. {@code :integrate} is EXCLUDED from the 1.8 tree (its bridged plugin APIs are
 * modern-Bukkit-typed and cannot dual-compile on 1.8), so these return the SAME neutral defaults the modern
 * {@code Integrations} yields when a plugin is absent — and every bridged plugin is a modern-only plugin that
 * cannot be installed on 1.8.9, so on 1.8 the modern path would return exactly these too
 * (docs/legacy-1.8.9-codeshare-design.md §6, gate list).
 */
public final class Bridges {

    private Bridges() {
    }

    public static BiFunction<Player, String, String> placeholderResolver(Plugin plugin, Predicate<String> enabled) {
        return (player, text) -> text; // identity: no PlaceholderAPI on 1.8
    }

    public static void registerPlaceholders(Plugin plugin, Predicate<String> enabled,
                                            Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        // no PlaceholderAPI on 1.8 — nothing to register
    }

    public static List<ProtectionProvider> protectionProviders(Plugin plugin, Predicate<String> enabled) {
        return List.of();
    }

    public static EconomyProvider economyProvider(Plugin plugin, Predicate<String> enabled) {
        return null; // no Vault bridge on 1.8; the ServicesManager discovery path still runs
    }

    public static Consumer<Player> antiCheatExemption(Plugin plugin, Predicate<String> enabled, System.Logger log) {
        return player -> { };
    }

    public static BiPredicate<Player, Player> mcmmoFriendlyFire(Plugin plugin, Predicate<String> enabled) {
        return (attacker, victim) -> false;
    }

    public static Function<Entity, String> mythicMobType(Plugin plugin, Predicate<String> enabled) {
        return entity -> "";
    }

    public static Function<String, ItemStack> customItem(Plugin plugin, Predicate<String> enabled) {
        return material -> null;
    }
}
