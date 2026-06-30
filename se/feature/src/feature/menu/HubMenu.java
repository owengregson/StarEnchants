package feature.menu;

import compile.load.MenusConfig;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import platform.caps.Capabilities;

/**
 * Base for a single-screen navigation hub (ADR-0030) — a curated grid of feature tiles that drill into other
 * menus or fire an action, the GUI-first entry point a player or operator lands on. Unlike {@link PagedMenu}
 * it is not paginated: the subclass places each tile at a hand-chosen slot in {@link #layoutTiles} (curated
 * placement reads as designed, not packed), and inherits the themed frame, info pane, and close button.
 */
public abstract class HubMenu implements Menu {

    private final String name;
    private final String permission;
    private final Supplier<MenuLayout> layout;
    private final Supplier<MenuTheme> theme;
    private final Capabilities caps;

    /**
     * @param permission the node required to open this hub, or {@code null} to inherit the {@code /se} gate
     *                   (the user-facing hubs pass {@code null}; the operator console passes the admin node)
     */
    protected HubMenu(String name, String permission, MenuLayout defaultLayout, Capabilities caps,
                      Supplier<MenusConfig> menus) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        this.permission = permission;
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

    @Override
    public String permission() {
        return permission;
    }

    protected final MenuLayout layout() {
        return layout.get();
    }

    protected final MenuTheme theme() {
        return theme.get();
    }

    /** Place the hub's tiles via {@link #tile}; the frame, info pane, and close button are already laid. */
    protected abstract void layoutTiles(MenuHolder holder);

    /** The info-pane title for this hub, or {@code null} to keep the theme default. */
    protected String infoTitle() {
        return null;
    }

    protected List<String> infoLore() {
        return List.of();
    }

    @Override
    public void render(MenuHolder holder) {
        MenuLayout layout = layout();
        MenuTheme theme = theme();
        holder.begin(layout.size(), MenuText.title(layout.titleTemplate(), caps));
        MenuIcons.fillAll(holder, layout); // a solid glass backdrop — the tiles pop against it
        placeInfo(holder, layout, theme);
        if (layout.closeSlot() >= 0) {
            holder.set(layout.closeSlot(), MenuIcons.plain(theme.close()),
                    click -> click.player().closeInventory());
        }
        layoutTiles(holder);
    }

    private void placeInfo(MenuHolder holder, MenuLayout layout, MenuTheme theme) {
        int slot = theme.infoSlot();
        if (slot < 0 || slot >= layout.size() || !layout.paneSlots().contains(slot)) {
            return;
        }
        String title = infoTitle();
        NavButton info = title == null ? theme.info()
                : new NavButton(theme.info().material(), theme.info().fallback(), title, infoLore());
        holder.set(slot, MenuIcons.plain(info), null);
    }

    /** Place one tile at {@code slot} bound to {@code action} ({@code null} for a non-interactive tile). */
    protected final void tile(MenuHolder holder, int slot, org.bukkit.inventory.ItemStack icon,
                              ClickAction action) {
        holder.set(slot, icon, action);
    }

    /** Open a sibling menu for the clicker (used by a drill-into-menu tile), respecting that menu's permission. */
    protected static void openMenu(MenuClick click, Menu target) {
        String perm = target.permission();
        if (perm != null && !click.player().hasPermission(perm)) {
            click.player().closeInventory();
            return;
        }
        // Remember this hub as the target's opener, so its back button returns here instead of just closing.
        target.open(click.player(), click.holder().menu());
    }
}
