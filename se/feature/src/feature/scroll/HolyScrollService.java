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
 * The holy / death scroll (docs/v3-directives.md §I) — held in the inventory (incl. off-hand), on a death
 * it has a configurable chance to spare the player's items + levels, consuming one scroll on the saved
 * death. A distinct {@code HOLY}-kind scroll ({@link ScrollCodec}); its behaviour is the death-event scan
 * here, not a gesture. The roll is the only non-determinism, injected as a {@link Random} for testability.
 *
 * <p>Folia-correct: {@code PlayerDeathEvent} fires on the dying player's own region thread, so scanning
 * and mutating their inventory in {@link #trySave} is in-thread.
 */
public final class HolyScrollService {

    /** The scroll kind this service owns. */
    public static final String HOLY = "HOLY";

    private final ScrollCodec scrolls;
    private final Supplier<ScrollsConfig> config;
    private final Random random;
    private final item.lang.Messages messages; // §L lang.yml — the "saved" message

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

    /** Whether {@code stack} is a holy scroll. */
    public boolean isHolyScroll(ItemStack stack) {
        return HOLY.equals(scrolls.kind(stack));
    }

    /** Mint a holy scroll. */
    public ItemStack mint() {
        ScrollsConfig.Holy cfg = config.get().holy();
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.material(), Material.TOTEM_OF_UNDYING), cfg.name(), cfg.lore());
        scrolls.mark(stack, HOLY);
        return stack;
    }

    /**
     * Attempt to save {@code player} from this death: if they carry a holy scroll (storage or off-hand) and
     * the save roll succeeds, consume one scroll and return the configured "saved" message; otherwise return
     * {@code null} (no scroll, or the roll failed — the scroll is not consumed and the death proceeds).
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
