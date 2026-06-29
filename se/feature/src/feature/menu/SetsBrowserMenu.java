package feature.menu;

import compile.load.ContentHolder;
import compile.load.MenusConfig;
import compile.load.SetDef;
import feature.apply.ItemEnchanter;
import item.lang.Messages;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The armour-set browser (docs/v3-directives.md §K, ADR-0030): a two-level drill — every set, then the pieces
 * of a chosen set (each armour member + the optional weapon). Browsing is open to everyone (informational),
 * but a piece click MINTS that piece only for {@code starenchants.admin}, checked on the clicker — a
 * non-operator sees the same rich preview without the grant. Drill state lives on the {@link MenuHolder}:
 * {@code view} is {@code null} (sets) or {@code "pieces"}, and {@code payload} is the chosen {@link SetDef}.
 */
public final class SetsBrowserMenu extends PagedMenu<SetsBrowserMenu.Row> {

    /** A row is either a set bucket (index view) or one piece of the chosen set (pieces view). */
    record Row(SetDef set, Piece piece) {
        boolean isSet() {
            return piece == null;
        }
    }

    /** One mintable piece of a set: its {@code mintSetPiece} token, material, display name, and slot label. */
    record Piece(String token, String material, String name, String slotLabel, boolean weapon) {
    }

    static final String VIEW_PIECES = "pieces";

    private final ContentHolder content;
    private final ItemEnchanter enchanter;
    private final Messages messages;

    /** Default-messages form (tests/fixtures). */
    public SetsBrowserMenu(ContentHolder content, ItemEnchanter enchanter, Capabilities caps) {
        this(content, enchanter, caps, Messages.defaults(), MenusConfig::empty);
    }

    public SetsBrowserMenu(ContentHolder content, ItemEnchanter enchanter, Capabilities caps, Messages messages,
                           Supplier<MenusConfig> menus) {
        super("sets", MenuLayout.paged("&3&lArmour Sets"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected List<Row> items(MenuHolder holder) {
        if (VIEW_PIECES.equals(holder.view()) && holder.payload() instanceof SetDef def) {
            return pieces(def);
        }
        List<Row> rows = new ArrayList<>();
        for (SetDef def : content.library().sets()) {
            rows.add(new Row(def, null));
        }
        return rows;
    }

    private static List<Row> pieces(SetDef def) {
        List<Row> rows = new ArrayList<>();
        for (SetDef.Member member : def.armorMembers()) {
            rows.add(new Row(def, new Piece(member.slot(), member.material(),
                    member.name() != null ? member.name() : def.display(), capitalize(member.slot()), false)));
        }
        if (def.hasWeapon()) {
            SetDef.Member weapon = def.weapon();
            rows.add(new Row(def, new Piece("weapon", weapon.material(),
                    weapon.name() != null ? weapon.name() : def.display(), "Weapon", true)));
        }
        return rows;
    }

    @Override
    protected ItemStack icon(MenuHolder holder, Row row) {
        return row.isSet() ? setIcon(row.set()) : pieceIcon(row.piece());
    }

    @Override
    protected void onSelect(MenuClick click, Row row) {
        MenuHolder holder = click.holder();
        if (row.isSet()) {
            holder.setView(VIEW_PIECES);
            holder.setPayload(row.set());
            holder.setSelection(row.set().key());
            holder.setPage(0);
            reopen(click);
            return;
        }
        Player player = click.player();
        if (!player.hasPermission("starenchants.admin")) {
            messages.send(player, "menu.sets.preview-only");
            return;
        }
        SetDef def = holder.payload() instanceof SetDef d ? d : null;
        if (def == null) {
            return;
        }
        enchanter.mintSetPiece(def.key(), row.piece().token()).ifPresentOrElse(piece -> {
            MenuItems.giveOrDrop(player, piece);
            messages.send(player, "menu.sets.minted", "PIECE", row.piece().slotLabel(), "SET", def.display());
        }, () -> messages.send(player, "menu.sets.mint-failed", "PIECE", row.piece().slotLabel()));
    }

    @Override
    protected String titleFor(MenuHolder holder) {
        if (VIEW_PIECES.equals(holder.view()) && holder.payload() instanceof SetDef def) {
            return def.display() + " &8• &7Pieces";
        }
        return layout().titleTemplate();
    }

    @Override
    protected boolean showBack(MenuHolder holder) {
        return holder.view() != null;
    }

    @Override
    protected void onBack(MenuClick click) {
        MenuHolder holder = click.holder();
        holder.setView(null);
        holder.setPayload(null);
        holder.setSelection(null);
        holder.setPage(0);
        reopen(click);
    }

    @Override
    protected String infoTitle(MenuHolder holder) {
        return VIEW_PIECES.equals(holder.view()) ? "&3&lSet Pieces" : "&3&lArmour Sets";
    }

    @Override
    protected List<String> infoLore(MenuHolder holder) {
        return VIEW_PIECES.equals(holder.view())
                ? List.of("&7Each piece of this set.",
                        "&8Operators click a piece to mint it.")
                : List.of("&7Every armour set on the server.", "&eClick a set to see its pieces.");
    }

    private ItemStack setIcon(SetDef def) {
        List<String> lore = new ArrayList<>(MenuText.describe(def.description(), "&7"));
        lore.add("&8completes at: &7" + def.armorComplete() + " armour piece" + (def.armorComplete() == 1 ? "" : "s"));
        lore.add("&8armour: &7" + String.join(", ", def.appliesTo()));
        if (def.hasWeapon()) {
            lore.add("&8weapon: &7" + (def.weapon().name() != null ? def.weapon().name() : def.weapon().material()));
        }
        lore.add("&eClick to view the pieces.");
        return MenuIcons.hideDetails(ItemFactory.build(
                material("DIAMOND_CHESTPLATE", "IRON_CHESTPLATE", "LEATHER_CHESTPLATE"), def.display(), lore));
    }

    private ItemStack pieceIcon(Piece piece) {
        List<String> lore = new ArrayList<>();
        lore.add("&8slot: &7" + piece.slotLabel());
        lore.add("&8material: &7" + piece.material());
        lore.add("&eClick to mint this piece. &8(operator)");
        Material fallback = piece.weapon() ? Material.IRON_SWORD : Material.LEATHER_CHESTPLATE;
        return MenuIcons.hideDetails(ItemFactory.build(piece.material(), fallback, piece.name(), lore));
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
