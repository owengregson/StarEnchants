package feature.useitem;

import compile.load.UseItemDef;
import feature.compat.Hands;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import platform.item.EdibleItems;

/**
 * Right-click a held use-item to fire its abilities (§3.6, docs/decisions/0048-use-items.md). Bukkit-thin glue —
 * the activation tail is the shared {@link UseItemRunner}; this clones {@link feature.book.UnopenedBookListener}'s
 * guard structure (priority LOW, NOT ignoreCancelled, main-hand only, RIGHT_CLICK_AIR|BLOCK). Folia-correct:
 * fires on the player's own region thread, touching only their held item.
 *
 * <p>The one branch (the is-food design spec): an {@code is-food} def on a server where {@link EdibleItems} is
 * enabled (1.20.5+) is NOT claimed here — the right-click is left to start the vanilla eat animation, and
 * {@link UseItemConsumeListener} fires the abilities when the eat completes. Every other case (non-food items, and
 * {@code is-food} on a sub-1.20.5 server) still claims the right-click and fires immediately — which is where
 * "no-op below 1.20.5" falls out.
 */
public final class UseItemListener implements Listener {

    private final UseItemService service;
    private final UseItemRunner runner;
    private final EdibleItems edibleItems;
    private final Hands hands;
    private final BooleanSupplier enabled; // §L features.use-items — live; a disabled flag leaves the item inert

    public UseItemListener(UseItemService service, UseItemRunner runner, EdibleItems edibleItems, Hands hands,
                           BooleanSupplier enabled) {
        this.service = Objects.requireNonNull(service, "service");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.edibleItems = Objects.requireNonNull(edibleItems, "edibleItems");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    // priority LOW, NOT ignoreCancelled (a RIGHT_CLICK_BLOCK often arrives pre-cancelled) — mirrors the unopened
    // book so a use-item claims its right-click ahead of the INTERACT triggers (TriggerListeners at HIGH).
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!hands.isMainHand(event)) {
            return; // main-hand only — the off-hand pass of a two-hand interact would double-fire
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // Read the main hand directly (not event.getItem(), which is null on an air-click).
        ItemStack used = hands.mainHand(player);
        String key = used == null ? null : service.keyOf(used);
        if (key == null) {
            return; // not a use-item
        }
        if (!enabled.getAsBoolean()) {
            return; // feature disabled: leave the item inert (do not claim the gesture)
        }
        UseItemDef def = service.defOf(key);
        // An is-food item on a ≥1.20.5 server: let the vanilla eat BEGIN (do NOT cancel, which would abort the
        // animation). UseItemConsumeListener fires the abilities when the eat completes.
        if (def != null && def.isFood() && edibleItems.enabled()) {
            return;
        }
        event.setCancelled(true); // claim the gesture: a use-item does nothing else on right-click
        runner.activate(player, key);
    }
}
