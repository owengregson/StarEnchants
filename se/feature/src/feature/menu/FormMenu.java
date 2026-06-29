package feature.menu;

import compile.load.MenusConfig;
import item.mint.ItemFactory;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * Base for a single-screen bench menu with item-input slots (§K, ADR-0030). Unlike {@link PagedMenu} it
 * renders once — no re-render that would discard staged items — and buttons act on the live inventory in
 * place. It lays a themed backdrop (every non-input cell a frame pane), an info pane, and a close button; the
 * subclass adds its control buttons and input labels in {@link #layoutControls}. The shared {@link MenuListener}
 * locks every non-{@link #inputSlots() input} slot; {@link #onClose} returns staged inputs so a closed bench
 * never eats items.
 */
public abstract class FormMenu implements Menu, InteractiveMenu {

    private final String name;
    private final Supplier<MenuLayout> layout;
    private final Supplier<MenuTheme> theme;
    private final Capabilities caps;

    /** Fixed-layout form (tests/fixtures): the programmatic default is used verbatim, no menus/ override. */
    protected FormMenu(String name, MenuLayout layout, Capabilities caps) {
        this(name, layout, caps, MenusConfig::empty);
    }

    /** Canonical form: layout + chrome = defaults merged with the live {@code menus/<name>.yml} (§L), per render. */
    protected FormMenu(String name, MenuLayout defaultLayout, Capabilities caps, Supplier<MenusConfig> menus) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(defaultLayout, "defaultLayout");
        Objects.requireNonNull(menus, "menus");
        this.layout = () -> MenuLayout.from(defaultLayout, menus.get().forMenu(this.name).orElse(null));
        this.theme = () -> MenuTheme.from(MenuTheme.DEFAULT, menus.get().forMenu(this.name).orElse(null));
        this.caps = caps;
    }

    @Override
    public final String name() {
        return name;
    }

    protected final MenuLayout layout() {
        return layout.get();
    }

    protected final MenuTheme theme() {
        return theme.get();
    }

    /** Place the control buttons + input labels (e.g. the combine/salvage button) onto the holder. */
    protected abstract void layoutControls(MenuHolder holder);

    /** The info-pane title for this bench, or {@code null} to keep the theme's default info pane. */
    protected String infoTitle() {
        return null;
    }

    /** The info-pane lore for this bench (used only when {@link #infoTitle} is non-null). */
    protected List<String> infoLore() {
        return List.of();
    }

    @Override
    public void render(MenuHolder holder) {
        MenuLayout layout = layout();
        MenuTheme theme = theme();
        holder.begin(layout.size(), MenuText.title(layout.titleTemplate(), caps));
        fillBackground(holder, layout);
        placeInfo(holder, layout, theme);
        if (layout.closeSlot() >= 0 && !inputSlots().contains(layout.closeSlot())) {
            holder.set(layout.closeSlot(), MenuIcons.plain(theme.close()),
                    click -> click.player().closeInventory());
        }
        layoutControls(holder); // overwrites the background where the bench wants a control
    }

    /** Fill every non-input slot with the decorative filler pane (inputs stay empty for placement). */
    private void fillBackground(MenuHolder holder, MenuLayout layout) {
        ItemStack pane = MenuIcons.pane(layout.fillerMaterial());
        if (pane == null) {
            return;
        }
        Set<Integer> inputs = inputSlots();
        for (int slot = 0; slot < layout.size(); slot++) {
            if (!inputs.contains(slot)) {
                holder.set(slot, pane, null);
            }
        }
    }

    private void placeInfo(MenuHolder holder, MenuLayout layout, MenuTheme theme) {
        int slot = theme.infoSlot();
        if (slot < 0 || slot >= layout.size() || inputSlots().contains(slot)) {
            return;
        }
        String title = infoTitle();
        NavButton info = title == null ? theme.info()
                : new NavButton(theme.info().material(), theme.info().fallback(), title, infoLore());
        holder.set(slot, MenuIcons.plain(info), null);
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

    /** A glowing control-button variant for the bench's primary action, so it stands out from labels. */
    protected static ItemStack actionButton(String materialToken, String name, List<String> lore) {
        return ItemFactory.glow(button(materialToken, name, lore));
    }
}
