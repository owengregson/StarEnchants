package engine.run;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import platform.caps.Regions;

/**
 * The actor's positional state at activation (ADR-0043), captured once on the firing thread before any
 * region hop; a null {@code world} marks an uncapturable origin (no actor, or a guarded cross-region fault).
 */
record ActorOrigin(double x, double y, double z, double eyeY, float yaw, float pitch, World world) {

    static final ActorOrigin ABSENT = new ActorOrigin(0, 0, 0, 0, 0f, 0f, null);

    boolean present() {
        return world != null;
    }

    /** Guarded like FactPopulator's actor reads (ADR-0042): a cross-region fault yields ABSENT, never a throw. */
    static ActorOrigin capture(Player actor) {
        if (actor == null) {
            return ABSENT;
        }
        try {
            Location at = actor.getLocation();
            World world = at.getWorld();
            if (world == null) {
                return ABSENT;
            }
            return new ActorOrigin(at.getX(), at.getY(), at.getZ(), at.getY() + actor.getEyeHeight(),
                    at.getYaw(), at.getPitch(), world);
        } catch (RuntimeException unreadable) {
            Regions.swallowed("ActorOrigin.capture", unreadable);
            return ABSENT;
        }
    }

    Location feet() {
        return new Location(world, x, y, z, yaw, pitch);
    }

    Location eye() {
        return new Location(world, x, eyeY, z, yaw, pitch);
    }
}
