package feature.menu;

import compile.load.ContentHolder;
import compile.load.MasterConfig;
import compile.load.MenusConfig;
import compile.load.PetDef;
import feature.pet.PetService;
import item.mint.VanillaEnchants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;
import platform.item.Inventories;
import platform.lang.Messages;

/**
 * The pets browser (ADR-0052, the ADR-0030 chrome): every pet rendered as its ACTUAL textured head + likeness.
 * Browsing is open to everyone; an operator ({@code starenchants.admin}) clicks a pet to drill into a LEVEL
 * picker (every level 1..max, paged) and clicks a level to mint the pet at it — the AdminBrowserMenu drill,
 * because a flat mint tile cannot carry a level. Feeding/leveling stays on the item itself (the Pet Food
 * gesture); only the catalogue + operator mint live here.
 */
public final class PetsBrowserMenu extends PagedMenu<PetsBrowserMenu.Row> {

    /** One row: a pet in the list view, or one mintable level in the drill ({@code def} set on both). */
    public record Row(PetDef def, int level) {
    }

    private static final String VIEW_LEVELS = "levels";

    private final ContentHolder content;
    private final PetService pets;
    private final Supplier<MasterConfig.PetsSection> config;
    private final Messages messages;

    public PetsBrowserMenu(ContentHolder content, PetService pets, Supplier<MasterConfig.PetsSection> config,
                           Capabilities caps, Messages messages, Supplier<MenusConfig> menus,
                           VanillaEnchants vanilla) {
        super("pets", MenuLayout.paged("&6&lPets"), caps, menus, vanilla);
        this.content = Objects.requireNonNull(content, "content");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.config = Objects.requireNonNull(config, "config");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected List<Row> items(MenuHolder holder) {
        if (VIEW_LEVELS.equals(holder.view()) && holder.payload() instanceof PetDef def) {
            List<Row> levels = new ArrayList<>();
            for (int level = 1; level <= config.get().maxLevel(); level++) {
                levels.add(new Row(def, level));
            }
            return levels;
        }
        List<Row> out = new ArrayList<>();
        for (PetDef def : content.library().pets()) {
            out.add(new Row(def, 1));
        }
        return out;
    }

    @Override
    protected ItemStack icon(MenuHolder holder, Row row) {
        // The icon IS the minted item (the receive-tile rule): the textured head with the real likeness —
        // the level picker's rows differ only by the {LEVEL} the likeness renders.
        ItemStack head = pets.mint(row.def().key(), row.level());
        return head != null ? head
                : item.mint.ItemFactory.build(material("PLAYER_HEAD", "PAPER"), "&c" + row.def().display(),
                        List.of("&7This pet's content failed to mint."));
    }

    @Override
    protected void onSelect(MenuClick click, Row row) {
        Player player = click.player();
        if (!player.hasPermission("starenchants.admin")) {
            messages.send(player, "menu.pets.preview-only");
            return;
        }
        MenuHolder holder = click.holder();
        if (VIEW_LEVELS.equals(holder.view())) {
            Inventories.giveOrDrop(player, pets.mint(row.def().key(), row.level()));
            messages.send(player, "menu.pets.minted", "PET", row.def().display(),
                    "LEVEL", Integer.toString(row.level()));
            return;
        }
        holder.setView(VIEW_LEVELS);
        holder.setPayload(row.def());
        holder.setPage(0);
        reopen(click);
    }

    @Override
    protected boolean showBack(MenuHolder holder) {
        return holder.view() != null || super.showBack(holder);
    }

    @Override
    protected void onBack(MenuClick click) {
        MenuHolder holder = click.holder();
        if (VIEW_LEVELS.equals(holder.view())) {
            holder.setView(null);
            holder.setPayload(null);
            holder.setPage(0);
            reopen(click);
            return;
        }
        super.onBack(click);
    }

    @Override
    protected String titleFor(MenuHolder holder) {
        if (VIEW_LEVELS.equals(holder.view()) && holder.payload() instanceof PetDef def) {
            return "&6&lPets &8» &f" + def.display();
        }
        return super.titleFor(holder);
    }

    @Override
    protected String infoTitle(MenuHolder holder) {
        return "&6&lPets";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        if (VIEW_LEVELS.equals(holder.view())) {
            return List.of("&7Pick the level to mint at.", "&8Back returns to the pet list.");
        }
        return List.of("&7Hold a pet in your hotbar; right-click", "&7ACTIVE pets to fire their ability.",
                "&7Feed Pet Food to level them up.", "", "&eOperators click a pet to mint it.");
    }
}
