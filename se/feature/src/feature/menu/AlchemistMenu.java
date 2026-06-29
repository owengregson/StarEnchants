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
 * The Alchemist combine bench (§K, ADR-0030): fuse two identical enchant books (same enchant + level, below
 * max) into one of the next level. A five-row, hand-laid bench — the two ingredient slots sit under labelled
 * guides flanking a glowing Combine button, on a themed backdrop with an info pane and close button. A Cosmic
 * Enchants-style magic-dust rarity-tinkering is excluded (ADR-0019). The slot constants are public so the live
 * GuiSuite reads positions from the menu rather than hard-coding them.
 */
public final class AlchemistMenu extends FormMenu {

    public static final int LEFT_INPUT = 20;
    public static final int RIGHT_INPUT = 24;
    public static final int COMBINE_BUTTON = 22;
    private static final int LEFT_LABEL = 11;
    private static final int RIGHT_LABEL = 15;

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
        super("alchemist", MenuLayout.form(5, "&a&lAlchemist &8• &7Combine Books"), caps, menus);
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Set<Integer> inputSlots() {
        return Set.of(LEFT_INPUT, RIGHT_INPUT);
    }

    @Override
    protected String infoTitle() {
        return "&a&lAlchemist";
    }

    @Override
    protected List<String> infoLore() {
        return List.of("&7Place two identical enchant books",
                "&7(same enchant &8+&7 same level, below max)", "&7to fuse them one level higher.");
    }

    @Override
    protected void layoutControls(MenuHolder holder) {
        holder.set(LEFT_LABEL, MenuIcons.tile("LIME_STAINED_GLASS_PANE", org.bukkit.Material.PAPER,
                "&e① First Book", List.of("&7Drop the first enchant book", "&7in the slot below."), ""), null);
        holder.set(RIGHT_LABEL, MenuIcons.tile("LIME_STAINED_GLASS_PANE", org.bukkit.Material.PAPER,
                "&e② Second Book", List.of("&7Drop the matching book", "&7in the slot below."), ""), null);
        holder.set(COMBINE_BUTTON, actionButton("ANVIL", "&a&l✦ Combine ✦",
                List.of("&7Fuse the two books above into", "&7one of the next level.", "",
                        "&eClick to combine.")), this::combine);
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
        holder.getInventory().setItem(LEFT_INPUT, null);
        holder.getInventory().setItem(RIGHT_INPUT, null);
        MenuItems.giveOrDrop(player, combined.get());
        messages.send(player, "menu.alchemist.combined");
    }
}
