package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import compile.load.MenusConfig;
import compile.load.TierRegistry;
import feature.carrier.CarrierService;
import item.lang.Messages;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The admin enchant browser (§K) — a THREE-level drill-down: tier groups → enchants of the tier → the
 * enchant's levels, where clicking a level mints a guaranteed (100%-success) book of that level. Privileged
 * counterpart of {@link EnchantsBrowserMenu} (which is the two-level browse-only version); gated by a real
 * {@code starenchants.admin} node. Drill state lives on the {@link MenuHolder}: {@code view} is {@code null}
 * (tiers), {@code "enchants"}, or {@code "levels"}; {@code selection} is the viewed tier; {@code payload} is
 * the chosen {@link EnchantDef} in the levels view.
 */
public final class AdminBrowserMenu extends PagedMenu<AdminBrowserMenu.Row> {

    /** A row is a tier bucket, an enchant, or a single level of an enchant. */
    record Row(String tier, EnchantDef enchant, int level) {
        static Row tier(String tier) {
            return new Row(tier, null, 0);
        }

        static Row enchant(EnchantDef enchant) {
            return new Row(null, enchant, 0);
        }

        static Row level(EnchantDef enchant, int level) {
            return new Row(null, enchant, level);
        }

        boolean isTier() {
            return tier != null;
        }

        boolean isLevel() {
            return level > 0;
        }
    }

    static final String VIEW_ENCHANTS = "enchants";
    static final String VIEW_LEVELS = "levels";

    private final ContentHolder content;
    private final CarrierService carriers;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public AdminBrowserMenu(ContentHolder content, CarrierService carriers, Capabilities caps) {
        this(content, carriers, caps, Messages.defaults());
    }

    public AdminBrowserMenu(ContentHolder content, CarrierService carriers, Capabilities caps, Messages messages) {
        this(content, carriers, caps, messages, MenusConfig::empty);
    }

    public AdminBrowserMenu(ContentHolder content, CarrierService carriers, Capabilities caps, Messages messages,
                            Supplier<MenusConfig> menus) {
        super("admin", MenuLayout.paged("&cAdmin &8• &cEnchants"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String permission() {
        return "starenchants.admin";
    }

    @Override
    protected List<Row> items(MenuHolder holder) {
        var library = content.library();
        String defaultTier = library.tiers().defaultTier();
        if (VIEW_LEVELS.equals(holder.view()) && holder.payload() instanceof EnchantDef def) {
            List<Row> rows = new ArrayList<>(Math.max(0, def.maxLevel()));
            for (int level = 1; level <= def.maxLevel(); level++) {
                rows.add(Row.level(def, level));
            }
            return rows;
        }
        if (VIEW_ENCHANTS.equals(holder.view()) && holder.selection() != null) {
            List<Row> rows = new ArrayList<>();
            for (EnchantDef def : BrowseFilters.enchantsOfTier(library.catalog(), holder.selection(), defaultTier)) {
                rows.add(Row.enchant(def));
            }
            return rows;
        }
        List<String> order = library.tiers().tiers().stream().map(TierRegistry.Tier::name).toList();
        List<Row> rows = new ArrayList<>();
        for (String tier : BrowseFilters.populatedTiers(library.catalog(), order, defaultTier)) {
            rows.add(Row.tier(tier));
        }
        return rows;
    }

    @Override
    protected ItemStack icon(MenuHolder holder, Row row) {
        if (row.isTier()) {
            return tierIcon(row.tier());
        }
        return row.isLevel() ? levelIcon(row.enchant(), row.level()) : enchantIcon(row.enchant());
    }

    @Override
    protected void onSelect(MenuClick click, Row row) {
        MenuHolder holder = click.holder();
        if (row.isTier()) {
            holder.setView(VIEW_ENCHANTS);
            holder.setSelection(row.tier());
            holder.setPage(0);
            reopen(click);
            return;
        }
        if (!row.isLevel()) { // an enchant row — drill into its levels
            holder.setView(VIEW_LEVELS);
            holder.setPayload(row.enchant());
            holder.setPage(0);
            reopen(click);
            return;
        }
        // A level row — mint that exact level as a guaranteed book; keep the menu open to grab more.
        Player player = click.player();
        ItemStack book = carriers.mintBook(row.enchant().key(), row.level(), 100); // 100 = guaranteed success
        MenuItems.giveOrDrop(player, book);
        messages.send(player, "menu.admin.granted", "DISPLAY", row.enchant().display(), "LEVEL", row.level());
    }

    @Override
    protected String titleFor(MenuHolder holder) {
        if (VIEW_LEVELS.equals(holder.view()) && holder.payload() instanceof EnchantDef def) {
            return MenuText.tierColor(content.library().tiers(), def.tier()) + def.display() + " &cLevels";
        }
        if (VIEW_ENCHANTS.equals(holder.view()) && holder.selection() != null) {
            return tierColor(holder.selection()) + capitalize(holder.selection()) + " &cEnchants";
        }
        return layout().titleTemplate();
    }

    @Override
    protected boolean showBack(MenuHolder holder) {
        return holder.view() != null;
    }

    /** Step back exactly one level: levels → enchants (keeping the tier), enchants → tiers. */
    @Override
    protected void onBack(MenuClick click) {
        MenuHolder holder = click.holder();
        if (VIEW_LEVELS.equals(holder.view())) {
            holder.setView(VIEW_ENCHANTS);
            holder.setPayload(null); // selection (the tier) is kept so we land back on the tier's enchants
        } else {
            holder.setView(null);
            holder.setSelection(null);
        }
        holder.setPage(0);
        reopen(click);
    }

    private ItemStack tierIcon(String tier) {
        int count = BrowseFilters.enchantsOfTier(
                content.library().catalog(), tier, content.library().tiers().defaultTier()).size();
        List<String> lore = List.of("&7" + count + " enchant" + (count == 1 ? "" : "s"), "&eClick to browse this tier.");
        return ItemFactory.build(EnchantsBrowserMenu.bookMaterial(), tierColor(tier) + capitalize(tier), lore);
    }

    /** A rich enchant icon whose tooltip is the per-enchant detail (§G); clicking drills into its levels. */
    private ItemStack enchantIcon(EnchantDef def) {
        List<String> lore = new ArrayList<>(MenuText.describe(def.description(), "&7"));
        lore.add("&8applies to: &7" + String.join(", ", def.appliesTo()));
        lore.add("&8max level: &7" + def.maxLevel());
        if (!def.requires().isEmpty()) {
            lore.add("&8requires: &7" + String.join(", ", def.requires()));
        }
        if (!def.blacklist().isEmpty()) {
            lore.add("&8conflicts: &7" + String.join(", ", def.blacklist()));
        }
        lore.add("&eClick to choose a level.");
        // Name styled by the enchant-book likeness (tier colour + any bold/underline), so it matches the book;
        // level-less in the enchant view.
        return ItemFactory.build(EnchantsBrowserMenu.bookMaterial(), carriers.bookDisplayName(def.key(), 0), lore);
    }

    /** One level of an enchant — clicking mints a guaranteed book of exactly that level; the icon IS that book's name. */
    private ItemStack levelIcon(EnchantDef def, int level) {
        List<String> lore = List.of("&aClick to receive a guaranteed level &f" + level + " &abook.");
        return ItemFactory.build(EnchantsBrowserMenu.bookMaterial(), carriers.bookDisplayName(def.key(), level), lore);
    }

    private String tierColor(String tier) {
        return MenuText.tierColor(content.library().tiers(), tier);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
