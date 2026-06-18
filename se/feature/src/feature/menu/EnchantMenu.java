package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.caps.Capabilities;

/**
 * The direct-apply enchant menu, now built on the shared framework ({@link PagedMenu}): each enchant in the
 * published catalog is a clickable icon that applies it (level 1) to the viewer's held item through the
 * {@link ItemEnchanter} — the visual equivalent of {@code /se enchant}. The framework owns pagination,
 * the nav/close buttons, the title (with cross-version truncation) and the Folia open-hop; this subclass
 * supplies only the catalog, the icon, and the apply-on-click behaviour.
 *
 * <p>Registered as {@code "apply"} (the §K merchant "Enchanter" is a separate buy shop). The book/scroll/
 * dust application <em>economy</em> is a separate gesture surface; this menu is the direct-apply one. A
 * level picker remains a follow-up — clicks apply at level 1.
 */
public final class EnchantMenu extends PagedMenu<EnchantDef> {

    private final ContentHolder content;
    private final ItemEnchanter enchanter;
    private final Consumer<Player> refreshWorn;

    public EnchantMenu(ContentHolder content, ItemEnchanter enchanter, Consumer<Player> refreshWorn,
                       Capabilities caps) {
        super("apply", MenuLayout.paged("StarEnchants"), caps);
        this.content = Objects.requireNonNull(content, "content");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.refreshWorn = Objects.requireNonNull(refreshWorn, "refreshWorn");
    }

    @Override
    protected List<EnchantDef> items(MenuHolder holder) {
        return content.library().catalog();
    }

    @Override
    protected void onSelect(MenuClick click, EnchantDef def) {
        applyEnchant(click.player(), def);
    }

    private void applyEnchant(Player player, EnchantDef def) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ApplyResult result = enchanter.applyEnchant(held, def.key(), 1); // level 1; a level picker is a follow-up
        if (result.ok()) {
            player.getInventory().setItemInMainHand(held); // write the mutated copy back
            refreshWorn.accept(player);                    // re-resolve the cached WornState (no equip event fires)
        }
        player.sendMessage(result.message());
    }

    @Override
    @SuppressWarnings("deprecation") // setDisplayName/setLore(String): the floor-stable item-meta path
    protected ItemStack icon(EnchantDef def) {
        ItemStack item = new ItemStack(iconMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(def.display());
            List<String> lore = new ArrayList<>();
            if (!def.description().isBlank()) {
                lore.add("§7" + def.description());
            }
            lore.add("§8applies to: §7" + String.join(", ", def.appliesTo()));
            lore.add("§8max level: §7" + def.maxLevel());
            lore.add("§eClick to apply to your held item.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The icon Material, resolved by NAME (cross-version-safe; never a hard constant). */
    private static Material iconMaterial() {
        return firstMaterial("ENCHANTED_BOOK", "BOOK", "PAPER");
    }

    /** The first of {@code names} that exists on this server (resolved by name), or STONE as a last resort. */
    private static Material firstMaterial(String... names) {
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.STONE; // present on every version
    }
}
