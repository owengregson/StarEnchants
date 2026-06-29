package feature.menu;

import feature.carrier.CarrierService;
import item.lang.Messages;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The Tinkerer salvage bench (docs/v3-directives.md §K, ADR-0030): place an enchant book and click Salvage for
 * an EXP-level refund. A five-row, hand-laid bench — the input slot sits under a labelled guide beside a
 * glowing Salvage button, on a themed backdrop with an info pane and close button. A Cosmic Enchants-style
 * salvage-to-XP-bottle item and book↔dust conversion are NOT built (no data model — ADR-0019); this is a
 * direct EXP refund. The slot constants are public so the live GuiSuite reads positions from the menu.
 */
public final class TinkererMenu extends FormMenu {

    public static final int INPUT = 20;
    public static final int SALVAGE_BUTTON = 24;
    private static final int INPUT_LABEL = 11;

    private final CarrierService carriers;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public TinkererMenu(CarrierService carriers, Capabilities caps) {
        this(carriers, caps, Messages.defaults());
    }

    public TinkererMenu(CarrierService carriers, Capabilities caps, Messages messages) {
        this(carriers, caps, messages, compile.load.MenusConfig::empty);
    }

    public TinkererMenu(CarrierService carriers, Capabilities caps, Messages messages,
                        java.util.function.Supplier<compile.load.MenusConfig> menus) {
        super("tinkerer", MenuLayout.form(5, "&6&lTinkerer &8• &7Salvage Books"), caps, menus);
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Set<Integer> inputSlots() {
        return Set.of(INPUT);
    }

    @Override
    protected String infoTitle() {
        return "&6&lTinkerer";
    }

    @Override
    protected List<String> infoLore() {
        return List.of("&7Place an enchant book and salvage", "&7it for an experience-level refund.");
    }

    @Override
    protected void layoutControls(MenuHolder holder) {
        holder.set(INPUT_LABEL, MenuIcons.tile("ORANGE_STAINED_GLASS_PANE", org.bukkit.Material.PAPER,
                "&e① Enchant Book", List.of("&7Drop the book to salvage", "&7in the slot below."), ""), null);
        holder.set(SALVAGE_BUTTON, actionButton("FURNACE", "&6&l✦ Salvage ✦",
                List.of("&7Break the book down for an", "&7experience-level refund.", "",
                        "&eClick to salvage.")), this::salvage);
    }

    private void salvage(MenuClick click) {
        Player player = click.player();
        MenuHolder holder = click.holder();
        ItemStack book = holder.getInventory().getItem(INPUT);
        if (book == null || book.getAmount() != 1) {
            messages.send(player, "menu.tinkerer.bad-input");
            return;
        }
        Optional<Integer> levels = carriers.salvageLevels(book);
        if (levels.isEmpty()) {
            messages.send(player, "menu.tinkerer.not-book");
            return;
        }
        holder.getInventory().setItem(INPUT, null);
        player.giveExpLevels(levels.get());            // click runs on the player's region thread — in-region, Folia-safe
        messages.send(player, "menu.tinkerer.salvaged", "LEVELS", levels.get(), "S", levels.get() == 1 ? "" : "s");
    }
}
