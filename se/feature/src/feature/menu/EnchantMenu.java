package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import compile.load.MenusConfig;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.caps.Capabilities;

/**
 * The direct-apply enchant menu: clicking an enchant applies it (level 1) to the held item via
 * {@link ItemEnchanter} — the visual {@code /se enchant}. Registered as {@code "apply"}, distinct from the
 * §K merchant "Enchanter" buy shop and from the book/scroll/dust application economy.
 */
public final class EnchantMenu extends PagedMenu<EnchantDef> {

    private final ContentHolder content;
    private final ItemEnchanter enchanter;
    private final Consumer<Player> refreshWorn;

    /** Default-layout form (tests/fixtures). */
    public EnchantMenu(ContentHolder content, ItemEnchanter enchanter, Consumer<Player> refreshWorn,
                       Capabilities caps) {
        this(content, enchanter, refreshWorn, caps, MenusConfig::empty);
    }

    public EnchantMenu(ContentHolder content, ItemEnchanter enchanter, Consumer<Player> refreshWorn,
                       Capabilities caps, Supplier<MenusConfig> menus) {
        super("apply", MenuLayout.paged("StarEnchants"), caps, menus);
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
        ApplyResult result = enchanter.applyEnchant(held, def.key(), 1);
        if (result.ok()) {
            player.getInventory().setItemInMainHand(held);
            refreshWorn.accept(player); // no equip event fires, so re-resolve the cached WornState by hand
        }
        player.sendMessage(result.message());
    }

    @Override
    @SuppressWarnings("deprecation") // setDisplayName/setLore(String): the floor-stable item-meta path
    protected ItemStack icon(MenuHolder holder, EnchantDef def) {
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

    /** Resolved by name, never a hard constant (cross-version-safe). */
    private static Material iconMaterial() {
        return firstMaterial("ENCHANTED_BOOK", "BOOK", "PAPER");
    }

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
