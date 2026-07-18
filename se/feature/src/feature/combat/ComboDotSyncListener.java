package feature.combat;

import engine.sink.DotParkLedger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * The combo-DoT park-flush confirm + death clear (ADR-0069). At MONITOR it re-parks a drained flush the event
 * ended up cancelling (owed damage never dies with someone else's dodge). On death it clears the victim's
 * ledger so a respawn never inherits banked damage (the dead stay dead, ADR-0051) — and the same {@code clear}
 * drops the release in-flight flag, the cleanup that actually runs when a Folia mid-release death retires the
 * entity task without running its body.
 */
public final class ComboDotSyncListener implements Listener {

    private final DotParkLedger ledger;

    public ComboDotSyncListener(DotParkLedger ledger) {
        this.ledger = ledger;
    }

    /** NOT ignoreCancelled — the cancelled outcome is the one this confirm acts on (re-park owed damage). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageResolved(EntityDamageByEntityEvent event) {
        ParkFlushRelay.Pending pending = ParkFlushRelay.consume(event);
        if (pending != null && event.isCancelled()) {
            ledger.restore(pending.victim(), pending.drained());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ledger.clear(event.getEntity().getUniqueId());
    }
}
