package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import feature.carrier.CarrierService;
import item.lang.Messages;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The admin enchant browser (§K): clicking an enchant mints a guaranteed (100%-success) level-1 book.
 * Privileged counterpart of {@link EnchantsBrowserMenu}; gated by a real {@code starenchants.admin} node.
 */
public final class AdminBrowserMenu extends PagedMenu<EnchantDef> {

    private final ContentHolder content;
    private final CarrierService carriers;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public AdminBrowserMenu(ContentHolder content, CarrierService carriers, Capabilities caps) {
        this(content, carriers, caps, Messages.defaults());
    }

    public AdminBrowserMenu(ContentHolder content, CarrierService carriers, Capabilities caps, Messages messages) {
        this(content, carriers, caps, messages, compile.load.MenusConfig::empty);
    }

    public AdminBrowserMenu(ContentHolder content, CarrierService carriers, Capabilities caps, Messages messages,
                            java.util.function.Supplier<compile.load.MenusConfig> menus) {
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
    protected List<EnchantDef> items(MenuHolder holder) {
        return content.library().catalog();
    }

    @Override
    protected ItemStack icon(MenuHolder holder, EnchantDef def) {
        List<String> lore = new ArrayList<>(MenuText.describe(def.description(), "&7"));
        lore.add("&8applies to: &7" + String.join(", ", def.appliesTo()));
        lore.add("&8max level: &7" + def.maxLevel());
        lore.add("&aClick to receive a guaranteed book.");
        String name = MenuText.tierColor(content.library().tiers(), def.tier()) + def.display();
        return ItemFactory.build(EnchantsBrowserMenu.bookMaterial(), name, lore);
    }

    @Override
    protected void onSelect(MenuClick click, EnchantDef def) {
        Player player = click.player();
        ItemStack book = carriers.mintBook(def.key(), 1, 100); // 100 = guaranteed success
        MenuItems.giveOrDrop(player, book);
        messages.send(player, "menu.admin.granted", "DISPLAY", def.display());
    }
}
