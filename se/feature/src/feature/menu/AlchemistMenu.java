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
 * The Alchemist combine bench (docs/v3-directives.md §K): place two <strong>identical</strong> enchant books
 * (same enchant, same level, below that enchant's max) in the two input slots and click Combine to fuse them
 * into one book of the next level. A {@link FormMenu} — the inputs are real item slots; an unconsumed input
 * is returned on close.
 *
 * <p>In-scope economy only: a mint of an existing item type via {@link CarrierService#combineBooks}. EE's
 * magic-dust rarity-tinkering (combine two dusts → a next-rarity dust) is NOT built — ADR-0019 excludes it
 * (no data model).
 */
public final class AlchemistMenu extends FormMenu {

    private static final int LEFT_INPUT = 11;
    private static final int RIGHT_INPUT = 15;
    private static final int COMBINE_BUTTON = 13;

    private final CarrierService carriers;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public AlchemistMenu(CarrierService carriers, Capabilities caps) {
        this(carriers, caps, Messages.defaults());
    }

    public AlchemistMenu(CarrierService carriers, Capabilities caps, Messages messages) {
        this(carriers, caps, messages, compile.load.MenusConfig::empty);
    }

    public AlchemistMenu(CarrierService carriers, Capabilities caps, Messages messages,
                         java.util.function.Supplier<compile.load.MenusConfig> menus) {
        super("alchemist", MenuLayout.form(3, "&3Alchemist"), caps, menus);
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Set<Integer> inputSlots() {
        return Set.of(LEFT_INPUT, RIGHT_INPUT);
    }

    @Override
    protected void layoutControls(MenuHolder holder) {
        holder.set(COMBINE_BUTTON, button("ANVIL", "&aCombine",
                List.of("&7Place two identical enchant books", "&7(same enchant + level) to fuse them",
                        "&7into one book of the next level.")), this::combine);
    }

    private void combine(MenuClick click) {
        Player player = click.player();
        MenuHolder holder = click.holder();
        ItemStack a = holder.getInventory().getItem(LEFT_INPUT);
        ItemStack b = holder.getInventory().getItem(RIGHT_INPUT);
        if (a == null || b == null || a.getAmount() != 1 || b.getAmount() != 1) {
            messages.send(player, "menu.alchemist.bad-input");
            return;
        }
        Optional<ItemStack> combined = carriers.combineBooks(a, b);
        if (combined.isEmpty()) {
            messages.send(player, "menu.alchemist.cant-combine");
            return;
        }
        holder.getInventory().setItem(LEFT_INPUT, null);   // consume both inputs
        holder.getInventory().setItem(RIGHT_INPUT, null);
        MenuItems.giveOrDrop(player, combined.get());       // give the upgraded book
        messages.send(player, "menu.alchemist.combined");
    }
}
