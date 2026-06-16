package feature.menu;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.sched.Scheduling;

/**
 * A paginated enchant-application menu (docs/architecture.md §7): each enchant in the published catalog
 * is a clickable icon; clicking one applies it (level 1) to the viewer's held item through the
 * {@link ItemEnchanter} — the visual equivalent of {@code /se enchant}. Stateless and shareable; the
 * per-open page lives on the {@link MenuHolder}, and all inventory work runs on the viewer's region
 * thread (Folia-correct — the menu opens from the command thread, so it hops; a click event already
 * fires on the player's thread, so the apply runs inline).
 *
 * <p>The book/scroll/dust application <em>economy</em> (success rates, protect/destroy) is a later
 * feature that needs the identity-item data layer; this menu is the direct-apply surface.
 */
public final class EnchantMenu {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;          // 54
    private static final int CONTENT_SLOTS = SIZE - 9; // top 5 rows (45) are enchant icons
    static final int PREV_SLOT = SIZE - 9;             // 45 — bottom-left
    static final int NEXT_SLOT = SIZE - 1;             // 53 — bottom-right
    private static final String TITLE = "StarEnchants";

    private final ContentHolder content;
    private final ItemEnchanter enchanter;
    private final Consumer<Player> refreshWorn;

    public EnchantMenu(ContentHolder content, ItemEnchanter enchanter, Consumer<Player> refreshWorn) {
        this.content = Objects.requireNonNull(content, "content");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.refreshWorn = Objects.requireNonNull(refreshWorn, "refreshWorn");
    }

    /** Open page 0 for {@code player} (hops to the player's region thread — safe from the command thread). */
    public void open(Player player) {
        open(player, 0);
    }

    /** Open the given page for {@code player} on the player's own region thread. */
    public void open(Player player, int page) {
        Scheduling.onEntity(player, () -> player.openInventory(build(page)));
    }

    /** Build the inventory for {@code page} (pure — no player needed; safe to call off the main path in tests). */
    @SuppressWarnings("deprecation") // createInventory(holder, size, String title): deprecated-not-removed 1.17.1→26.1.x.
    public Inventory build(int page) {
        List<EnchantDef> catalog = content.library().catalog();
        int pages = Math.max(1, (catalog.size() + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        int clamped = Math.floorMod(page, pages);
        MenuHolder holder = new MenuHolder(clamped);
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE + "  (" + (clamped + 1) + "/" + pages + ")");
        holder.setInventory(inv);

        int start = clamped * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < catalog.size(); i++) {
            inv.setItem(i, icon(catalog.get(start + i)));
        }
        if (clamped > 0) {
            inv.setItem(PREV_SLOT, navIcon("§e« Previous"));
        }
        if (clamped < pages - 1) {
            inv.setItem(NEXT_SLOT, navIcon("§eNext »"));
        }
        return inv;
    }

    /**
     * Handle a click in slot {@code rawSlot} of a menu opened at {@code holder}'s page, for {@code player}.
     * Runs on the player's region thread (the click event fires there). A content-slot click applies the
     * enchant to the held item; a nav slot re-opens the adjacent page.
     */
    void handleClick(Player player, MenuHolder holder, int rawSlot) {
        List<EnchantDef> catalog = content.library().catalog();
        Click click = resolveClick(holder.page(), rawSlot, catalog.size());
        switch (click.kind()) {
            case OPEN_PAGE -> open(player, click.value());
            case APPLY -> applyEnchant(player, catalog.get(click.value()));
            case NONE -> { } // an empty/non-interactive slot — the listener already cancelled the click
        }
    }

    private void applyEnchant(Player player, EnchantDef def) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ApplyResult result = enchanter.applyEnchant(held, def.key(), 1); // level 1; a level picker is a follow-up
        if (result.ok()) {
            player.getInventory().setItemInMainHand(held); // write the mutated copy back
            refreshWorn.accept(player);                    // re-resolve the cached WornState (no equip event fires)
        }
        player.sendMessage(result.message());
    }

    /** What a click resolves to — decided purely (no Bukkit) so the pagination/bounds logic is unit-testable. */
    enum ClickKind { OPEN_PAGE, APPLY, NONE }

    /** A click outcome: {@code OPEN_PAGE}→target page, {@code APPLY}→catalog index, {@code NONE}→ignore. */
    record Click(ClickKind kind, int value) {
    }

    /**
     * Resolve a raw-slot click against the page + catalog size. Nav slots resolve to {@code OPEN_PAGE}
     * ONLY when the corresponding arrow was rendered (prev when {@code page>0}, next when more pages
     * remain) — a click on an empty corner slot is {@code NONE}, never a wrap. A content slot resolves to
     * {@code APPLY} only when it maps to a real catalog entry (trailing slots on the last page are {@code NONE}).
     */
    static Click resolveClick(int page, int rawSlot, int catalogSize) {
        int pages = Math.max(1, (catalogSize + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        if (rawSlot == PREV_SLOT) {
            return page > 0 ? new Click(ClickKind.OPEN_PAGE, page - 1) : new Click(ClickKind.NONE, 0);
        }
        if (rawSlot == NEXT_SLOT) {
            return page < pages - 1 ? new Click(ClickKind.OPEN_PAGE, page + 1) : new Click(ClickKind.NONE, 0);
        }
        if (rawSlot < 0 || rawSlot >= CONTENT_SLOTS) {
            return new Click(ClickKind.NONE, 0); // a non-icon slot in the bottom row
        }
        int index = page * CONTENT_SLOTS + rawSlot;
        return index < catalogSize ? new Click(ClickKind.APPLY, index) : new Click(ClickKind.NONE, 0);
    }

    private static ItemStack icon(EnchantDef def) {
        ItemStack item = new ItemStack(iconMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(def.display());
            List<String> lore = new ArrayList<>();
            if (!def.description().isBlank()) {
                lore.add("§7" + def.description());
            }
            lore.add("§8applies to: §7" + String.join(", ", def.appliesTo()));
            lore.add("§8max level: §7" + def.maxLevel());
            lore.add("§eClick to apply to your held item.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack navIcon(String name) {
        ItemStack item = new ItemStack(firstMaterial("ARROW", "FEATHER", "PAPER"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The icon Material, resolved by NAME (cross-version-safe; never a hard constant). */
    private static Material iconMaterial() {
        return firstMaterial("ENCHANTED_BOOK", "BOOK", "PAPER");
    }

    /** The first of {@code names} that exists on this server (resolved by name), or STONE as a last resort. */
    private static Material firstMaterial(String... names) {
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.STONE; // present on every version
    }
}
