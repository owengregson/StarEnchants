package item.mint;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final System.Logger LOG = System.getLogger("StarEnchants.Item");
    private static final java.util.Set<String> WARNED_DEGRADE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Newer-than-floor materials mapped to the closest equivalent that exists on an OLDER server, for the
     * optional 1.8 lane: a content token like {@code NETHERITE_HELMET} (added 1.16) has no 1.8 material, so
     * minting it on 1.8 would otherwise drop to the caller's generic fallback (every set-armour slot →
     * {@code LEATHER_HELMET}). This is a lossy, legacy-only DEGRADATION (not a rename), so it lives here on
     * the one cross-version mint path rather than in {@link platform.resolve.Aliases} (which the migrator
     * reuses bidirectionally). Dormant on the floor build — the modern name resolves directly and this map is
     * never consulted. Shovels are {@code _SPADE} on 1.8.
     */
    private static final Map<String, String> LEGACY_FALLBACK = Map.ofEntries(
            Map.entry("NETHERITE_HELMET", "DIAMOND_HELMET"),
            Map.entry("NETHERITE_CHESTPLATE", "DIAMOND_CHESTPLATE"),
            Map.entry("NETHERITE_LEGGINGS", "DIAMOND_LEGGINGS"),
            Map.entry("NETHERITE_BOOTS", "DIAMOND_BOOTS"),
            Map.entry("NETHERITE_SWORD", "DIAMOND_SWORD"),
            Map.entry("NETHERITE_AXE", "DIAMOND_AXE"),
            Map.entry("NETHERITE_PICKAXE", "DIAMOND_PICKAXE"),
            Map.entry("NETHERITE_SHOVEL", "DIAMOND_SPADE"),
            Map.entry("NETHERITE_HOE", "DIAMOND_HOE"),
            Map.entry("NETHERITE_BLOCK", "DIAMOND_BLOCK"),
            Map.entry("NETHERITE_INGOT", "DIAMOND"));

    /** The closest older-server equivalent of a newer material, or {@code null} if none is registered. */
    static String legacyFallback(String upperToken) {
        return LEGACY_FALLBACK.get(upperToken);
    }

    /**
     * Resolve a config material token cross-version: exact enum name, then {@link Material#matchMaterial}
     * (namespaced/legacy spellings), then a {@linkplain #LEGACY_FALLBACK newer&rarr;older degradation} for the
     * optional 1.8 lane, else {@code fallback}. Never null, never throws — an off-server registry probe
     * degrades to the fallback rather than propagating.
     */
    public static Material material(String token, Material fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (token == null || token.isBlank()) {
            return fallback;
        }
        String upper = token.trim().toUpperCase(Locale.ROOT);
        Material exact = Material.getMaterial(upper);
        if (exact != null) {
            return exact;
        }
        Material matched;
        try {
            matched = Material.matchMaterial(token.trim());
        } catch (RuntimeException registryUnavailable) {
            matched = null; // registry not initialised (off-server) — fall back, don't crash the load
        }
        if (matched != null) {
            return matched;
        }
        String older = LEGACY_FALLBACK.get(upper);
        if (older != null) {
            Material degraded = Material.getMaterial(older);
            if (degraded != null) {
                if (WARNED_DEGRADE.add(upper)) {
                    LOG.log(System.Logger.Level.WARNING,
                            "material '" + upper + "' is unavailable on this server version; using '"
                                    + older + "' (legacy degradation)");
                }
                return degraded;
            }
        }
        return fallback;
    }

    public static String color(String raw) {
        return raw == null ? null : ChatColor.translateAlternateColorCodes('&', raw);
    }

    /** Blank name / empty lore is left unset. */
    public static ItemStack build(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    /**
     * §N custom-item resolver (ADR-0027): ItemsAdder/Oraxen token → custom {@link ItemStack}, or {@code null}
     * for a vanilla token. Static no-op default so this module never references an integration API and is inert
     * without them; the root installs the live one via {@link #customItemResolver}.
     */
    private static volatile java.util.function.Function<String, ItemStack> customItemResolver = token -> null;

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
