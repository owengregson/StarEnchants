package engine.sink;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.Location;

/**
 * Per-owner area zones: a cylinder (world + centre x/z + radius) a wearer owns for a window, so a condition can
 * ask "is the victim standing in one of MY zones?" (devil's Hell's Kitchen — +35% to an enemy inside the
 * hellfire floor). Written by the {@code MARK_ZONE} effect; consulted by the {@code %victim.inzone%} fact, which
 * keys the owner to the activating wearer. Static + era-agnostic (no store threading); wall-clock expiry,
 * self-evicting. An owner may hold several zones at once (each on-hit lays one under that victim).
 */
public final class OwnerZones {

    private OwnerZones() {
    }

    private record Zone(UUID world, double x, double z, double radiusSq, long expiryMs, UUID victim) {
    }

    private static final Map<UUID, Queue<Zone>> ZONES = new ConcurrentHashMap<>();

    /** Register a zone owned by {@code owner}: a cylinder of {@code radius} blocks at ({@code x},{@code z}) in
     *  {@code world}, active for {@code durationMs}, laid under {@code victim} (nullable — a victimless zone still
     *  serves the {@code %victim.inzone%} fact but grants nobody magma immunity). No-op on a null owner/world or a
     *  non-positive radius/duration. */
    public static void mark(UUID owner, UUID world, double x, double z, double radius, long durationMs, UUID victim) {
        if (owner == null || world == null || radius <= 0 || durationMs <= 0) {
            return;
        }
        ZONES.computeIfAbsent(owner, k -> new ConcurrentLinkedQueue<>())
                .add(new Zone(world, x, z, radius * radius, System.currentTimeMillis() + durationMs, victim));
    }

    /** Whether {@code loc} lies inside any of {@code owner}'s currently-active zones (expired ones self-evict). */
    public static boolean contains(UUID owner, Location loc) {
        if (owner == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        Queue<Zone> zones = ZONES.get(owner);
        if (zones == null || zones.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID world = loc.getWorld().getUID();
        double x = loc.getX();
        double z = loc.getZ();
        boolean inside = false;
        for (java.util.Iterator<Zone> it = zones.iterator(); it.hasNext();) {
            Zone zone = it.next();
            if (now >= zone.expiryMs()) {
                it.remove(); // lazily drop expired zones as we pass them
                continue;
            }
            if (world.equals(zone.world())) {
                double dx = x - zone.x();
                double dz = z - zone.z();
                if (dx * dx + dz * dz <= zone.radiusSq()) {
                    inside = true; // keep scanning so the rest of the queue still gets its expiry sweep
                }
            }
        }
        return inside;
    }

    /** Whether {@code entity} is the intended victim of an active zone it is standing in — the magma-burn immunity
     *  over a hellfire floor is scoped to the entity the floor was laid under, NOT everyone on it (so an ally or the
     *  wearer cannot surf a proc'd cylinder over natural magma). Scans every owner's zones; expired ones self-evict. */
    public static boolean immuneFor(UUID entity, Location loc) {
        if (entity == null || loc == null || loc.getWorld() == null || ZONES.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID world = loc.getWorld().getUID();
        double x = loc.getX();
        double z = loc.getZ();
        boolean immune = false;
        for (Queue<Zone> zones : ZONES.values()) {
            for (java.util.Iterator<Zone> it = zones.iterator(); it.hasNext();) {
                Zone zone = it.next();
                if (now >= zone.expiryMs()) {
                    it.remove(); // sweep expired zones as we pass them
                    continue;
                }
                if (entity.equals(zone.victim()) && world.equals(zone.world())) {
                    double dx = x - zone.x();
                    double dz = z - zone.z();
                    if (dx * dx + dz * dz <= zone.radiusSq()) {
                        immune = true; // keep scanning so every queue still gets its expiry sweep
                    }
                }
            }
        }
        return immune;
    }

    /** Forget one owner's zones (quit). */
    public static void clear(UUID owner) {
        ZONES.remove(owner);
    }

    /** Forget all zones (disable). */
    public static void clearAll() {
        ZONES.clear();
    }
}
