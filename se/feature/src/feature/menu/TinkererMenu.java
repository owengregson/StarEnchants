package feature.menu;

import feature.carrier.CarrierService;
import platform.lang.Messages;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The Tinkerer salvage bench (docs/v3-directives.md §K, ADR-0030): trade enchant books for an EXP-level
 * refund. A six-row trade window in the Cosmic Enchants likeness — the left four columns are the player's
 * side (stage any number of books), a glass divider splits the window, and each staged book mirrors a live
 * experience-bottle preview of its refund range on the right. The red ACCEPT panes in the top corners salvage
 * everything staged at once. A Cosmic Enchants-style salvage-to-XP-bottle item and book↔dust conversion are
 * NOT built (no data model — ADR-0019); this is a direct EXP refund. The slot constants are public so the
 * live GuiSuite reads positions from the menu.
 */
public final class TinkererMenu extends FormMenu {

    /** Left/right ACCEPT panes — either salvages every staged book. */
    public static final int SALVAGE_BUTTON = 0;
    public static final int SALVAGE_BUTTON_RIGHT = 8;
    /** The first input cell (row 1, column 0) — the canonical staging slot the live suites use. */
    public static final int INPUT = 9;
    /** A staged input at slot {@code s} mirrors its refund preview at {@code s + PREVIEW_OFFSET}. */
    public static final int PREVIEW_OFFSET = 5;

    /** The player's side: rows 1–4 × columns 0–3 (row 5 is the nav row, row 0 the ACCEPT row). */
    private static final Set<Integer> INPUTS = inputGrid();
    private static final int DIVIDER_COLUMN = 4;
    private static final int ROWS = 6;

    private final CarrierService carriers;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public TinkererMenu(CarrierService carriers, Capabilities caps) {
        this(carriers, caps, Messages.defaults());
    }

    public TinkererMenu(CarrierService carriers, Capabilities caps, Messages messages) {
        this(carriers, caps, messages, compile.load.MenusConfig::empty, item.mint.VanillaEnchants.NONE);
    }

    public TinkererMenu(CarrierService carriers, Capabilities caps, Messages messages,
                        java.util.function.Supplier<compile.load.MenusConfig> menus,
                        item.mint.VanillaEnchants vanilla) {
        super("tinkerer", MenuLayout.form(ROWS, "You                 Tinkerer"), caps, menus, vanilla);
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    private static Set<Integer> inputGrid() {
        Set<Integer> slots = new LinkedHashSet<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 0; col < DIVIDER_COLUMN; col++) {
                slots.add(row * 9 + col);
            }
        }
        return Set.copyOf(slots);
    }

    @Override
    public Set<Integer> inputSlots() {
        return INPUTS;
    }

    @Override
    protected String infoTitle() {
        return "&6&lTinkerer";
    }

    @Override
    protected List<String> infoLore() {
        return List.of("&7Place enchant books on the left;", "&7each shows its refund on the right.",
                "&7Click &c&lACCEPT &7to trade them all.");
    }

    @Override
    protected void layoutControls(MenuHolder holder) {
        ItemStack accept = actionButton("RED_STAINED_GLASS_PANE", "&eClick to ACCEPT trade",
                List.of("&7Salvages every staged book for", "&7an experience-level refund."));
        holder.set(SALVAGE_BUTTON, accept, this::salvageAll);
        holder.set(SALVAGE_BUTTON_RIGHT, accept, this::salvageAll);
        ItemStack divider = MenuIcons.pane("GLASS_PANE");
        for (int row = 1; row < ROWS; row++) { // row 0 keeps the info pane at the theme's slot 4
            int slot = row * 9 + DIVIDER_COLUMN;
            if (slot != layout().backSlot() && slot != layout().closeSlot()) {
                holder.set(slot, divider, null);
            }
        }
        refreshPreviews(holder);
    }

    @Override
    public void onInputChanged(Player player, MenuHolder holder) {
        refreshPreviews(holder);
    }

    /**
     * Mirror every staged input into its preview cell on the tinkerer's side: a salvageable book shows an
     * experience bottle with its honest refund range (the roll happens at ACCEPT — see
     * {@link CarrierService#salvageLevels}), anything else a "not salvageable" pane, an empty cell the
     * backdrop filler. Preview cells are locked, display-only slots.
     */
    private void refreshPreviews(MenuHolder holder) {
        ItemStack filler = MenuIcons.pane(layout().fillerMaterial());
        for (int slot : INPUTS) {
            ItemStack staged = holder.getInventory().getItem(slot);
            holder.set(slot + PREVIEW_OFFSET, previewFor(staged, filler), null);
        }
    }

    private ItemStack previewFor(ItemStack staged, ItemStack filler) {
        if (staged == null || staged.getType() == Material.AIR) {
            return filler;
        }
        Optional<Integer> cap = staged.getAmount() == 1 ? carriers.salvageCapLevels(staged) : Optional.empty();
        if (cap.isEmpty()) {
            return MenuIcons.tile(vanilla, "RED_STAINED_GLASS_PANE", Material.PAPER, "&c&lNot salvageable",
                    List.of("&7Only single enchant books", "&7can be traded here."), "");
        }
        Material bottle = item.mint.ItemFactory.material("EXPERIENCE_BOTTLE",
                item.mint.ItemFactory.material("EXP_BOTTLE", Material.PAPER));
        String range = cap.get() <= 1 ? "1" : "1&7-&f" + cap.get();
        return MenuIcons.tile(vanilla, bottle, "&a&lExperience Bottle",
                List.of("&dValue &f" + range + " XP Level" + (cap.get() == 1 ? "" : "s"),
                        "&dTinkerer &fCosmic"), "");
    }

    /** Salvage every staged book (each rolls its own refund), leaving anything unsalvageable staged. */
    private void salvageAll(MenuClick click) {
        Player player = click.player();
        MenuHolder holder = click.holder();
        int total = 0;
        int salvaged = 0;
        for (int slot : INPUTS) {
            ItemStack staged = holder.getInventory().getItem(slot);
            if (staged == null || staged.getAmount() != 1) {
                continue;
            }
            Optional<Integer> levels = carriers.salvageLevels(staged);
            if (levels.isEmpty()) {
                continue;
            }
            holder.getInventory().setItem(slot, null);
            total += levels.get();
            salvaged++;
        }
        if (salvaged == 0) {
            messages.send(player, "menu.tinkerer.bad-input");
            return;
        }
        refreshPreviews(holder);
        player.giveExpLevels(total);                   // click runs on the player's region thread — in-region, Folia-safe
        messages.send(player, "menu.tinkerer.salvaged", "LEVELS", total, "S", total == 1 ? "" : "s");
    }
}
