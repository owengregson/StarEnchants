package item.mint;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.text.Colors;

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
            Map.entry("FIRE_CHARGE", "FIREBALL"), // 1.13 rename: the SoulTrak gem's material on the 1.8 lane
            Map.entry("WRITABLE_BOOK", "BOOK_AND_QUILL"), // 1.13 rename: the Godly Transmog scroll on the 1.8 lane
            Map.entry("DRIED_KELP", "INK_SACK"), // 1.13+ item: the Black Scroll's material → the 1.8 ink sac
            // 1.8's real red dye was INK_SACK+data (unexpressible by name here); REDSTONE is the closest red item.
            Map.entry("RED_DYE", "REDSTONE"),
            Map.entry("ENDER_EYE", "EYE_OF_ENDER"),   // 1.13 rename: the Singularity reforge icon on the 1.8 lane
            Map.entry("STONE_BRICKS", "SMOOTH_BRICK"),// 1.13 rename: the Castling reforge icon on the 1.8 lane
            Map.entry("CHORUS_FRUIT", "ENDER_PEARL"), // 1.9+ item: the Blink reforge icon → the 1.8 pearl
            Map.entry("TRIDENT", "ARROW"),            // 1.13+ item: the Javelin reforge icon → the 1.8 arrow
            Map.entry("BELL", "GOLD_INGOT"),          // 1.14+ item: the Grand Bell reforge icon on the 1.8 lane
            // The identity-rework reforge icons (ADR-0070): each post-1.8 material degrades to the closest
            // 1.8-name likeness so the legacy catalogue keeps a readable identity.
            Map.entry("END_CRYSTAL", "EYE_OF_ENDER"),       // 1.9+ item: The Singularity
            Map.entry("SOUL_CAMPFIRE", "FURNACE"),          // 1.16+ item: Spell Grappler
            Map.entry("GLOW_BERRIES", "SPECKLED_MELON"),    // 1.17+ item: Berry Overdrive
            Map.entry("HEART_OF_THE_SEA", "PRISMARINE_SHARD"), // 1.13+ item: Star Battery
            Map.entry("LIGHT_BLUE_CANDLE", "TORCH"),        // 1.17+ item: Spectral Javelin
            // The untextured-head default (ADR-0052): PetDefReader hands every pet PLAYER_HEAD, and a pet
            // that authors no `head:` never reaches the era seam, so on 1.8 it minted as the generic PAPER
            // fallback. Lossy in a way the others are not — this path carries no data value, so 1.8 renders
            // the data-0 SKELETON skull rather than the player variant the seam's SKULL_ITEM:3 produces.
            // A head-shaped trophy is the intended likeness; the skin is a codex question (deferred-content).
            Map.entry("PLAYER_HEAD", "SKULL_ITEM"));

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

    /** The live universal lore-wrap width (visible chars; {@code 0} = off) — for renderers that wrap authored
     *  lore OUTSIDE {@link #buildItem}: set-member lore and the enchant-book {@code {DESCRIPTION}} share it. */
    public static int itemWrapWidth() {
        return itemWrapWidth.getAsInt();
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
     * Build from a config TOKEN: a recognised ItemsAdder/Oraxen custom item is the base, else the token resolves
     * vanilla via {@link #material}. {@code name}/{@code lore} apply on top; blank leaves the custom item's own.
     */
    public static ItemStack build(String token, Material fallback, String name, List<String> lore) {
        ItemStack custom = customItemResolver.apply(token);
        ItemStack base = custom != null ? custom.clone() : new ItemStack(material(token, fallback));
        return decorate(base, name, lore);
    }

    /**
     * Re-decorate an EXISTING stack's display name + lore (colour-translated) in place — the mint-time
     * {@link #build} tail exposed for items whose stack identity must survive (a textured pet head being
     * re-rendered at a level-up, ADR-0052). Blank name / empty lore leave that half untouched.
     */
    public static ItemStack decorated(ItemStack stack, String name, List<String> lore) {
        return decorate(stack, name, lore);
    }

    /**
     * Dye a leather piece in place (§6.6 set-piece colour). {@code token} is {@code #RRGGBB} (or bare hex) or
     * one of {@link org.bukkit.Color}'s named constants, matched case-insensitively. A blank/unreadable token,
     * or a stack that is not leather, leaves the item untouched — an unrecognised dye must never cost the
     * piece its likeness. {@code LeatherArmorMeta} and {@code Color} are floor-stable across the whole range,
     * so this needs no era seam; the named constants are read reflectively because they are static FIELDS on a
     * class rather than an enum, and hard-coding the seventeen would drift the day Bukkit adds an eighteenth.
     *
     * @return whether a colour was applied
     */
    public static boolean dye(ItemStack stack, String token) {
        if (stack == null || token == null || token.isBlank()) {
            return false;
        }
        org.bukkit.Color color = color(token.trim());
        if (color == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leather)) {
            return false;
        }
        leather.setColor(color);
        stack.setItemMeta(leather);
        return true;
    }

    /** {@code #RRGGBB} / bare hex / a {@link org.bukkit.Color} constant name, or {@code null} when neither. */
    private static org.bukkit.Color color(String token) {
        String hex = token.startsWith("#") ? token.substring(1) : token;
        if (hex.length() == 6) {
            try {
                return org.bukkit.Color.fromRGB(Integer.parseInt(hex, 16));
            } catch (IllegalArgumentException notHex) {
                // fall through to the named constants — "ORANGE" is six characters too
            }
        }
        try {
            java.lang.reflect.Field field = org.bukkit.Color.class.getField(token.toUpperCase(Locale.ROOT));
            return field.get(null) instanceof org.bukkit.Color named ? named : null;
        } catch (ReflectiveOperationException unknown) {
            return null;
        }
    }

    @SuppressWarnings("deprecation") // setDisplayName/setLore(String/List): the floor-stable item-meta path
    private static ItemStack decorate(ItemStack stack, String name, List<String> lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isBlank()) {
                meta.setDisplayName(Colors.translate(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(Colors::translate).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
