package feature.pet;

import engine.stores.PlayerScoped;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shared pet-use gate (ADR-0070 rider): {@code player → gate-open-until tick}. A successful pet
 * activation (an active's use, or a Mole dig / recall past its point of no return) arms a short universal
 * window every pet USE attempt checks FIRST — you cannot fire a burst of different pet actives at once. A
 * pending home recall is EXEMPT from the check (owner ruling 2026-07-18): it resolves before the gate and
 * only arms it. Distinct from the per-ability gate-6 cooldown (ENCHANT scope): this one spans ALL pets for
 * one player. Cleared on quit (the module's {@link PlayerScoped} sweep). Concurrent: reads/writes on the
 * player's own thread, quit sweeps elsewhere.
 */
public final class PetSharedUseStore implements PlayerScoped {

    private final Map<UUID, Long> until = new ConcurrentHashMap<>();

    /** Open {@code player}'s shared window until {@code untilTick} (replacing any earlier one). */
    public void arm(UUID player, long untilTick) {
        until.put(player, untilTick);
    }

    /** Ticks left on {@code player}'s shared window at {@code nowTick}; {@code 0} = free to use. */
    public long remaining(UUID player, long nowTick) {
        Long t = until.get(player);
        return t == null ? 0 : Math.max(0, t - nowTick);
    }

    @Override
    public void clear(UUID player) {
        until.remove(player);
    }

    public void clearAll() {
        until.clear();
    }
}
