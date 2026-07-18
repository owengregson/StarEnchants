package feature.combat;

import engine.sink.EngineDamage;
import engine.stores.HitTempoStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Third-party fairness for Quickening Fang (ADR-0071 HIT_TEMPO). A holder's landed tempo hit rewrites its
 * victim's i-frames so the HOLDER's next full hit is admitted early; that same early-clear would let ANY
 * attacker in the window strike early too. This gate — at LOWEST + ignoreCancelled, before the whole SE
 * pipeline (CombatDispatch is HIGH) — cancels a hit on that victim from an attacker holding no live tempo
 * interval of their own, so third parties see exactly the i-frames the write erased while concurrent
 * Quickening holders each keep their own cadence (per-holder stolen intervals in {@link HitTempoStore}).
 *
 * <p>A cancelled hit here reads to the rest of the server exactly like natural i-frames — no SE walk, no rage
 * break, no Mental knock — because a cancelled event never reaches those higher-priority listeners. Engine DoT
 * and reflect ticks obey the park economy, never this gate.
 */
public final class ReforgeTempoGuardListener implements Listener {

    private final HitTempoStore hitTempo;
    private final LongSupplier nowTicks;

    public ReforgeTempoGuardListener(HitTempoStore hitTempo, LongSupplier nowTicks) {
        this.hitTempo = Objects.requireNonNull(hitTempo, "hitTempo");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (EngineDamage.active()) {
            return; // engine DoT/reflect ticks obey the park economy, never this gate
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter; // a bow shot is its shooter's hit (the dispatch's resolve)
        }
        if (hitTempo.stolenBlocks(event.getEntity().getUniqueId(), damager.getUniqueId(), nowTicks.getAsLong())) {
            event.setCancelled(true); // the i-frame the tempo write erased, restored for non-holders
        }
    }
}
