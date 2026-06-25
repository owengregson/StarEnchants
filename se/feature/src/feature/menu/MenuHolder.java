package feature.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link InventoryHolder} tagging a StarEnchants menu inventory and carrying all per-open state
 * (docs/v3-directives.md §K) — the only mutable per-open object in the framework. The listener recognises
 * our menus by {@code getView().getTopInventory().getHolder() instanceof MenuHolder}: the VIEW's top
 * inventory, not {@code event.getInventory()} (the <em>clicked</em> one, which is the player's own grid for
 * a bottom-row click), so foreign inventories are never touched.
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private final Map<Integer, ClickAction> actions = new HashMap<>();
    private Inventory inventory;

    // Navigation cursor — advanced by drill-down menus; read back inside render().
    private int page;
    private String view;
    private String selection;
    private Object payload;

    public MenuHolder(Menu menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    public Menu menu() {
        return menu;
    }

    /**
     * Start a fresh render: tag a new {@code size}-cell inventory with this holder and clear the previous
     * view's action bindings. The {@code createInventory(holder, size, String title)} overload is
     * deprecated-not-removed across 1.17.1 → 26.1.x and is the cross-version title path ({@link MenuText}
     * length-clamps {@code title} upstream).
     */
    @SuppressWarnings("deprecation")
    public Inventory begin(int size, String title) {
        actions.clear();
        inventory = Bukkit.createInventory(this, size, title);
        return inventory;
    }

    /** Place {@code icon} in {@code slot}, binding {@code action} ({@code null} for a decorative cell). */
    public void set(int slot, ItemStack icon, ClickAction action) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, icon);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    ClickAction actionAt(int slot) {
        return actions.get(slot);
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    /** The current sub-view id for a drill-down menu, or {@code null} for the index. */
    public String view() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public String selection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }

    /** Arbitrary per-open state a menu attaches (e.g. a working list); {@code null} until set. */
    public Object payload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public Inventory getInventory() {
        // Defensive empty chest in case Bukkit queries the holder before begin() runs.
        return inventory != null ? inventory : Bukkit.createInventory(this, InventoryType.CHEST);
    }
}
