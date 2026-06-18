package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import feature.carrier.CarrierService;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The admin enchant browser (docs/v3-directives.md §K — "Admin browser (all enchants): grant/apply, at 100%
 * rate"): a flat paged list of every catalog enchant; clicking one mints a <strong>guaranteed
 * (100%-success) level-1 book</strong> of that enchant and gives it to the admin. The privileged
 * counterpart of the read-only {@link EnchantsBrowserMenu}; gated by {@code starenchants.admin} (a real
 * permission node, not an {@code isOp()} check — §K). A level picker is a follow-up (consistent with the
 * direct-apply menu).
 */
public final class AdminBrowserMenu extends PagedMenu<EnchantDef> {

    private final ContentHolder content;
    private final CarrierService carriers;

    public AdminBrowserMenu(ContentHolder content, CarrierService carriers, Capabilities caps) {
        super("admin", MenuLayout.paged("&cAdmin &8• &cEnchants"), caps);
        this.content = Objects.requireNonNull(content, "content");
        this.carriers = Objects.requireNonNull(carriers, "carriers");
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
    protected ItemStack icon(EnchantDef def) {
        List<String> lore = new ArrayList<>();
        if (!def.description().isBlank()) {
            lore.add("&7" + def.description());
        }
        lore.add("&8applies to: &7" + String.join(", ", def.appliesTo()));
        lore.add("&8max level: &7" + def.maxLevel());
        lore.add("&aClick to receive a guaranteed book.");
        return ItemFactory.build(EnchantsBrowserMenu.bookMaterial(), def.display(), lore);
    }

    @Override
    protected void onSelect(MenuClick click, EnchantDef def) {
        Player player = click.player();
        ItemStack book = carriers.mintBook(def.key(), 1, 100); // 100% success — the admin "guaranteed" book
        giveOrDrop(player, book);
        player.sendMessage("§aGranted a guaranteed §f" + def.display() + " §abook.");
    }

    /** Add {@code item} to the player's inventory, dropping any overflow at their feet (inventory-full guard). */
    private static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        // The click fires on the player's own region thread, so dropping at their location is in-region (Folia-safe).
        for (ItemStack over : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), over);
        }
    }
}
