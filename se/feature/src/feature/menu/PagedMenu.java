package feature.menu;

import item.mint.ItemFactory;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The reusable base for a paginated list/browse menu (docs/v3-directives.md §K): it owns pagination, the
 * filler nav row, the prev/next/back/close buttons, title rendering + cross-version truncation, and the
 * Folia open-hop — a subclass supplies only the content, the icon for each item, and what a click does.
 * Both a flat catalog (the enchant menu) and a drill-down browser (tier → enchant → detail, built on the
 * holder's {@link MenuHolder#view()}/{@link MenuHolder#selection()} cursor) are expressed as overrides of
 * {@link #items}/{@link #icon}/{@link #onSelect} (+ optional {@link #titleFor}/{@link #showBack}/{@link #onBack}).
 *
 * @param <T> the content row type (an {@code EnchantDef}, {@code SetDef}, a reference entry, …)
 */
public abstract class PagedMenu<T> implements Menu {

    private final String name;
    private final MenuLayout layout;
    private final Capabilities caps;

    protected PagedMenu(String name, MenuLayout layout, Capabilities caps) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(java.util.Locale.ROOT);
        this.layout = Objects.requireNonNull(layout, "layout");
        this.caps = caps; // nullable in pure tests; MenuText degrades to the conservative 32-char cap
    }

    @Override
    public final String name() {
        return name;
    }

    protected final MenuLayout layout() {
        return layout;
    }

    /** The content rows to page through for the holder's current view (drill-down menus branch on view()). */
    protected abstract List<T> items(MenuHolder holder);

    /** The icon for one content row. */
    protected abstract ItemStack icon(T item);

    /** Handle a click on a content row (apply, drill in, grant, …). */
    protected abstract void onSelect(MenuClick click, T item);

    /** The base (page-suffix-free) title for the holder's current view; defaults to the layout title. */
    protected String titleFor(MenuHolder holder) {
        return layout.titleTemplate();
    }

    /** Whether a "back" button is shown for the holder's current view (drill-down menus enable it). */
    protected boolean showBack(MenuHolder holder) {
        return false;
    }

    /** Handle a "back" click — default closes; a drill-down menu pops to its parent view and re-renders. */
    protected void onBack(MenuClick click) {
        click.player().closeInventory();
    }

    @Override
    public void render(MenuHolder holder) {
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
            holder.set(i, icon(item), click -> onSelect(click, item));
        }

        fillNavRow(holder);
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

    /** Fill the reserved bottom navigation row with decorative filler panes (config material, by name). */
    private void fillNavRow(MenuHolder holder) {
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
     * hard {@code Material} constant), or {@code STONE} as a last resort. The reusable icon-material idiom for
     * a menu whose button material is a fixed preference list rather than a config token.
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
