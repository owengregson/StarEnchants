package feature.apply;

import feature.compat.Mats;
import feature.compat.MenuClicks;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;
import platform.lang.Messages;

/**
 * ADR-0041 — the ONE cursor-consumable apply gesture: a family item on the CURSOR clicked onto a target in
 * the player's OWN (bottom) inventory. Shared guard preamble + commit protocol; families parameterize via the
 * {@code claims*} hooks and {@link #apply}. Folia-correct: {@code InventoryClickEvent} fires on the clicking
 * player's own region thread, so cursor/inventory mutation and the overflow drop are in-thread. Bukkit
 * registers the inherited {@code @EventHandler} on each leaf (the {@code feature.combat.EquipListener}
 * pattern).
 */
public abstract class ApplyGestureListener implements Listener {

    protected final Messages messages;
    private final ParticleFx particles;
    private final Sounds sounds;

    protected ApplyGestureListener(Messages messages, Sounds sounds) {
        this(messages, ParticleFx.NONE, sounds);
    }

    protected ApplyGestureListener(Messages messages, ParticleFx particles, Sounds sounds) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.particles = Objects.requireNonNull(particles, "particles");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public final void onGestureClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // A pre-gesture branch the subclass owns entirely (nametag's anvil dialog lock) must run FIRST, so a
        // click inside a subclass-owned GUI is never re-interpreted as a fresh drag-onto-gear gesture.
        if (handledBeforeGesture(player, event)) {
            return;
        }
        if (!claimsClick(event)) {
            return;
        }
        if (MenuClicks.clickedInventory(event) == null
                || MenuClicks.clickedInventory(event) != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor == null || !claimsCursor(cursor)) {
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || Mats.isAir(target.getType())) {
            return;
        }
        if (!claimsTarget(cursor, target)) {
            return;
        }

        event.setCancelled(true);
        GestureOutcome result = apply(player, cursor, target, event.getSlot());
        if (result.consumeCursor()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
        }
        if (result.commit()) {
            ItemStack next = result.newTarget();
            event.setCurrentItem(next == null || next.getAmount() <= 0 ? null : next);
        }
        if (result.produced() != null) {
            Inventories.giveOrDrop(player, result.produced());
        }
        if (result.consumeCursor() || result.commit()) {
            player.updateInventory();
        }
        GestureOutcome.Cue cue = result.cue();
        if (cue != null) {
            if (cue.sound() != null) {
                sounds.play(player, player.getLocation(), cue.sound().name(), cue.sound().volume(), cue.sound().pitch());
            }
            if (!cue.particles().isEmpty()) {
                particles.spawn(player, cue.particles(), 1);
            }
        }
        if (result.message() != null) {
            messages.sendText(player, result.message());
        }
        if (result.followUp() != null) {
            result.followUp().run();
        }
    }

    /** A pre-gesture branch the subclass owns entirely (nametag's anvil dialog lock). Default: not handled. */
    protected boolean handledBeforeGesture(Player player, InventoryClickEvent event) {
        return false;
    }

    /** Which click shapes begin the gesture; default plain LEFT/RIGHT (crystal adds SWAP_WITH_CURSOR). */
    protected boolean claimsClick(InventoryClickEvent event) {
        // CREATIVE is the plain-click shape creative-mode clients deliver; they are inventory-authoritative,
        // and updateInventory() repaints them after the commit.
        return event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT
                || event.getClick() == ClickType.CREATIVE;
    }

    protected abstract boolean claimsCursor(ItemStack cursor);

    /** Default: X-onto-X is meaningless — the target must not itself be a cursor item of this family. */
    protected boolean claimsTarget(ItemStack cursor, ItemStack target) {
        return !claimsCursor(target);
    }

    /** The family's service call; runs cancelled, on the player's region thread. {@code slot} = event.getSlot(). */
    protected abstract GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot);
}
