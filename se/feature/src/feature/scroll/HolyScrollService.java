package feature.scroll;

import compile.load.ScrollsConfig;
import item.codec.ScrollCodec;
import item.mint.ItemFactory;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Holy/death scroll (§I): carried (incl. off-hand), on death it has a chance to spare the player's
 * items + levels, consuming one scroll only on a saved death. The roll is injected for tests.
 */
public final class HolyScrollService {

    public static final String HOLY = "HOLY";

    private final ScrollCodec scrolls;
    private final Supplier<ScrollsConfig> config;
    private final Random random;
    private final item.lang.Messages messages;

    /** Default-messages form (tests/fixtures). */
    public HolyScrollService(ScrollCodec scrolls, Supplier<ScrollsConfig> config, Random random) {
        this(scrolls, config, random, item.lang.Messages.defaults());
    }

    public HolyScrollService(ScrollCodec scrolls, Supplier<ScrollsConfig> config, Random random,
                             item.lang.Messages messages) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isHolyScroll(ItemStack stack) {
        return HOLY.equals(scrolls.kind(stack));
    }

    public ItemStack mint() {
        ScrollsConfig.Holy cfg = config.get().holy();
        ItemStack stack = ItemFactory.build(
                cfg.material(), Material.TOTEM_OF_UNDYING, cfg.name(), cfg.lore());
        scrolls.mark(stack, HOLY);
        return stack;
    }

    /**
     * Attempt to save {@code player}: on a carried scroll + a winning roll, consume one and return the
     * "saved" message; else {@code null} — nothing consumed, the death proceeds.
     */
    public String trySave(Player player) {
        ScrollsConfig.Holy cfg = config.get().holy();
        PlayerInventory inv = player.getInventory();
        int slot = findScrollSlot(inv);
        boolean offhand = slot < 0 && isHolyScroll(inv.getItemInOffHand());
        if (slot < 0 && !offhand) {
            return null; // no holy scroll carried
        }
        if (random.nextInt(100) >= cfg.saveChance()) {
            return null; // the roll failed — the scroll is NOT consumed; the player dies normally
        }
        if (slot >= 0) {
            consume(inv.getItem(slot), inv, slot);
        } else {
            ItemStack off = inv.getItemInOffHand();
            off.setAmount(off.getAmount() - 1);
            inv.setItemInOffHand(off.getAmount() <= 0 ? null : off);
        }
        return messages.format("scroll.holy.saved");
    }

    /** The first storage slot holding a holy scroll, or {@code -1} if none. */
    private int findScrollSlot(PlayerInventory inv) {
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (isHolyScroll(storage[i])) {
                return i;
            }
        }
        return -1;
    }

    private static void consume(ItemStack stack, PlayerInventory inv, int slot) {
        if (stack == null) {
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        inv.setItem(slot, stack.getAmount() <= 0 ? null : stack);
    }
}
