package feature.menu;

import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The in-game reference browser (docs/v3-directives.md §K — "Effects/reference browser"; §M): a two-level
 * drill-down over the {@link ReferenceCatalog}. The index lists the five categories (Effects, Selectors,
 * Triggers, Conditions, Variables); selecting one drills into its paged entry list, each icon's tooltip
 * carrying that entry's detail (doc, params, example / metadata). Read-only — it mirrors the
 * {@code /se effects|selectors|triggers|conditions|variables} reference commands (§J).
 *
 * <p>The catalog is built once at construction (the registries are deterministic and server-free); the
 * drill state lives on the holder ({@code view} = the category, or {@code null} for the index).
 */
public final class ReferenceBrowserMenu extends PagedMenu<ReferenceBrowserMenu.Row> {

    /** A row is either a category (index view) or a reference entry (category view). */
    record Row(String category, ReferenceCatalog.Entry entry) {
        boolean isCategory() {
            return entry == null;
        }
    }

    private final ReferenceCatalog catalog;

    public ReferenceBrowserMenu(Capabilities caps) {
        super("reference", MenuLayout.paged("&3Reference"), caps);
        this.catalog = ReferenceCatalog.build();
    }

    @Override
    protected List<Row> items(MenuHolder holder) {
        String view = holder.view();
        List<Row> rows = new ArrayList<>();
        if (view != null) {
            for (ReferenceCatalog.Entry entry : catalog.entries(view)) {
                rows.add(new Row(view, entry));
            }
            return rows;
        }
        for (String category : catalog.categories()) {
            rows.add(new Row(category, null));
        }
        return rows;
    }

    @Override
    protected ItemStack icon(Row row) {
        if (row.isCategory()) {
            int count = catalog.entries(row.category()).size();
            return ItemFactory.build(categoryMaterial(row.category()), "&3" + row.category(),
                    List.of("&7" + count + " entr" + (count == 1 ? "y" : "ies"), "&eClick to browse."));
        }
        return ItemFactory.build(categoryMaterial(row.category()), "&b" + row.entry().title(), row.entry().lore());
    }

    @Override
    protected void onSelect(MenuClick click, Row row) {
        if (row.isCategory()) {
            MenuHolder holder = click.holder();
            holder.setView(row.category());
            holder.setPage(0);
            reopen(click);
        }
        // An entry is read-only: its tooltip is the detail.
    }

    @Override
    protected String titleFor(MenuHolder holder) {
        return holder.view() != null ? "&3Reference &8• &3" + holder.view() : layout().titleTemplate();
    }

    @Override
    protected boolean showBack(MenuHolder holder) {
        return holder.view() != null;
    }

    @Override
    protected void onBack(MenuClick click) {
        click.holder().setView(null);
        click.holder().setPage(0);
        reopen(click);
    }

    /** A representative, cross-version-safe icon material per category (resolved by name). */
    private static Material categoryMaterial(String category) {
        return switch (category) {
            case ReferenceCatalog.EFFECTS -> material("NETHER_STAR", "BLAZE_POWDER", "PAPER");
            case ReferenceCatalog.SELECTORS -> material("ENDER_EYE", "COMPASS", "PAPER");
            case ReferenceCatalog.TRIGGERS -> material("REDSTONE", "LEVER", "PAPER");
            case ReferenceCatalog.CONDITIONS -> material("COMPARATOR", "REPEATER", "PAPER");
            case ReferenceCatalog.VARIABLES -> material("NAME_TAG", "OAK_SIGN", "PAPER");
            default -> material("PAPER", "BOOK");
        };
    }
}
