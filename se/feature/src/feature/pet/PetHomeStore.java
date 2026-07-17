package feature.pet;

import engine.stores.PlayerScoped;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Mole-style dig-home windows (ADR-0061): {@code player → (world id, coords, recall range, expiry tick,
 * arm generation)} — the {@link PetArmedStore} twin. One window per player: a dig replaces any previous one.
 * The GENERATION pairs each window with its scheduled expiry task so a stale expiry can never clear a newer
 * dig ({@link #clearIfGeneration}); a recall consumes via {@link #clear}. Coordinates are primitives + the
 * world UID — never a {@code Location}/{@code World} reference, so a 30-second entry cannot pin a world and an
 * unload cannot leave a dangling weak ref; the recall rebuilds the destination from the PLAYER's own world
 * after the UID match.
 *
 * <p>Cleared on recall (consume), expiry (the generation-guarded task), death ({@code PetService.dropWindows}),
 * quit (the module's {@link PlayerScoped} sweep) and disable (the module stop). Never persisted — a window
 * must not survive a restart. Concurrency: all writes for one player happen on that player's own thread
 * (dig/recall run in their interact event, the expiry on their entity scheduler); quit sweeps elsewhere —
 * the {@link PetArmedStore} contract.
 */
public final class PetHomeStore implements PlayerScoped {

    /** One dig-home window; {@code generation} pairs it with its scheduled expiry task. */
    public record Home(UUID worldId, double x, double y, double z, float yaw, float pitch,
                       double range, long expiryTick, long generation) {
    }

    private final Map<UUID, Home> homes = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();

    /** Open (or replace) {@code player}'s home window; returns the arm generation for the expiry task. */
    public long arm(UUID player, UUID worldId, double x, double y, double z, float yaw, float pitch,
                    double range, long expiryTick) {
        long generation = generations.incrementAndGet();
        homes.put(player, new Home(worldId, x, y, z, yaw, pitch, range, expiryTick, generation));
        return generation;
    }

    /** The live window at {@code nowTick}, or {@code null}; an elapsed entry is lazily evicted. */
    public Home get(UUID player, long nowTick) {
        Home home = homes.get(player);
        if (home == null) {
            return null;
        }
        if (nowTick >= home.expiryTick()) {
            homes.remove(player, home); // value-matched: a concurrent re-dig survives
            return null;
        }
        return home;
    }

    /**
     * Clear {@code player}'s window iff it is still generation {@code generation} (the scheduled expiry's own
     * arm) — a re-dug window survives its predecessor's task. Returns whether it cleared (the caller sends the
     * ENDED message only then).
     */
    public boolean clearIfGeneration(UUID player, long generation) {
        Home home = homes.get(player);
        if (home == null || home.generation() != generation) {
            return false;
        }
        homes.remove(player, home);
        return true;
    }

    /** Consume/teardown for one player (recall, death, quit). */
    @Override
    public void clear(UUID player) {
        homes.remove(player);
    }

    /** Disable teardown. */
    public void clearAll() {
        homes.clear();
    }
}
