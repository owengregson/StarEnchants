package feature.mask;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import platform.sched.Scheduling;

/**
 * Illusion lifecycle re-assertion (ADR-0053 §5): join re-derives the joiner AND pushes every stored wearer to
 * them; respawn/teleport/world-change re-derive the mover (its client's tracker state reset). All 1-tick-deferred
 * on the player's entity thread (the {@code EquipListener} idiom — event-time state is stale, and an entity task
 * follows a Folia async teleport across regions). Quit cleanup is the module's {@code PlayerScoped} sweep, not
 * handled here. LIVE toggle: always registered, every handler short-circuits on the flag.
 */
public final class MaskIllusionListener implements Listener {

    private final MaskIllusionService illusions;
    private final BooleanSupplier enabled;

    public MaskIllusionListener(MaskIllusionService illusions, BooleanSupplier enabled) {
        this.illusions = Objects.requireNonNull(illusions, "illusions");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Player joiner = event.getPlayer();
        Scheduling.onEntityLater(joiner, 1L, () -> {
            illusions.refresh(joiner);
            illusions.pushTo(joiner);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        deferRefresh(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        deferRefresh(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        deferRefresh(event.getPlayer());
    }

    // ADR-0064: entering CREATIVE flips the wearer's self-view to reality (a creative client would write the
    // illusion back); leaving it re-asserts the mask. The 1-tick defer reads the NEW gamemode.
    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        deferRefresh(event.getPlayer());
    }

    // A DENY-ed use-item interact (a pet head / use-item claiming the right-click) makes the server resync
    // the client's whole container, and the resync carries the REAL helmet over a masked wearer's dressed
    // self-view until the next sweep. Re-assert one tick later — after the resync packet has gone out. The
    // masked() gate keeps the hot interact path to one map lookup for everyone else.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() != Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        if (illusions.masked(player.getUniqueId())) {
            deferRefresh(player);
        }
    }

    private void deferRefresh(Player player) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Scheduling.onEntityLater(player, 1L, () -> illusions.refresh(player));
    }
}
