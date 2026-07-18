package feature.menu;

import compile.load.ContentHolder;
import compile.load.MenusConfig;
import compile.load.ReforgeDef;
import feature.reforge.ReforgeService;
import item.mint.VanillaEnchants;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;
import platform.item.Inventories;
import platform.lang.Messages;

/**
 * The reforges browser (ADR-0070, the ADR-0030 chrome): every weapon reforge rendered as its catalogue icon +
 * likeness. Browsing is open to everyone; an operator ({@code starenchants.admin}) clicks a reforge to mint one
 * to themselves to drag onto a sword or axe. Like masks a reforge is a single identity (no levels), so a flat
 * mint tile carries it whole. Applying / removing a reforge onto gear stays a gesture ({@code ReforgeListener} /
 * the Item Extractor, §4); only the catalogue + operator mint live here.
 */
public final class ReforgesBrowserMenu extends PagedMenu<ReforgeDef> {

    private final ContentHolder content;
    private final ReforgeService reforges;
    private final Messages messages;

    public ReforgesBrowserMenu(ContentHolder content, ReforgeService reforges, Capabilities caps,
                               Messages messages, Supplier<MenusConfig> menus, VanillaEnchants vanilla) {
        super("reforges", MenuLayout.paged("&6&lWeapon Reforges"), caps, menus, vanilla);
        this.content = Objects.requireNonNull(content, "content");
        this.reforges = Objects.requireNonNull(reforges, "reforges");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected List<ReforgeDef> items(MenuHolder holder) {
        return content.library().reforges();
    }

    @Override
    protected ItemStack icon(MenuHolder holder, ReforgeDef def) {
        // The icon IS the minted item (the receive-tile rule): the catalogue icon with the real likeness.
        ItemStack minted = reforges.mint(def.key());
        return minted != null ? minted
                : item.mint.ItemFactory.build(material("ANVIL", "PAPER"), "&c" + def.display(),
                        List.of("&7This reforge's content failed to mint."));
    }

    @Override
    protected void onSelect(MenuClick click, ReforgeDef def) {
        Player player = click.player();
        if (!player.hasPermission("starenchants.admin")) {
            messages.send(player, "menu.reforges.preview-only");
            return;
        }
        Inventories.giveOrDrop(player, reforges.mint(def.key()));
        messages.send(player, "menu.reforges.minted", "REFORGE", def.display());
    }

    @Override
    protected String infoTitle(MenuHolder holder) {
        return "&6&lWeapon Reforges";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        return List.of("&7Every weapon reforge — drag one onto", "&7a sword or axe, then SHIFT + right-click",
                "&7while holding it to fire its ability.", "", "&eOperators click a reforge to mint it.");
    }
}
