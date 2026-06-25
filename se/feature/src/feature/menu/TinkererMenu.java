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
 * The Tinkerer salvage bench (docs/v3-directives.md §K): place an enchant book in the input slot and click
 * Salvage to break it down for an EXP-level refund (the book's level, at least one). A {@link FormMenu};
 * an unconsumed input is returned on close.
 *
 * <p>In-scope economy only — a salvage to EXP via {@link CarrierService#salvageLevels}. A Cosmic Enchants-style salvage-to-XP-
 * bottle item and book↔dust conversion are NOT built (no data model — ADR-0019); this is the modernized
 * StarEnchants salvage (a direct EXP refund, no new carrier item).
 */
public final class TinkererMenu extends FormMenu {

    private static final int INPUT = 13;
    private static final int SALVAGE_BUTTON = 15;

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
        super("tinkerer", MenuLayout.form(3, "&3Tinkerer"), caps, menus);
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Set<Integer> inputSlots() {
        return Set.of(INPUT);
    }

    @Override
    protected void layoutControls(MenuHolder holder) {
        holder.set(SALVAGE_BUTTON, button("FURNACE", "&aSalvage",
                List.of("&7Place an enchant book to break it", "&7down for an experience refund.")),
                this::salvage);
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
