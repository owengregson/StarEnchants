package feature.menu;

import compile.load.MenusConfig;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Material;
import platform.caps.Capabilities;

/**
 * The player-facing landing hub (ADR-0030) — the GUI a normal player opens (via {@code /enchants}) to reach
 * everything they own: the Enchanter, Alchemist and Tinkerer benches, the Godly Transmog reorder tool, and
 * the read-only Enchants / Armour-Set / Crystal browsers. Pure navigation: each tile drills into a sibling
 * menu (looked up live from the {@link MenuRegistry}, so registration order is irrelevant), and every target
 * is permission-free, so the {@code /se} admin gate never blocks a user mid-flow. Operators reach the same
 * hub via {@code /se menu hub}.
 */
public final class UserHubMenu extends HubMenu {

    private final MenuRegistry registry;

    public UserHubMenu(MenuRegistry registry, Capabilities caps, Supplier<MenusConfig> menus,
                       item.mint.VanillaEnchants vanilla) {
        super("hub", null, MenuLayout.sized(5, "&d&lStarEnchants &8• &7Menu"), caps, menus, vanilla);
        this.registry = registry;
    }

    @Override
    protected String infoTitle() {
        return "&d&lStarEnchants";
    }

    @Override
    protected List<String> infoLore() {
        return List.of("&7Welcome! Choose a station below.",
                "&7Buy, combine, salvage and browse —", "&7all from one place.");
    }

    @Override
    protected void layoutTiles(MenuHolder holder) {
        // Row 1 — the four actionable stations (the things a player DOES).
        tile(holder, 10, MenuIcons.tile(vanilla,"ENCHANTING_TABLE", Material.BOOKSHELF, "&b&lEnchanter",
                List.of("&7Buy a random mystery enchant", "&7book with your experience levels."),
                "&eClick to open the shop."), open("enchanter"));
        tile(holder, 12, MenuIcons.tile(vanilla,"BREWING_STAND", Material.GLASS_BOTTLE, "&a&lAlchemist",
                List.of("&7Fuse two matching enchant books", "&7into one of the next level."),
                "&eClick to open the bench."), open("alchemist"));
        tile(holder, 14, MenuIcons.tile(vanilla,"GRINDSTONE", Material.ANVIL, "&6&lTinkerer",
                List.of("&7Break an enchant book down", "&7for an experience refund."),
                "&eClick to open the bench."), open("tinkerer"));
        tile(holder, 16, MenuIcons.tile(vanilla,"END_CRYSTAL", Material.NETHER_STAR, "&5&lGodly Transmog",
                List.of("&7Re-order the enchant lore on", "&7your held item, line by line."),
                "&eClick to open the editor."), open("transmog"));

        // Row 3 — the read-only catalogues (the things a player SEES).
        tile(holder, 29, MenuIcons.tile(vanilla,"ENCHANTED_BOOK", Material.BOOK, "&3&lEnchant Catalogue",
                List.of("&7Browse every custom enchant,", "&7grouped by rarity tier."),
                "&eClick to browse."), open("enchants"));
        tile(holder, 31, MenuIcons.tile(vanilla,"DIAMOND_CHESTPLATE", Material.IRON_CHESTPLATE, "&3&lArmour Sets",
                List.of("&7Browse every armour set and", "&7preview its pieces and bonus."),
                "&eClick to browse."), open("sets"));
        tile(holder, 33, MenuIcons.tile(vanilla,"AMETHYST_SHARD", Material.QUARTZ, "&3&lCrystals",
                List.of("&7Browse every socketable crystal", "&7and what it grants your gear."),
                "&eClick to browse."), open("crystals"));
        // Row 2 centre — the item catalogues (pets ADR-0052, masks ADR-0053, reforges ADR-0070), a centred triple.
        tile(holder, 20, MenuIcons.tile(vanilla,"PLAYER_HEAD", Material.GOLDEN_CARROT, "&6&lPets",
                List.of("&7Browse every pet — hold one in", "&7your hotbar and level it up."),
                "&eClick to browse."), open("pets"));
        tile(holder, 22, MenuIcons.tile(vanilla,"PLAYER_HEAD", Material.PUMPKIN, "&8&lMasks",
                List.of("&7Browse every mask — drag one onto", "&7a helmet to wear its likeness."),
                "&eClick to browse."), open("masks"));
        tile(holder, 24, MenuIcons.tile(vanilla,"ANVIL", Material.ANVIL, "&6&lWeapon Reforges",
                List.of("&7Browse every weapon reforge — socket", "&7one and shift + right-click to fire it."),
                "&eClick to browse."), open("reforges"));
    }

    /** A tile action that drills into the sibling menu registered under {@code name}. */
    private ClickAction open(String name) {
        return click -> registry.get(name).ifPresent(menu -> openMenu(click, menu));
    }
}
