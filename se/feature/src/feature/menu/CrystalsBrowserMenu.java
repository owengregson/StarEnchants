package feature.menu;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.MenusConfig;
import feature.crystal.CrystalService;
import item.lang.Messages;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;
import platform.item.ItemGroups;

/**
 * The crystals/modifiers browser (§K, ADR-0030; a Cosmic Enchants-style plugin called crystals "modifiers").
 * Browsing is open to everyone; an operator ({@code starenchants.admin}) clicks a crystal to MINT one to
 * themselves to socket, checked on the clicker. Applying / extracting / merging a crystal onto gear stays a
 * drag gesture ({@code CrystalListener}, §E) — only the catalogue + operator mint live here.
 */
public final class CrystalsBrowserMenu extends PagedMenu<CrystalDef> {

    private final ContentHolder content;
    private final CrystalService crystals;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public CrystalsBrowserMenu(ContentHolder content, CrystalService crystals, Capabilities caps) {
        this(content, crystals, caps, Messages.defaults(), MenusConfig::empty);
    }

    public CrystalsBrowserMenu(ContentHolder content, CrystalService crystals, Capabilities caps,
                               Messages messages, Supplier<MenusConfig> menus) {
        super("crystals", MenuLayout.paged("&3&lCrystals"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
        this.crystals = Objects.requireNonNull(crystals, "crystals");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected List<CrystalDef> items(MenuHolder holder) {
        return content.library().crystals();
    }

    @Override
    protected ItemStack icon(MenuHolder holder, CrystalDef def) {
        List<String> lore = new ArrayList<>(MenuText.describe(def.description(), "&7"));
        lore.add("&8tier: " + tierColor(def.tier()) + tierLabel(def.tier()));
        lore.add("&8applies to: &7" + ItemGroups.kindsLabel(def.appliesTo()));
        lore.add("&7Drag a minted crystal onto gear to apply.");
        lore.add("&eClick to mint one. &8(operator)");
        // The crystal keeps its own authored name colour (its tier shows on the "tier:" line above), unlike an
        // enchant whose name is tier-coloured.
        return ItemFactory.build(material("AMETHYST_SHARD", "PRISMARINE_CRYSTALS", "NETHER_STAR", "QUARTZ"),
                def.display(), lore);
    }

    @Override
    protected void onSelect(MenuClick click, CrystalDef def) {
        Player player = click.player();
        if (!player.hasPermission("starenchants.admin")) {
            messages.send(player, "menu.crystals.preview-only");
            return;
        }
        MenuItems.giveOrDrop(player, crystals.mint(List.of(def.key())));
        messages.send(player, "menu.crystals.minted", "CRYSTAL", def.display());
    }

    @Override
    protected String infoTitle(MenuHolder holder) {
        return "&3&lCrystals";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        return List.of("&7Every socketable crystal.", "&8Operators click to mint one.");
    }

    private String tierColor(String tier) {
        return MenuText.tierColor(content.library().tiers(), tier);
    }

    private static String tierLabel(String tier) {
        return tier == null || tier.isBlank() ? "—" : tier;
    }
}
