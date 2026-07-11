package feature.pet;

import feature.compat.Hands;
import item.codec.PetCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click a held pet head to fire its ACTIVE ability (ADR-0052). Bukkit-thin — the activation tail is
 * {@link PetService#use}; the guard structure clones {@link feature.useitem.UseItemListener} (priority LOW,
 * NOT ignoreCancelled, main-hand only, RIGHT_CLICK_AIR|BLOCK), so a pet claims its right-click ahead of the
 * INTERACT triggers. Also drops a dying holder's armed windows — a buff never survives death (the potion the
 * window granted is cleared by vanilla; the DAMAGE_MOD riders must go with it).
 */
public final class PetUseListener implements Listener {

    private final PetService service;
    private final PetCodec codec;
    private final Hands hands;
    private final BooleanSupplier enabled; // §L features.pets — live; a disabled flag leaves the head inert

    public PetUseListener(PetService service, PetCodec codec, Hands hands, BooleanSupplier enabled) {
        this.service = Objects.requireNonNull(service, "service");
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
        // Read the main hand directly (not event.getItem(), which is null on an air-click).
        ItemStack used = hands.mainHand(player);
        if (used == null || !codec.isPet(used)) {
            return;
        }
        if (!enabled.getAsBoolean()) {
            return; // feature disabled: leave the head inert (do not claim the gesture)
        }
        event.setCancelled(true); // claim the gesture: a pet head does nothing else on right-click
        service.use(player, used);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        service.dropWindows(event.getEntity().getUniqueId());
    }
}
