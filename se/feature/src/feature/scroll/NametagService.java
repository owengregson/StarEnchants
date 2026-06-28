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
 * Item nametag (§I): dragged onto gear it begins a chat-capture rename; the next chat line becomes the
 * display name unless blacklisted.
 *
 * <p>The pending store holds a <em>clone of the target item</em>, not a slot index: the player may move the
 * item between the click and the (async) chat line, so the rename re-locates the exact stack by
 * {@link ItemStack#isSimilar(ItemStack) identity}. Concurrent because the chat event fires async; the
 * mutation is hopped to the region thread by the listener (Folia).
 */
public final class NametagService {

    public static final String NAMETAG = "NAMETAG";

    private final ScrollCodec scrolls;
    private final Supplier<ScrollsConfig> config;
    private final item.lang.Messages messages;
    private final ConcurrentHashMap<UUID, ItemStack> pending = new ConcurrentHashMap<>();

    /** Default-messages form (tests/fixtures). */
    public NametagService(ScrollCodec scrolls, Supplier<ScrollsConfig> config) {
        this(scrolls, config, item.lang.Messages.defaults());
    }

    public NametagService(ScrollCodec scrolls, Supplier<ScrollsConfig> config, item.lang.Messages messages) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.config = Objects.requireNonNull(config, "config");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isNametag(ItemStack stack) {
        return NAMETAG.equals(scrolls.kind(stack));
    }

    public ItemStack mint() {
        ScrollsConfig.Nametag cfg = config.get().nametag();
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Material.NAME_TAG, cfg.name(), cfg.lore());
        scrolls.mark(stack, NAMETAG);
        return stack;
    }

    /**
     * Begin a rename targeting {@code target}, unless one is already pending. Returns the prompt, or
     * {@code null} when already pending — the caller must NOT consume a nametag in that case.
     */
    public String begin(UUID player, ItemStack target) {
        if (pending.containsKey(player)) {
            return null;
        }
        pending.put(player, target.clone()); // capture identity, not a volatile slot index
        return messages.format("scroll.nametag.prompt");
    }

    public String busyMessage() {
        return messages.format("scroll.nametag.busy");
    }

    public boolean isPending(UUID player) {
        return pending.containsKey(player);
    }

    /** Drop any pending rename (called on quit, so a stale capture is never reused). */
    public void clear(UUID player) {
        pending.remove(player);
    }

    /**
     * Complete a pending rename with the chat-typed {@code text} — MUST run on the player's own region
     * thread (mutates their inventory). Cancel / blacklisted name / vanished target all refund the nametag.
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
            return messages.format("scroll.nametag.cancelled");
        }
        String translated = ItemFactory.color(trimmed);
        String plain = ChatColor.stripColor(translated).toLowerCase(Locale.ROOT);
        for (String word : cfg.blacklist()) {
            if (!word.isBlank() && plain.contains(word.toLowerCase(Locale.ROOT))) {
                refund(player);
                return messages.format("scroll.nametag.blacklisted");
            }
        }
        int slot = locate(player, token); // re-find the captured stack wherever it now sits
        if (slot < 0) {
            refund(player); // the target moved out of reach / changed — return the nametag, never lose it
            return messages.format("scroll.nametag.target-gone");
        }
        ItemStack item = player.getInventory().getItem(slot);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            refund(player);
            return messages.format("scroll.nametag.cannot-rename");
        }
        meta.setDisplayName(translated);
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
        return messages.format("scroll.nametag.renamed");
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

    /** Return one nametag to the player; overflow drops at their feet. */
    private void refund(Player player) {
        player.getInventory().addItem(mint()).values()
                .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
}
