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
            Map.entry("NETHERITE_INGOT", "DIAMOND"),
            Map.entry("FIRE_CHARGE", "FIREBALL")); // 1.13 rename: the SoulTrak gem's material on the 1.8 lane

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

    /**
     * §L universal economy-item lore wrap (ADR-0019 lineage): the visible width authored economy/identity
     * item lore auto-wraps to on the {@link #buildItem} mint path. Injected once at the composition root from
     * {@code config.lore().itemWrap()} (re-read live so a {@code /se reload} re-tunes it), mirroring
     * {@link #customItemResolver}. Static default {@code 0} (= no wrap) keeps this module inert and every
     * unit test server-free. {@link #build} is NOT wrapped — menu icons carry curated lore and must not be
     * re-split.
     */
    private static volatile java.util.function.IntSupplier itemWrapWidth = () -> 0;

    public static void itemWrapWidth(java.util.function.IntSupplier supplier) {
        itemWrapWidth = supplier == null ? () -> 0 : supplier;
    }

    /** Blank name / empty lore is left unset. Lore is taken verbatim (no wrap) — for menu icons / fixed text. */
    public static ItemStack build(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    /**
     * Like {@link #build(Material, String, List)} but AUTO-WRAPS each authored lore line to the injected
     * {@link #itemWrapWidth} (§L {@code lore.item-wrap}) — the mint path for economy/identity items whose
     * lore an author writes as single long lines. Authored blank lines are preserved as separators.
     */
    public static ItemStack buildItem(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, wrapItemLore(lore));
    }

    /** Token form of {@link #buildItem(Material, String, List)} (custom-item base, else vanilla fallback). */
    public static ItemStack buildItem(String token, Material fallback, String name, List<String> lore) {
        ItemStack custom = customItemResolver.apply(token);
        ItemStack base = custom != null ? custom.clone() : new ItemStack(material(token, fallback));
        return decorate(base, name, wrapItemLore(lore));
    }

    /** Word-wrap authored economy-item lore at the injected width; {@code null} stays {@code null}. */
    private static List<String> wrapItemLore(List<String> lore) {
        return lore == null ? null : item.render.TextWrap.wrapAll(lore, itemWrapWidth.getAsInt());
    }

    /**
     * The §L economy-item wrap applied to already-substituted lore — for re-render paths (e.g. the soul gem
     * name/count re-render on deposit/spend) that rebuild lore OUTSIDE {@link #buildItem} but must wrap
     * identically to the mint, else the lore visibly "unwraps" on the first update.
     */
    public static List<String> wrapLore(List<String> lore) {
        return wrapItemLore(lore);
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
     * Resolves a vanilla {@link org.bukkit.enchantments.Enchantment} by its modern canonical NAME
     * ({@code PROTECTION}, {@code UNBREAKING}, {@code SHARPNESS}) cross-version — the modern overlay maps via
     * the namespaced-key registry, the legacy overlay via the 1.8 names. Installed at the composition root
     * (behind the {@code Wiring} seam); static no-op default keeps this module server-free for unit tests.
     */
    private static volatile java.util.function.Function<String, org.bukkit.enchantments.Enchantment>
            enchantResolver = name -> null;

    public static void enchantResolver(java.util.function.Function<String, org.bukkit.enchantments.Enchantment> resolver) {
        enchantResolver = resolver == null ? name -> null : resolver;
    }

    /**
     * Apply vanilla enchants by NAME ({@code name → level}) to {@code stack} in place — the cross-version mint
     * path for set-piece base enchants (Protection/Unbreaking/Sharpness, §6.6). Unknown names (resolver miss)
     * are skipped, never throwing. {@code addUnsafeEnchantment} bypasses the vanilla level cap.
     */
    public static void applyVanillaEnchants(ItemStack stack, Map<String, Integer> nameToLevel) {
        if (stack == null || nameToLevel == null || nameToLevel.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : nameToLevel.entrySet()) {
            org.bukkit.enchantments.Enchantment enchant = enchantResolver.apply(entry.getKey());
            if (enchant != null) {
                stack.addUnsafeEnchantment(enchant, Math.max(1, entry.getValue()));
            }
        }
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
