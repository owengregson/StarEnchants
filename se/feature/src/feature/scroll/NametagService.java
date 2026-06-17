package feature.scroll;

import compile.load.ScrollsConfig;
import item.codec.ScrollCodec;
import item.mint.ItemFactory;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The item nametag (docs/v3-directives.md §I) — dragged onto gear it begins a chat-capture rename; the
 * player's next chat line becomes the item's display name unless it contains a blacklisted word. A distinct
 * {@code NAMETAG}-kind scroll ({@link ScrollCodec}).
 *
 * <p>The pending-rename store is keyed by player UUID and holds a <em>clone of the target item</em>, not a
 * volatile slot index: between the click and the (async) chat line the player may move the item, so the
 * rename re-locates the exact stack by {@link ItemStack#isSimilar(ItemStack) identity} and renames it where
 * it now sits — never whatever later occupies a fixed slot. If the target has vanished the spent nametag is
 * refunded rather than silently lost, and starting a rename while one is pending is rejected (so a second
 * nametag is never consumed for nothing). The store is concurrent because the chat event fires async; the
 * inventory mutation itself is hopped back to the player's region thread by the listener (Folia-correct).
 */
public final class NametagService {

    /** The scroll kind this service owns. */
    public static final String NAMETAG = "NAMETAG";

    private final ScrollCodec scrolls;
    private final Supplier<ScrollsConfig> config;
    private final ConcurrentHashMap<UUID, ItemStack> pending = new ConcurrentHashMap<>();

    public NametagService(ScrollCodec scrolls, Supplier<ScrollsConfig> config) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Whether {@code stack} is an item nametag. */
    public boolean isNametag(ItemStack stack) {
        return NAMETAG.equals(scrolls.kind(stack));
    }

    /** Mint an item nametag. */
    public ItemStack mint() {
        ScrollsConfig.Nametag cfg = config.get().nametag();
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.material(), Material.NAME_TAG), cfg.name(), cfg.lore());
        scrolls.mark(stack, NAMETAG);
        return stack;
    }

    /**
     * Begin a rename for {@code player} targeting {@code target} (a clone is captured for identity), unless
     * one is already pending. Returns the prompt message, or {@code null} when a rename is already pending
     * (the caller must NOT consume a nametag in that case) — both guard against a second nametag being spent
     * for nothing.
     */
    public String begin(UUID player, ItemStack target) {
        if (pending.containsKey(player)) {
            return null; // a rename is already awaiting this player's chat line — don't start (or consume) a second
        }
        pending.put(player, target.clone()); // capture identity, not a volatile slot index
        return ItemFactory.color(config.get().nametag().messagePrompt());
    }

    /** Whether {@code player} has a rename awaiting a chat line. */
    public boolean isPending(UUID player) {
        return pending.containsKey(player);
    }

    /** Drop any pending rename for {@code player} (called on quit, so a stale capture is never reused). */
    public void clear(UUID player) {
        pending.remove(player);
    }

    /**
     * Complete a pending rename for {@code player} with the chat-typed {@code text} — MUST run on the
     * player's own region thread (it mutates their inventory). Re-locates the captured target by identity
     * and renames it; on cancel / a blacklisted name / the target having vanished, the spent nametag is
     * refunded. Returns the message to show, or {@code null} if there was no pending rename.
     */
    @SuppressWarnings("deprecation") // setDisplayName: the floor-stable item-meta path
    public String complete(Player player, String text) {
        ItemStack token = pending.remove(player.getUniqueId());
        if (token == null) {
            return null; // no pending rename (defensive)
        }
        ScrollsConfig.Nametag cfg = config.get().nametag();
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("cancel")) {
            refund(player);
            return ItemFactory.color(cfg.messageCancelled());
        }
        String translated = ItemFactory.color(trimmed);
        String plain = ChatColor.stripColor(translated).toLowerCase(Locale.ROOT);
        for (String word : cfg.blacklist()) {
            if (!word.isBlank() && plain.contains(word.toLowerCase(Locale.ROOT))) {
                refund(player);
                return ItemFactory.color(cfg.messageBlacklisted());
            }
        }
        int slot = locate(player, token); // re-find the captured stack wherever it now sits
        if (slot < 0) {
            refund(player); // the target moved out of reach / changed — return the nametag, never lose it
            return "§cThe item to rename is no longer there — your nametag was returned.";
        }
        ItemStack item = player.getInventory().getItem(slot);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            refund(player);
            return "§cThat item cannot be renamed — your nametag was returned.";
        }
        meta.setDisplayName(translated);
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
        return ItemFactory.color(cfg.messageRenamed());
    }

    /** The slot of the first stack matching the captured {@code token} by identity, or {@code -1} if gone. */
    private static int locate(Player player, ItemStack token) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (candidate != null && candidate.isSimilar(token)) {
                return i;
            }
        }
        return -1;
    }

    /** Return one nametag to the player (overflow drops at their feet) — they aborted, so it was not used. */
    private void refund(Player player) {
        player.getInventory().addItem(mint()).values()
                .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
}
