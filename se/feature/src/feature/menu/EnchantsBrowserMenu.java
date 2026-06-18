package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import compile.load.MenusConfig;
import compile.load.TierRegistry;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The read-only enchant catalog browser (docs/v3-directives.md §K — "Enchants browser, tier → enchant"):
 * a two-level drill-down on the shared framework. The first view lists the rarity tiers that have at least
 * one enchant (coloured by {@link TierRegistry}); selecting a tier drills into the paged list of that tier's
 * enchants, each icon carrying the per-enchant detail (description, applicable item groups, max level, and
 * the §G requires/blacklist relationships) as its tooltip. Browse-only — clicking an enchant does nothing
 * but is informative; the {@link AdminBrowserMenu} is the privileged grant counterpart.
 *
 * <p>The drill state lives on the {@link MenuHolder}: {@code view} is {@code "tier"} (default) or
 * {@code "enchant"}, and {@code selection} is the tier being viewed. The framework supplies pagination, the
 * back button (enchant view → tier list), and the title.
 */
public final class EnchantsBrowserMenu extends PagedMenu<EnchantsBrowserMenu.Row> {

    /** A row is either a tier bucket (tier view) or an enchant (enchant view). */
    record Row(String tier, EnchantDef enchant) {
        boolean isTier() {
            return enchant == null;
        }
    }

    static final String VIEW_ENCHANT = "enchant";

    private final ContentHolder content;

    /** Default-layout form (tests/fixtures). */
    public EnchantsBrowserMenu(ContentHolder content, Capabilities caps) {
        this(content, caps, MenusConfig::empty);
    }

    public EnchantsBrowserMenu(ContentHolder content, Capabilities caps, Supplier<MenusConfig> menus) {
        this(content, caps, "enchants", "&3Enchants", menus);
    }

    EnchantsBrowserMenu(ContentHolder content, Capabilities caps, String name, String title,
                        Supplier<MenusConfig> menus) {
        super(name, MenuLayout.paged(title), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    protected List<Row> items(MenuHolder holder) {
        var library = content.library();
        if (VIEW_ENCHANT.equals(holder.view()) && holder.selection() != null) {
            String defaultTier = library.tiers().defaultTier();
            List<Row> rows = new ArrayList<>();
            for (EnchantDef def : BrowseFilters.enchantsOfTier(library.catalog(), holder.selection(), defaultTier)) {
                rows.add(new Row(holder.selection(), def));
            }
            return rows;
        }
        // Default: the tier list.
        TierRegistry tiers = library.tiers();
        List<String> order = tiers.tiers().stream().map(TierRegistry.Tier::name).toList();
        List<Row> rows = new ArrayList<>();
        for (String tier : BrowseFilters.populatedTiers(library.catalog(), order, tiers.defaultTier())) {
            rows.add(new Row(tier, null));
        }
        return rows;
    }

    @Override
    protected ItemStack icon(MenuHolder holder, Row row) {
        return row.isTier() ? tierIcon(row.tier()) : enchantIcon(row.enchant());
    }

    @Override
    protected void onSelect(MenuClick click, Row row) {
        if (row.isTier()) {
            MenuHolder holder = click.holder();
            holder.setView(VIEW_ENCHANT);
            holder.setSelection(row.tier());
            holder.setPage(0);
            reopen(click);
        }
        // An enchant row is browse-only: its tooltip is the detail; clicking does nothing.
    }

    @Override
    protected String titleFor(MenuHolder holder) {
        if (VIEW_ENCHANT.equals(holder.view()) && holder.selection() != null) {
            return tierColor(holder.selection()) + capitalize(holder.selection()) + " &3Enchants";
        }
        return layout().titleTemplate();
    }

    @Override
    protected boolean showBack(MenuHolder holder) {
        return VIEW_ENCHANT.equals(holder.view());
    }

    @Override
    protected void onBack(MenuClick click) {
        MenuHolder holder = click.holder();
        holder.setView(null);
        holder.setSelection(null);
        holder.setPage(0);
        reopen(click);
    }

    private ItemStack tierIcon(String tier) {
        int count = BrowseFilters.enchantsOfTier(
                content.library().catalog(), tier, content.library().tiers().defaultTier()).size();
        List<String> lore = List.of("&7" + count + " enchant" + (count == 1 ? "" : "s"), "&eClick to browse this tier.");
        return ItemFactory.build(material("ENCHANTED_BOOK", "BOOK", "PAPER"),
                tierColor(tier) + capitalize(tier), lore);
    }

    /** A rich enchant icon whose tooltip is the per-enchant detail (description, applies-to, level, §G). */
    static ItemStack enchantIcon(EnchantDef def) {
        List<String> lore = new ArrayList<>();
        if (!def.description().isBlank()) {
            lore.add("&7" + def.description());
        }
        lore.add("&8applies to: &7" + String.join(", ", def.appliesTo()));
        lore.add("&8max level: &7" + def.maxLevel());
        if (!def.requires().isEmpty()) {
            lore.add("&8requires: &7" + String.join(", ", def.requires()));
        }
        if (!def.blacklist().isEmpty()) {
            lore.add("&8conflicts: &7" + String.join(", ", def.blacklist()));
        }
        return ItemFactory.build(material("ENCHANTED_BOOK", "BOOK", "PAPER"), def.display(), lore);
    }

    /** The tier's legacy colour code (e.g. {@code &b}), or grey when the tier is unregistered. */
    private String tierColor(String tier) {
        TierRegistry.Tier t = content.library().tiers().tier(tier);
        return t != null && !t.color().isBlank() ? t.color() : "&7";
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Exposed for the admin browser, which reuses the rich enchant icon. */
    static Material bookMaterial() {
        return material("ENCHANTED_BOOK", "BOOK", "PAPER");
    }
}
