package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantBookConfig;
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
    private final Supplier<String> nameTemplate; // the enchant-book name template — icons match the book (§I/§K)

    /** Default-layout form (tests/fixtures). */
    public EnchantMenu(ContentHolder content, ItemEnchanter enchanter, Consumer<Player> refreshWorn,
                       Capabilities caps) {
        this(content, enchanter, refreshWorn, caps, MenusConfig::empty);
    }

    public EnchantMenu(ContentHolder content, ItemEnchanter enchanter, Consumer<Player> refreshWorn,
                       Capabilities caps, Supplier<MenusConfig> menus) {
        this(content, enchanter, refreshWorn, caps, menus, () -> EnchantBookConfig.defaults().name());
    }

    public EnchantMenu(ContentHolder content, ItemEnchanter enchanter, Consumer<Player> refreshWorn,
                       Capabilities caps, Supplier<MenusConfig> menus, Supplier<String> nameTemplate) {
        super("apply", MenuLayout.paged("&d&lApply Enchant"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.refreshWorn = Objects.requireNonNull(refreshWorn, "refreshWorn");
        this.nameTemplate = Objects.requireNonNull(nameTemplate, "nameTemplate");
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
    protected String infoTitle(MenuHolder holder) {
        return "&d&lApply Enchant";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        return List.of("&7Click an enchant to apply it", "&7straight to your held item.");
    }

    @Override
    protected ItemStack icon(MenuHolder holder, EnchantDef def) {
        List<String> lore = new ArrayList<>(MenuText.describe(def.description(), "&7"));
        lore.add("&8applies to: &7" + String.join(", ", def.appliesTo()));
        lore.add("&8max level: &7" + def.maxLevel());
        lore.add("&eClick to apply to your held item.");
        // The icon name is styled by the enchant-book name template (tier colour + any bold/underline), so it
        // matches the unapplied book; level-less here (no specific level in the apply menu).
        String name = MenuText.enchantName(nameTemplate.get(),
                MenuText.tierColor(content.library().tiers(), def.tier()), def.display(), "");
        return ItemFactory.build(material("ENCHANTED_BOOK", "BOOK", "PAPER"), name, lore);
    }
}
