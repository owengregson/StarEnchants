package item.mint;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds identity/economy {@link ItemStack}s (soul gems, carriers, scrolls, …) from configured tokens
 * — the ONE place that resolves a config material name cross-version and applies a coloured name + lore.
 * Centralises what every minting site (CarrierService, SoulService, …) needs, so the cross-version
 * material concern (cross-version-item-api) lives in a single resolver rather than being re-implemented
 * per site.
 *
 * <p>Pure construction (no entity/world read) — Folia-safe to call from any thread; the caller decides
 * which thread to GIVE the resulting stack on. Placeholders in the strings must already be substituted
 * by the caller (this layer only colours and assembles).
 */
public final class ItemFactory {

    private ItemFactory() {
    }

    /**
     * Resolve a config material token to a {@link Material}, cross-version: exact enum name first (e.g.
     * {@code EMERALD}), then {@link Material#matchMaterial} (namespaced / some legacy spellings), falling
     * back to {@code fallback} for an unknown/blank token. Never null, never throws — a registry probe
     * that fails (e.g. a unit test with no server) degrades to the fallback rather than propagating.
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
            matched = null; // registry not initialised (off-server) — fall back rather than crash a load
        }
        return matched != null ? matched : fallback;
    }

    /** Translate legacy {@code &} colour codes in {@code raw} (null-safe → null). */
    public static String color(String raw) {
        return raw == null ? null : ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Build a stack of {@code material} with a coloured display {@code name} and coloured {@code lore}
     * (each line {@code &}-translated). A blank name or empty lore is simply left unset.
     */
    public static ItemStack build(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    /**
     * §N custom-item resolver (ADR-0027): a bundled ItemsAdder/Oraxen bridge that turns a config material
     * TOKEN (e.g. {@code itemsadder:ruby_scroll}, {@code oraxen:amethyst_dust}) into its custom {@link
     * ItemStack}, or {@code null} for a plain/vanilla token. A static, boot-configured no-op by default (set
     * once via {@link #customItemResolver}, mirroring the other soft hooks) so this module never references an
     * integration API and is inert without them.
     */
    private static volatile java.util.function.Function<String, ItemStack> customItemResolver = token -> null;

    /** Install the ItemsAdder/Oraxen custom-item resolver (boot-time). A {@code null} resets to no-op. */
    public static void customItemResolver(java.util.function.Function<String, ItemStack> resolver) {
        customItemResolver = resolver == null ? token -> null : resolver;
    }

    /**
     * Build an item from a config material TOKEN: if a bundled custom-item integration (ItemsAdder / Oraxen)
     * recognises the token its custom item is the base, otherwise the token resolves to a vanilla
     * {@link Material} (cross-version, via {@link #material}). The StarEnchants {@code name}/{@code lore} are
     * then applied on top (a blank name/lore leaves the custom item's own).
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
