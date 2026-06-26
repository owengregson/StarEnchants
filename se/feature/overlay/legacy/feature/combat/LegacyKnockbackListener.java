package feature.combat;

import engine.stores.KnockbackControlStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.event.Listener;

/**
 * Legacy (1.8.9) KNOCKBACK_CONTROL applier — a no-op. 1.8 Spigot has no Paper
 * {@code EntityKnockbackByEntityEvent} (the {@code com.destroystokyo.paper} package is absent), and
 * {@code KnockbackListener} only registers an applier when that event class is present, so on 1.8 this is
 * never wired. Same-FQN counterpart to the {@code overlay/modern} listener; kept (with the same ctor) so
 * the shared registrar still type-checks. KNOCKBACK_CONTROL on 1.8 would need an NMS damage hook
 * (Phase 2 / Gate 4) — docs/legacy-1.8.9-codeshare-design.md §6.
 */
final class LegacyKnockbackListener implements Listener {

    LegacyKnockbackListener(KnockbackControlStore store, LongSupplier nowTicks) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(nowTicks, "nowTicks");
    }
}
