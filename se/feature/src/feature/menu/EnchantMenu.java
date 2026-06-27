package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import compile.load.MenusConfig;
import feature.apply.ApplyResult;
import feature.compat.Hands;
import feature.apply.ItemEnchanter;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        ItemStack held = Hands.mainHand(player);
        ApplyResult result = enchanter.applyEnchant(held, def.key(), 1);
        if (result.ok()) {
            Hands.setMainHand(player, held);
            refreshWorn.accept(player); // no equip event fires, so re-resolve the cached WornState by hand
        }
        player.sendMessage(result.message());
    }

    @Override
    protected ItemStack icon(MenuHolder holder, EnchantDef def) {
        List<String> lore = new ArrayList<>(MenuText.describe(def.description(), "&7"));
        lore.add("&8applies to: &7" + String.join(", ", def.appliesTo()));
        lore.add("&8max level: &7" + def.maxLevel());
        lore.add("&eClick to apply to your held item.");
        // The name's base colour is the enchant's rarity tier (ADR-0016 §2), like the applied-gear lore; a
        // display that carries its own leading colour code overrides it (EE displays are plain, so the tier shows).
        String name = MenuText.tierColor(content.library().tiers(), def.tier()) + def.display();
        return ItemFactory.build(material("ENCHANTED_BOOK", "BOOK", "PAPER"), name, lore);
    }
}
