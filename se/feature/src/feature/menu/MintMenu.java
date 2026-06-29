package feature.menu;

import compile.load.MenusConfig;
import item.lang.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The operator "Mint Items" GUI (ADR-0030): a paged grid of every mintable plugin item, each tile rendered as
 * the very item it grants (so it looks like what you get), a click mints a fresh one to the operator. Gated by
 * {@code starenchants.admin}. Driven by {@link MintCatalog}; an entry whose subsystem is disabled (its mint
 * thunk throws) is silently skipped rather than shown broken.
 */
public final class MintMenu extends PagedMenu<MintMenu.Tile> {

    private static final System.Logger LOG = System.getLogger("StarEnchants.Menu");

    /** A renderable mint entry: the template stack (for the icon) plus the thunk that mints a fresh one. */
    record Tile(ItemStack template, Supplier<ItemStack> mint, String label) {
    }

    private final MintCatalog catalog;
    private final Messages messages;

    public MintMenu(MintCatalog catalog, Capabilities caps, Messages messages, Supplier<MenusConfig> menus) {
        super("mint", MenuLayout.paged("&6&lMint Items"), caps, menus);
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String permission() {
        return "starenchants.admin";
    }

    @Override
    protected List<Tile> items(MenuHolder holder) {
        List<Tile> out = new ArrayList<>();
        for (MintCatalog.Entry entry : catalog.entries()) {
            ItemStack template = mintQuietly(entry);
            if (template != null) {
                out.add(new Tile(template, entry.mint(), entry.label()));
            }
        }
        return out;
    }

    @Override
    protected ItemStack icon(MenuHolder holder, Tile tile) {
        return MenuIcons.receiveTile(tile.template(), "&eClick to receive one.");
    }

    @Override
    protected void onSelect(MenuClick click, Tile tile) {
        Player player = click.player();
        ItemStack minted;
        try {
            minted = tile.mint().get();
        } catch (RuntimeException disabled) {
            LOG.log(System.Logger.Level.WARNING, "mint '" + tile.label() + "' failed", disabled);
            messages.send(player, "menu.mint.unavailable", "ITEM", tile.label());
            return;
        }
        MenuItems.giveOrDrop(player, minted);
        messages.send(player, "menu.mint.given", "ITEM", displayName(minted, tile.label()));
    }

    @Override
    protected String infoTitle(MenuHolder holder) {
        return "&6&lMint Items";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        return List.of("&7Click any item to mint one", "&7straight to your inventory.");
    }

    /** Mint a template for the icon, returning {@code null} when the entry's subsystem refuses (disabled/gated). */
    private static ItemStack mintQuietly(MintCatalog.Entry entry) {
        try {
            return entry.mint().get();
        } catch (RuntimeException disabled) {
            // A gated subsystem (e.g. scrolls off) is expected to refuse; log at DEBUG so a genuine bug is
            // still discoverable without spamming the console on every render.
            LOG.log(System.Logger.Level.DEBUG, "mint tile '" + entry.label() + "' skipped", disabled);
            return null;
        }
    }

    /** The minted item's own display name for the confirmation, falling back to the catalogue label. */
    @SuppressWarnings("deprecation") // getDisplayName(): the floor-stable item-meta path
    private static String displayName(ItemStack minted, String label) {
        if (minted.hasItemMeta() && minted.getItemMeta() != null && minted.getItemMeta().hasDisplayName()) {
            return minted.getItemMeta().getDisplayName();
        }
        return label;
    }
}
