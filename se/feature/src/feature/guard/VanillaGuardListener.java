package feature.guard;

import feature.compat.Hands;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Strips the VANILLA behaviour from every custom plugin item — a custom item must do ONLY its intended action
 * (its dedicated drag/click apply or its dedicated right-click handler), never the vanilla mechanic of the
 * Material it happens to be built on (the slot-expander orb is an {@code ENDER_EYE}, so a bare right-click
 * would otherwise THROW it; the nametag is a {@code NAME_TAG}, so it would rename a clicked mob; a head material
 * (pets/masks are {@code PLAYER_HEAD}s) would be PLACED as a block; a food/potion material would be eaten/drunk).
 *
 * <p>Material-agnostic: it keys off the injected {@code isPluginItem} predicate (the OR of every economy/utility
 * codec, built at the composition root), NOT a material whitelist — so an admin re-skinning an item to any
 * vanilla material is still suppressed. It DENIES the item's own use while leaving block interaction intact
 * (so a player can still open a chest while holding the orb), and the dedicated listeners (soul gem toggle,
 * unopened-book open, the inventory-click appliers) keep working because none of them rely on the vanilla
 * item use. Block PLACEMENT of a placeable-material plugin item is denied outright at {@link BlockPlaceEvent}
 * — never placed-then-refunded — so a head item can never become a block. Real enchanted GEAR is deliberately
 * NOT covered — swords must still swing.
 *
 * <p>Cross-version: uses only {@code event.getItem()} / {@link Hands#mainHand} (no 1.9+ {@code getHand()}), so
 * the one shared class compiles + runs on the 1.8.9 floor and the modern range alike.
 */
public final class VanillaGuardListener implements Listener {

    private final Predicate<ItemStack> isPluginItem;
    private final Hands hands;

    public VanillaGuardListener(Predicate<ItemStack> isPluginItem, Hands hands) {
        this.isPluginItem = Objects.requireNonNull(isPluginItem, "isPluginItem");
        this.hands = Objects.requireNonNull(hands, "hands");
    }

    // LOW + NOT ignoreCancelled, mirroring the dedicated item listeners: DENY only the ITEM's vanilla use, never
    // the block interaction, so holding a custom item never stops a player opening a container / pressing a button.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isPluginItem.test(event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY); // suppress the vanilla item use (ender-eye throw, eat, …)
        }
    }

    // Renaming a mob with the nametag, hitting a mob with a thrown-type item, etc. — main-hand only (off-hand is
    // a 1.9+ concept; the shared signature stays floor-safe by reading the main hand rather than event.getHand()).
    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (isPluginItem.test(hands.mainHand(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isPluginItem.test(event.getItem())) {
            event.setCancelled(true); // a custom item built on a food/potion material is never eaten/drunk
        }
    }

    // A plugin item built on a placeable material is NEVER placed as a block. setUseItemInHand(DENY) on the interact
    // above is the clean no-server-place path, but a placeable material still reaches BlockPlaceEvent on some versions
    // (the soul-gem guard carries the same belt-and-braces cancel), so this is the definitive backstop: the placement
    // is denied outright — the client reverts its own prediction and the stack is never consumed, so no refund path.
    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (isPluginItem.test(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }
}
