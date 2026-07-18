package feature.reforge;

import feature.compat.Hands;
import item.codec.CombatCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * SHIFT + right-click a held reforged weapon to fire its signature ability (ADR-0070). Bukkit-thin —
 * the activation tail is {@link ReforgeRunner}; the guard structure clones {@link feature.pet.PetUseListener}
 * (priority LOW, NOT ignoreCancelled, main-hand only, RIGHT_CLICK_AIR|BLOCK) plus the SNEAK gate: a
 * non-sneak right-click falls through untouched (the weapon's vanilla use — a sword blocks on 1.8 —
 * survives). Folia-correct: fires on the player's own region thread, touching only their held item.
 */
public final class ReforgeUseListener implements Listener {

    private final ReforgeRunner runner;
    private final CombatCodec codec;
    private final Hands hands;
    private final BooleanSupplier enabled; // §L features.reforges — live; disabled leaves the weapon vanilla

    public ReforgeUseListener(ReforgeRunner runner, CombatCodec codec, Hands hands, BooleanSupplier enabled) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

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
        if (!player.isSneaking()) {
            return; // the reforge active is SHIFT-gated; a plain right-click keeps its vanilla meaning
        }
        // Read the main hand directly (not event.getItem(), which is null on an air-click).
        ItemStack held = hands.mainHand(player);
        String key = held == null ? null : codec.read(held).reforgeKey();
        if (key == null) {
            return; // not a reforged weapon
        }
        if (!enabled.getAsBoolean()) {
            return; // feature disabled: leave the weapon vanilla (do not claim the gesture)
        }
        event.setCancelled(true); // claim the gesture: shift+right-click on a reforged weapon is the active
        runner.activate(player, key);
    }
}
