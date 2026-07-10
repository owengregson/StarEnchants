package feature.menu;

import feature.carrier.CarrierService;
import platform.lang.Messages;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;
import platform.item.Inventories;

/**
 * The Alchemist combine bench (§K, ADR-0030): fuse two identical enchant books (same enchant + level, below
 * max) into one of the next level. A three-row exchange window in the Cosmic Enchants likeness — the two
 * ingredient slots flank the info pane on the top row, the centre cell live-previews the exact book the
 * exchange would forge, and the glowing exchange button sits bottom-centre. A Cosmic Enchants-style
 * magic-dust rarity-tinkering is excluded (ADR-0019), and the exchange itself is free (no XP charge). The
 * slot constants are public so the live GuiSuite reads positions from the menu rather than hard-coding them.
 */
public final class AlchemistMenu extends FormMenu {

    public static final int LEFT_INPUT = 3;
    public static final int RIGHT_INPUT = 5;
    public static final int PREVIEW = 13;
    public static final int COMBINE_BUTTON = 22;

    private final CarrierService carriers;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public AlchemistMenu(CarrierService carriers, Capabilities caps) {
        this(carriers, caps, Messages.defaults());
    }

    public AlchemistMenu(CarrierService carriers, Capabilities caps, Messages messages) {
        this(carriers, caps, messages, compile.load.MenusConfig::empty, item.mint.VanillaEnchants.NONE);
    }

    public AlchemistMenu(CarrierService carriers, Capabilities caps, Messages messages,
                         java.util.function.Supplier<compile.load.MenusConfig> menus,
                         item.mint.VanillaEnchants vanilla) {
        super("alchemist", MenuLayout.form(3, "&a&lAlchemist &8• &7Exchange"), caps, menus, vanilla);
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Set<Integer> inputSlots() {
        return Set.of(LEFT_INPUT, RIGHT_INPUT);
    }

    @Override
    protected String infoTitle() {
        return "&b&lTHE COSMIC ALCHEMIST";
    }

    @Override
    protected List<String> infoLore() {
        return List.of("&dThe Alchemist will exchange...", "",
                "&b* &f2x Enchantment Books", "&7(of the same enchant and level)",
                "&d= &71x Enchantment Book", "&7(of one level higher)");
    }

    @Override
    protected void layoutControls(MenuHolder holder) {
        holder.set(COMBINE_BUTTON, actionButton("WHITE_STAINED_GLASS_PANE", "&b&lCLICK TO EXCHANGE",
                List.of("&7Click here to confirm this", "&7item exchange and receive your",
                        "&7new item shown above!")), this::combine);
        refreshPreview(holder);
    }

    @Override
    public void onInputChanged(Player player, MenuHolder holder) {
        refreshPreview(holder);
    }

    /**
     * Re-render the centre preview cell from the staged inputs: a valid pair shows the EXACT book the exchange
     * would mint (locked, display-only); anything else shows the placeholder pane. Recomputed on every input
     * change and after every exchange, so the preview can never go stale against the live inventory.
     */
    private void refreshPreview(MenuHolder holder) {
        ItemStack a = holder.getInventory().getItem(LEFT_INPUT);
        ItemStack b = holder.getInventory().getItem(RIGHT_INPUT);
        Optional<ItemStack> combined = singles(a, b) ? carriers.combineBooks(a, b) : Optional.empty();
        if (combined.isPresent()) {
            holder.set(PREVIEW, MenuIcons.receiveTile(vanilla, combined.get(), "&eThis is what you'll receive."),
                    null);
            return;
        }
        holder.set(PREVIEW, MenuIcons.tile(vanilla, "WHITE_STAINED_GLASS_PANE", org.bukkit.Material.PAPER,
                "&e&lITEM PREVIEW",
                List.of("&7A preview of the item you will", "&7receive from the Cosmic Alchemist",
                        "&7will be displayed here."), ""), null);
    }

    private static boolean singles(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getAmount() == 1 && b.getAmount() == 1;
    }

    private void combine(MenuClick click) {
        Player player = click.player();
        MenuHolder holder = click.holder();
        ItemStack a = holder.getInventory().getItem(LEFT_INPUT);
        ItemStack b = holder.getInventory().getItem(RIGHT_INPUT);
        if (!singles(a, b)) {
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
        refreshPreview(holder); // the inputs are gone — drop the stale preview back to the placeholder
        Inventories.giveOrDrop(player, combined.get());
        messages.send(player, "menu.alchemist.combined");
    }
}
