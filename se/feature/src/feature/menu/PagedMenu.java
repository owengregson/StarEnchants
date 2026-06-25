package feature.menu;

import compile.load.MenusConfig;
import item.mint.ItemFactory;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The reusable base for a paginated list/browse menu (docs/v3-directives.md §K): it owns pagination, the
 * filler nav row, the prev/next/back/close buttons, and title rendering. A subclass overrides
 * {@link #items}/{@link #icon}/{@link #onSelect} (+ optional {@link #titleFor}/{@link #showBack}/{@link #onBack});
 * a drill-down browser branches those on the holder's {@link MenuHolder#view()} cursor.
 *
 * @param <T> the content row type (an {@code EnchantDef}, {@code SetDef}, a reference entry, …)
 */
public abstract class PagedMenu<T> implements Menu {

    private final String name;
    private final Supplier<MenuLayout> layout;
    private final Capabilities caps;

    /** Fixed-layout form (tests/fixtures): the programmatic default is used verbatim, no menus/ override. */
    protected PagedMenu(String name, MenuLayout layout, Capabilities caps) {
        this(name, layout, caps, MenusConfig::empty);
    }

    /**
     * Canonical form: the effective layout is {@code defaultLayout} merged with this menu's
     * {@code menus/<name>.yml} override (§L), re-resolved on every render so a {@code /se reload} re-lays-out
     * the next open.
     */
    protected PagedMenu(String name, MenuLayout defaultLayout, Capabilities caps, Supplier<MenusConfig> menus) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(defaultLayout, "defaultLayout");
        Objects.requireNonNull(menus, "menus");
        this.layout = () -> MenuLayout.from(defaultLayout, menus.get().forMenu(this.name).orElse(null));
        this.caps = caps; // nullable in pure tests; MenuText degrades to the conservative 32-char cap
    }

    @Override
    public final String name() {
        return name;
    }

    protected final MenuLayout layout() {
        return layout.get();
    }

    /** The content rows to page through for the holder's current view. */
    protected abstract List<T> items(MenuHolder holder);

    protected abstract ItemStack icon(MenuHolder holder, T item);

    protected abstract void onSelect(MenuClick click, T item);

    /** The base (page-suffix-free) title for the holder's current view; defaults to the layout title. */
    protected String titleFor(MenuHolder holder) {
        return layout().titleTemplate();
    }

    protected boolean showBack(MenuHolder holder) {
        return false;
    }

    /** Default closes; a drill-down menu pops to its parent view and re-renders. */
    protected void onBack(MenuClick click) {
        click.player().closeInventory();
    }

    @Override
    public void render(MenuHolder holder) {
        MenuLayout layout = layout(); // resolve the live config-merged layout once per render
        List<T> items = items(holder);
        int perPage = layout.contentSlots();
        int pages = Paging.pageCount(items.size(), perPage);
        int page = Paging.clampPage(holder.page(), pages);
        holder.setPage(page);

        String base = titleFor(holder);
        String withSuffix = pages > 1 ? base + "  (" + (page + 1) + "/" + pages + ")" : base;
        holder.begin(layout.size(), MenuText.title(withSuffix, caps));

        int start = Paging.indexFor(page, 0, perPage);
        for (int i = 0; i < perPage && start + i < items.size(); i++) {
            T item = items.get(start + i);
            holder.set(i, icon(holder, item), click -> onSelect(click, item));
        }

        fillNavRow(holder, layout);
        if (page > 0) {
            bind(holder, layout.prevSlot(), navIcon("ARROW", "&ePrevious"),
                    click -> { click.holder().setPage(page - 1); reopen(click); });
        }
        if (page < pages - 1) {
            bind(holder, layout.nextSlot(), navIcon("ARROW", "&eNext"),
                    click -> { click.holder().setPage(page + 1); reopen(click); });
        }
        if (showBack(holder)) {
            bind(holder, layout.backSlot(), navIcon("OAK_DOOR", "&eBack"), this::onBack);
        }
        bind(holder, layout.closeSlot(), navIcon("BARRIER", "&cClose"), click -> click.player().closeInventory());
    }

    private void fillNavRow(MenuHolder holder, MenuLayout layout) {
        Material filler = ItemFactory.material(layout.fillerMaterial(), Material.AIR);
        if (filler == Material.AIR) {
            return; // blank/unknown filler token → leave the nav row empty
        }
        ItemStack pane = ItemFactory.build(filler, " ", List.of());
        for (int slot = layout.navRowStart(); slot < layout.size(); slot++) {
            holder.set(slot, pane, null);
        }
    }

    private void bind(MenuHolder holder, int slot, ItemStack icon, ClickAction action) {
        if (slot >= 0) {
            holder.set(slot, icon, action);
        }
    }

    /** A nav button icon resolved by name (cross-version), falling back to {@code PAPER} for an absent token. */
    protected static ItemStack navIcon(String materialToken, String name) {
        return ItemFactory.build(ItemFactory.material(materialToken, Material.PAPER), name, List.of());
    }

    /**
     * The first of {@code names} that exists on this server (resolved by name — cross-version-safe; never a
     * hard {@code Material} constant), or {@code STONE} as a last resort.
     */
    protected static Material material(String... names) {
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.STONE; // present on every version
    }
}
