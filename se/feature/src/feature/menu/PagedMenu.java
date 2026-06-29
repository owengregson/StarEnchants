package feature.menu;

import compile.load.MenusConfig;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The reusable base for a paginated list/browse menu (docs/v3-directives.md §K, ADR-0030): it owns pagination,
 * the decorative frame, the themed prev/next/back/close buttons and the info pane, and title rendering. A
 * subclass overrides {@link #items}/{@link #icon}/{@link #onSelect} (+ optional
 * {@link #titleFor}/{@link #showBack}/{@link #onBack}/{@link #infoTitle}/{@link #infoLore}); a drill-down
 * browser branches those on the holder's {@link MenuHolder#view()} cursor. Content lands at
 * {@link MenuLayout#contentSlot} — a {@code BORDER} frame insets it inside the perimeter.
 *
 * @param <T> the content row type (an {@code EnchantDef}, {@code SetDef}, a reference entry, …)
 */
public abstract class PagedMenu<T> implements Menu {

    private final String name;
    private final Supplier<MenuLayout> layout;
    private final Supplier<MenuTheme> theme;
    private final Capabilities caps;

    /** Fixed-layout form (tests/fixtures): the programmatic default is used verbatim, no menus/ override. */
    protected PagedMenu(String name, MenuLayout layout, Capabilities caps) {
        this(name, layout, caps, MenusConfig::empty);
    }

    /**
     * Canonical form: the effective layout + chrome are {@code defaultLayout}/{@link MenuTheme#DEFAULT} merged
     * with this menu's {@code menus/<name>.yml} override (§L), re-resolved on every render so a {@code /se
     * reload} re-lays-out the next open.
     */
    protected PagedMenu(String name, MenuLayout defaultLayout, Capabilities caps, Supplier<MenusConfig> menus) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(defaultLayout, "defaultLayout");
        Objects.requireNonNull(menus, "menus");
        this.layout = () -> MenuLayout.from(defaultLayout, menus.get().forMenu(this.name).orElse(null));
        this.theme = () -> MenuTheme.from(MenuTheme.DEFAULT, menus.get().forMenu(this.name).orElse(null));
        this.caps = caps; // nullable in pure tests; MenuText degrades to the conservative 32-char cap
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

    /** The info-pane title for the current view, or {@code null} to keep the theme's default info pane. */
    protected String infoTitle(MenuHolder holder) {
        return null;
    }

    /** The info-pane lore for the current view (used only when {@link #infoTitle} is non-null). */
    protected List<String> infoLore(MenuHolder holder) {
        return List.of();
    }

    @Override
    public void render(MenuHolder holder) {
        MenuLayout layout = layout(); // resolve the live config-merged geometry + chrome once per render
        MenuTheme theme = theme();
        List<T> items = items(holder);
        int perPage = Math.max(1, layout.contentSlotCount());
        int pages = Paging.pageCount(items.size(), perPage);
        int page = Paging.clampPage(holder.page(), pages);
        holder.setPage(page);

        String base = titleFor(holder);
        String withSuffix = pages > 1 ? base + "  (" + (page + 1) + "/" + pages + ")" : base;
        holder.begin(layout.size(), MenuText.title(withSuffix, caps));

        fillFrame(holder, layout);

        int start = Paging.indexFor(page, 0, perPage);
        for (int i = 0; i < perPage && start + i < items.size(); i++) {
            T item = items.get(start + i);
            int slot = layout.contentSlot(i);
            if (slot >= 0) {
                holder.set(slot, icon(holder, item), click -> onSelect(click, item));
            }
        }

        placeInfo(holder, layout, theme);

        if (page > 0) {
            bind(holder, layout.prevSlot(), MenuIcons.page(theme.prev(), page, pages),
                    click -> { click.holder().setPage(page - 1); reopen(click); });
        }
        if (page < pages - 1) {
            bind(holder, layout.nextSlot(), MenuIcons.page(theme.next(), page + 2, pages),
                    click -> { click.holder().setPage(page + 1); reopen(click); });
        }
        if (showBack(holder)) {
            bind(holder, layout.backSlot(), MenuIcons.plain(theme.back()), this::onBack);
        }
        bind(holder, layout.closeSlot(), MenuIcons.plain(theme.close()),
                click -> click.player().closeInventory());
    }

    /** Lay the decorative frame panes for the resolved {@link Frame}; a blank filler token draws nothing. */
    private void fillFrame(MenuHolder holder, MenuLayout layout) {
        ItemStack pane = MenuIcons.pane(layout.fillerMaterial());
        if (pane == null) {
            return;
        }
        for (int slot : layout.paneSlots()) {
            holder.set(slot, pane, null);
        }
    }

    /** Place the info pane, but only where it would sit on a decorative cell (never clobbering paged content). */
    private void placeInfo(MenuHolder holder, MenuLayout layout, MenuTheme theme) {
        int slot = theme.infoSlot();
        if (slot < 0 || slot >= layout.size() || !layout.paneSlots().contains(slot)) {
            return;
        }
        String title = infoTitle(holder);
        NavButton info = title == null ? theme.info()
                : new NavButton(theme.info().material(), theme.info().fallback(), title, infoLore(holder));
        holder.set(slot, MenuIcons.plain(info), null);
    }

    private void bind(MenuHolder holder, int slot, ItemStack icon, ClickAction action) {
        if (slot >= 0) {
            holder.set(slot, icon, action);
        }
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
