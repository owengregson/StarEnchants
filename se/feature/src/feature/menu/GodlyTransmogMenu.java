package feature.menu;

import compile.load.ContentHolder;
import feature.scroll.ScrollService;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;
import platform.sched.Scheduling;

/**
 * The Godly Transmog reorder GUI (docs/v3-directives.md §K, §I): reorder the enchant lore lines of the
 * player's held gear by hand. One button per enchant on the item, in its current display order; click an
 * enchant to <em>select</em> it (it glows), then click another to <strong>swap</strong> their positions —
 * the swap is written to the item immediately (combat is order-independent, so this is purely cosmetic lore
 * order). Unlike the plain transmog scroll (a one-shot <em>random</em> shuffle), this is the deterministic,
 * player-chosen reorder.
 *
 * <p>The working order + the picked enchant are per-open state on the {@link MenuHolder} (its payload + its
 * selection). The held item is captured and reordered on the player's own region thread (the open-hop and
 * the click handler both run there), so the inventory reads/writes are Folia-correct.
 */
public final class GodlyTransmogMenu extends PagedMenu<String> {

    private final ContentHolder content;
    private final CombatCodec combat;
    private final ScrollService scrolls;

    /** Default-layout form (tests/fixtures). */
    public GodlyTransmogMenu(ContentHolder content, CombatCodec combat, ScrollService scrolls, Capabilities caps) {
        this(content, combat, scrolls, caps, compile.load.MenusConfig::empty);
    }

    public GodlyTransmogMenu(ContentHolder content, CombatCodec combat, ScrollService scrolls, Capabilities caps,
                             java.util.function.Supplier<compile.load.MenusConfig> menus) {
        super("transmog", MenuLayout.paged("&5Godly Transmog"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
    }

    /** Per-open binding: the working enchant order + the inventory slot it reorders ({@code -1} = main hand). */
    private record Working(List<String> order, int slot) {
    }

    @Override
    public void open(Player player) {
        open(player, -1); // -1 = the held main-hand item (the /se menu transmog path)
    }

    /**
     * Open the reorder GUI bound to a specific inventory {@code slot} ({@code -1} = main hand) — the physical
     * godly-transmog tool uses this to reorder the gear it was dragged onto, not whatever is held.
     */
    public void open(Player player, int slot) {
        // Capture the bound item's enchant order on the player's region thread (a cross-region read off the
        // command/click thread would be unsafe on Folia), then open inline.
        Scheduling.onEntity(player, () -> {
            MenuHolder holder = new MenuHolder(this);
            CombatState state = combat.read(gearAt(player, slot));
            holder.setPayload(new Working(new ArrayList<>(state.enchants().keySet()), slot));
            render(holder);
            player.openInventory(holder.getInventory());
        });
    }

    @Override
    protected List<String> items(MenuHolder holder) {
        return binding(holder).order();
    }

    private static Working binding(MenuHolder holder) {
        Object payload = holder.payload();
        return payload instanceof Working w ? w : new Working(new ArrayList<>(), -1);
    }

    /** The bound gear ItemStack: the main hand for {@code slot == -1}, else the inventory slot. */
    private static ItemStack gearAt(Player player, int slot) {
        return slot < 0 ? player.getInventory().getItemInMainHand() : player.getInventory().getItem(slot);
    }

    @Override
    protected ItemStack icon(MenuHolder holder, String key) {
        boolean selected = key.equals(holder.selection());
        String name = displayOf(key);
        List<String> lore = new ArrayList<>();
        if (selected) {
            lore.add("&aSelected &7— click another enchant to swap.");
        } else {
            lore.add("&eClick to pick this enchant.");
        }
        ItemStack icon = ItemFactory.build(
                selected ? material("ENCHANTED_BOOK", "BOOK") : material("BOOK", "PAPER"),
                (selected ? "&a➤ " : "&f") + stripColor(name), lore);
        return icon;
    }

    @Override
    protected void onSelect(MenuClick click, String key) {
        MenuHolder holder = click.holder();
        List<String> order = items(holder);
        String selected = holder.selection();
        if (selected == null) {
            holder.setSelection(key); // first pick — highlight on re-render
            reopen(click);
            return;
        }
        if (selected.equals(key)) {
            holder.setSelection(null); // clicked the picked enchant again → deselect
            reopen(click);
            return;
        }
        int i = order.indexOf(selected);
        int j = order.indexOf(key);
        if (i >= 0 && j >= 0) {
            Collections.swap(order, i, j); // swap in the working order
            int slot = binding(holder).slot();
            ItemStack gear = gearAt(click.player(), slot);
            if (scrolls.reorder(gear, order)) {
                if (slot < 0) {
                    click.player().getInventory().setItemInMainHand(gear);
                } else {
                    click.player().getInventory().setItem(slot, gear); // write back to the bound slot
                }
            }
        }
        holder.setSelection(null);
        reopen(click);
    }

    @Override
    protected String titleFor(MenuHolder holder) {
        return layout().titleTemplate();
    }

    private String displayOf(String key) {
        String name = content.library().displayNameOf(key);
        return name != null ? name : key;
    }

    /** Strip legacy colour codes from {@code name} so the prefix colour controls the icon name colour. */
    private static String stripColor(String name) {
        return org.bukkit.ChatColor.stripColor(ItemFactory.color(name));
    }
}
