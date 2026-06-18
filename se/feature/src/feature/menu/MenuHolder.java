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
 * The {@link InventoryHolder} that tags a StarEnchants menu inventory and carries all per-open state
 * (docs/v3-directives.md §K). The shared {@link MenuListener} recognises one of our menus by
 * {@code event.getView().getTopInventory().getHolder() instanceof MenuHolder} — the VIEW's top inventory,
 * not {@code event.getInventory()} (which is the <em>clicked</em> inventory and would be the player's own
 * grid for a bottom-row click) — so vanilla containers and other plugins' inventories are never touched.
 *
 * <p>The holder owns: the {@link Menu} (so the listener can re-render it), the live {@link Inventory}, the
 * slot→{@link ClickAction} map for the currently-rendered view, and a small navigation cursor
 * ({@link #page}, {@link #view}, {@link #selection}) that drill-down menus advance. The {@code Menu} itself
 * stays stateless and shareable — every mutable bit of an open session lives here.
 *
 * <p>{@link #begin} starts a fresh render (clearing the previous view's action bindings), and {@link #set}
 * places an icon with an optional action. This is the only mutable per-open object in the framework.
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private final Map<Integer, ClickAction> actions = new HashMap<>();
    private Inventory inventory;

    // Navigation cursor — advanced by drill-down menus; read back inside render().
    private int page;
    private String view;
    private String selection;

    public MenuHolder(Menu menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    /** The menu this holder belongs to — the listener re-renders/routes through it. */
    public Menu menu() {
        return menu;
    }

    /**
     * Start a fresh render: create a new {@code size}-cell inventory tagged with this holder and titled
     * {@code title}, and clear the previous view's action bindings. Uses the {@code createInventory(holder,
     * size, String title)} overload — deprecated-not-removed across 1.17.1 → 26.1.x and the cross-version
     * title path (the {@code String} title is length-clamped upstream by {@link MenuText}).
     */
    @SuppressWarnings("deprecation")
    public Inventory begin(int size, String title) {
        actions.clear();
        inventory = Bukkit.createInventory(this, size, title);
        return inventory;
    }

    /** Place {@code icon} in {@code slot}, binding {@code action} (may be {@code null} for a decorative cell). */
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

    /** The action bound to {@code slot} in the current view, or {@code null} (a decorative/empty cell). */
    ClickAction actionAt(int slot) {
        return actions.get(slot);
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    /** The current sub-view id for a drill-down menu (e.g. {@code "tier"} vs {@code "enchant"}), or {@code null}. */
    public String view() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    /** The currently-selected key in a drill-down menu (e.g. the tier or set being viewed), or {@code null}. */
    public String selection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }

    @Override
    public Inventory getInventory() {
        // Never null in practice (set by begin() before the menu is opened); a defensive empty chest avoids
        // an NPE if Bukkit queries the holder before the first render.
        return inventory != null ? inventory : Bukkit.createInventory(this, InventoryType.CHEST);
    }
}
