package feature.summon;

import engine.sink.GuardianCasts;
import engine.sink.PetSummons;
import engine.sink.SummonFlags;
import engine.sink.SummonPayloads;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * The two event-driven payload phases. DETONATE fully REPLACES the vanilla explosion: the event is cancelled,
 * so there is no terrain damage AND no vanilla entity damage, and the authored payload supplies the damage,
 * ignite, knockback and the cue. DEATH fires as the summon dies. Both run on the summon's own region thread
 * (its own event), and both forget the registries before the summon leaves — the ordering every summon path
 * keeps, so a reused entity id can never inherit a stale owner.
 */
public final class SummonPayloadListener implements Listener {

    private final SummonPayloads payloads;
    private final BooleanSupplier enabled;

    public SummonPayloadListener(SummonPayloads payloads, BooleanSupplier enabled) {
        this.payloads = Objects.requireNonNull(payloads, "payloads");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Entity summon = event.getEntity();
        SummonFlags flags = armed(summon, SummonFlags.PHASE_DETONATE);
        if (flags == null) {
            return;
        }
        event.setCancelled(true);
        payloads.fire(summon, flags);
        forget(summon.getUniqueId());
        summon.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Entity summon = event.getEntity();
        SummonFlags flags = armed(summon, SummonFlags.PHASE_DEATH);
        if (flags == null) {
            return;
        }
        payloads.fire(summon, flags);
        forget(summon.getUniqueId()); // the body is already leaving; only the registry rows are ours to drop
    }

    /** The summon's flags when it is armed for exactly {@code phase}, else {@code null}. */
    private SummonFlags armed(Entity summon, String phase) {
        if (summon == null || !enabled.getAsBoolean()) {
            return null;
        }
        SummonFlags flags = PetSummons.flags(summon.getUniqueId());
        return flags != null && flags.payloadOn(phase) ? flags : null;
    }

    private static void forget(UUID id) {
        PetSummons.forget(id);
        GuardianCasts.forget(id);
    }
}
