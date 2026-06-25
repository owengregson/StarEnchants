package item.mint;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds identity/economy {@link ItemStack}s (soul gems, carriers, scrolls, …) from config tokens — the one
 * place that resolves a material name cross-version (cross-version-item-api) and applies a coloured name+lore,
 * so no minting site re-implements it.
 *
 * <p>Pure construction (no entity/world read) — Folia-safe from any thread; the caller picks the GIVE thread.
 * Placeholders must already be substituted (this layer only colours and assembles).
 */
public final class ItemFactory {

    private ItemFactory() {
    }

    /**
     * Resolve a config material token cross-version: exact enum name, then {@link Material#matchMaterial}
     * (namespaced/legacy spellings), else {@code fallback}. Never null, never throws — an off-server registry
     * probe degrades to the fallback rather than propagating.
     */
    public static Material material(String token, Material fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (token == null || token.isBlank()) {
            return fallback;
        }
        Material exact = Material.getMaterial(token.trim().toUpperCase(Locale.ROOT));
        if (exact != null) {
            return exact;
        }
        Material matched;
        try {
            matched = Material.matchMaterial(token.trim());
        } catch (RuntimeException registryUnavailable) {
            matched = null; // registry not initialised (off-server) — fall back, don't crash the load
        }
        return matched != null ? matched : fallback;
    }

    /** Translate legacy {@code &} colour codes in {@code raw} (null-safe → null). */
    public static String color(String raw) {
        return raw == null ? null : ChatColor.translateAlternateColorCodes('&', raw);
    }

    /** Coloured name + {@code &}-translated lore on a {@code material} stack; blank name / empty lore left unset. */
    public static ItemStack build(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    /**
     * §N custom-item resolver (ADR-0027): ItemsAdder/Oraxen token → custom {@link ItemStack}, or {@code null}
     * for a vanilla token. Static no-op default so this module never references an integration API and is inert
     * without them; the root installs the live one via {@link #customItemResolver}.
     */
    private static volatile java.util.function.Function<String, ItemStack> customItemResolver = token -> null;

    /** Install the ItemsAdder/Oraxen custom-item resolver (boot-time). A {@code null} resets to no-op. */
    public static void customItemResolver(java.util.function.Function<String, ItemStack> resolver) {
        customItemResolver = resolver == null ? token -> null : resolver;
    }

    /**
     * Build from a config TOKEN: a recognised ItemsAdder/Oraxen custom item is the base, else the token resolves
     * vanilla via {@link #material}. {@code name}/{@code lore} apply on top; blank leaves the custom item's own.
     */
    public static ItemStack build(String token, Material fallback, String name, List<String> lore) {
        ItemStack custom = customItemResolver.apply(token);
        ItemStack base = custom != null ? custom.clone() : new ItemStack(material(token, fallback));
        return decorate(base, name, lore);
    }

    @SuppressWarnings("deprecation") // setDisplayName/setLore(String/List): the floor-stable item-meta path
    private static ItemStack decorate(ItemStack stack, String name, List<String> lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isBlank()) {
                meta.setDisplayName(color(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(ItemFactory::color).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
