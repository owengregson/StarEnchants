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

    @Override
    public void open(Player player) {
        // Capture the held item's enchant order on the player's region thread (a cross-region read off the
        // command thread would be unsafe on Folia), then open inline.
        Scheduling.onEntity(player, () -> {
            MenuHolder holder = new MenuHolder(this);
            CombatState state = combat.read(player.getInventory().getItemInMainHand());
            holder.setPayload(new ArrayList<>(state.enchants().keySet())); // the working order
            render(holder);
            player.openInventory(holder.getInventory());
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<String> items(MenuHolder holder) {
        Object payload = holder.payload();
        return payload instanceof List ? (List<String>) payload : List.of();
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
            ItemStack held = click.player().getInventory().getItemInMainHand();
            if (scrolls.reorder(held, order)) {
                click.player().getInventory().setItemInMainHand(held); // write the reordered copy back
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
