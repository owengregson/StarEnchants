package feature.menu;

import compile.load.MenusConfig;
import item.mint.ItemFactory;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * Base for a single-screen bench menu with item-input slots (§K). Unlike {@link PagedMenu} it renders once —
 * no re-render that would discard staged items — and buttons act on the live inventory in place. The shared
 * {@link MenuListener} locks every non-{@link #inputSlots() input} slot; {@link #onClose} returns staged inputs.
 */
public abstract class FormMenu implements Menu, InteractiveMenu {

    private final String name;
    private final Supplier<MenuLayout> layout;
    private final Capabilities caps;

    /** Fixed-layout form (tests/fixtures): the programmatic default is used verbatim, no menus/ override. */
    protected FormMenu(String name, MenuLayout layout, Capabilities caps) {
        this(name, layout, caps, MenusConfig::empty);
    }

    /** Canonical form: layout = {@code defaultLayout} merged with the live {@code menus/<name>.yml} (§L), per render. */
    protected FormMenu(String name, MenuLayout defaultLayout, Capabilities caps, Supplier<MenusConfig> menus) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(defaultLayout, "defaultLayout");
        Objects.requireNonNull(menus, "menus");
        this.layout = () -> MenuLayout.from(defaultLayout, menus.get().forMenu(this.name).orElse(null));
        this.caps = caps;
    }

    @Override
    public final String name() {
        return name;
    }

    protected final MenuLayout layout() {
        return layout.get();
    }

    /** Place the control buttons (e.g. the combine/salvage button) onto the holder. */
    protected abstract void layoutControls(MenuHolder holder);

    @Override
    public void render(MenuHolder holder) {
        MenuLayout layout = layout();
        holder.begin(layout.size(), MenuText.title(layout.titleTemplate(), caps));
        fillBackground(holder, layout);
        layoutControls(holder); // overwrites the background
    }

    /** Fill every non-input slot with the decorative filler pane (inputs stay empty for placement). */
    private void fillBackground(MenuHolder holder, MenuLayout layout) {
        Material filler = ItemFactory.material(layout.fillerMaterial(), Material.AIR);
        if (filler == Material.AIR) {
            return;
        }
        ItemStack pane = ItemFactory.build(filler, " ", List.of());
        Set<Integer> inputs = inputSlots();
        for (int slot = 0; slot < layout.size(); slot++) {
            if (!inputs.contains(slot)) {
                holder.set(slot, pane, null);
            }
        }
    }

    @Override
    public void onClose(Player player, MenuHolder holder) {
        // Return anything the player staged but didn't consume, so a closed bench never eats items.
        for (int slot : inputSlots()) {
            ItemStack staged = holder.getInventory().getItem(slot);
            if (staged != null && staged.getType() != Material.AIR) {
                MenuItems.giveOrDrop(player, staged);
                holder.getInventory().setItem(slot, null);
            }
        }
    }

    /** A control-button icon resolved by name (cross-version), falling back to {@code PAPER}. */
    protected static ItemStack button(String materialToken, String name, List<String> lore) {
        return ItemFactory.build(ItemFactory.material(materialToken, Material.PAPER), name, lore);
    }
}
