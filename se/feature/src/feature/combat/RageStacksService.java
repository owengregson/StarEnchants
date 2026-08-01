package feature.combat;

import engine.stores.ComboStore;
import engine.stores.RageStackStore;
import feature.compat.Sounds;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import platform.lang.Messages;

/**
 * Exact Cosmic Rage combo state. PvP and PvE use independent, unbounded counters; a qualifying Rage melee hit
 * increments its bucket after the damage walk, while taking any damage clears both buckets. There is no idle
 * timeout, victim-switch reset, level cap, title, action-bar, or sound in the source mechanic.
 *
 * <p>The legacy constructor dependencies remain in the signature because this service is assembled by the shared
 * composition root. Only {@code rageLevelOf}, {@code store}, and {@code nowTicks} belong to the Cosmic mechanic.
 */
public final class RageStacksService {

    private final Function<Player, Integer> rageLevelOf;
    private final RageStackStore store;
    private final LongSupplier nowTicks;

    public RageStacksService(Function<Player, Integer> rageLevelOf, ComboStore ignoredCombo, RageStackStore store,
                             Messages ignoredMessages, Sounds ignoredSounds, LongSupplier nowTicks) {
        this.rageLevelOf = Objects.requireNonNull(rageLevelOf, "rageLevelOf");
        this.store = Objects.requireNonNull(store, "store");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    /** Compatibility form used by older tests/callers; treats the target as a player. */
    public void onHit(Player attacker) {
        onHit(attacker, true);
    }

    /** Increment the matching bucket only when the landed hit was made with an active Rage weapon. */
    public void onHit(Player attacker, boolean pvp) {
        if (rageLevelOf.apply(attacker) <= 0) {
            return;
        }
        UUID id = attacker.getUniqueId();
        store.set(id, pvp, store.current(id, pvp) + 1, nowTicks.getAsLong());
    }

    /** Exact source formula, evaluated from the counter value before the current hit increments it. */
    static double multiplier(int priorHits, int level) {
        return Math.min(2.5, Math.max(0, priorHits) * (0.05 * level) + 1.0);
    }

    /** Any damage taken removes both source metadata counters. */
    public void onHitTaken(Player holder) {
        store.clear(holder.getUniqueId());
    }
}
