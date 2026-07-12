package feature.menu;

import compile.load.ContentHolder;
import compile.load.MaskDef;
import compile.load.MenusConfig;
import feature.mask.MaskService;
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
 * The masks browser (ADR-0053, the ADR-0030 chrome): every mask rendered as its ACTUAL textured head +
 * likeness. Browsing is open to everyone; an operator ({@code starenchants.admin}) clicks a mask to mint one
 * to themselves to drag onto a helmet. Unlike the pets browser there is no level drill — a mask is a single
 * identity (no levels, §1), so a flat mint tile carries it whole. Applying / removing a mask onto gear stays a
 * gesture ({@code MaskListener}/{@code MaskRemoveListener}, §3); only the catalogue + operator mint live here.
 */
public final class MasksBrowserMenu extends PagedMenu<MaskDef> {

    private final ContentHolder content;
    private final MaskService masks;
    private final Messages messages;

    public MasksBrowserMenu(ContentHolder content, MaskService masks, Capabilities caps,
                            Messages messages, Supplier<MenusConfig> menus, VanillaEnchants vanilla) {
        super("masks", MenuLayout.paged("&8&lMasks"), caps, menus, vanilla);
        this.content = Objects.requireNonNull(content, "content");
        this.masks = Objects.requireNonNull(masks, "masks");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected List<MaskDef> items(MenuHolder holder) {
        return content.library().masks();
    }

    @Override
    protected ItemStack icon(MenuHolder holder, MaskDef def) {
        // The icon IS the minted item (the receive-tile rule): the textured head with the real likeness.
        ItemStack head = masks.mint(def.key());
        return head != null ? head
                : item.mint.ItemFactory.build(material("PLAYER_HEAD", "PAPER"), "&c" + def.display(),
                        List.of("&7This mask's content failed to mint."));
    }

    @Override
    protected void onSelect(MenuClick click, MaskDef def) {
        Player player = click.player();
        if (!player.hasPermission("starenchants.admin")) {
            messages.send(player, "menu.masks.preview-only");
            return;
        }
        Inventories.giveOrDrop(player, masks.mint(def.key()));
        messages.send(player, "menu.masks.minted", "MASK", def.display());
    }

    @Override
    protected String infoTitle(MenuHolder holder) {
        return "&8&lMasks";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        return List.of("&7Every mask — drag one onto a helmet", "&7to wear its likeness and effects.", "",
                "&eOperators click a mask to mint it.");
    }
}
