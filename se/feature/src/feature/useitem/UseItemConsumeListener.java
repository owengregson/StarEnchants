package feature.useitem;

import compile.load.UseItemDef;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Complete an {@code is-food} use-item's eat gesture (§3.6, docs/decisions/0048-use-items.md; the is-food design
 * spec): on 1.20.5+ the right-click starts a real eating animation ({@link UseItemListener} deliberately does not
 * claim it) and this fires the abilities only when the eat FINISHES. Cancelling the event suppresses the vanilla
 * nutrition gain AND the vanilla single-item stack decrement on every ≥1.20.5 band (verified: the cancelled path
 * skips {@code finishUsingItem}), so the plugin owns the consume itself via the shared {@link UseItemRunner}.
 *
 * <p>Priority LOW mirrors the interact claim. Folia-correct: the consume event fires on the player's own region
 * thread and only their held item is touched. Registered always; below 1.20.5 our items carry no food/consumable
 * component, so no eat ever starts and this never fires for them — no behavior leak across the version boundary.
 */
public final class UseItemConsumeListener implements Listener {

    private final UseItemService service;
    private final UseItemRunner runner;
    private final BooleanSupplier enabled; // §L features.use-items — live, same toggle as the interact path

    public UseItemConsumeListener(UseItemService service, UseItemRunner runner, BooleanSupplier enabled) {
        this.service = Objects.requireNonNull(service, "service");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!enabled.getAsBoolean()) {
            return; // feature disabled
        }
        ItemStack eaten = event.getItem();
        String key = eaten == null ? null : service.keyOf(eaten);
        if (key == null) {
            return; // not a use-item
        }
        UseItemDef def = service.defOf(key);
        if (def != null && !def.isFood()) {
            return; // defensive: an edible use-item whose live def is no longer is-food — leave the vanilla eat be
        }
        event.setCancelled(true); // suppress vanilla nutrition + the vanilla single-item decrement; we own both
        runner.activate(event.getPlayer(), key);
    }
}
