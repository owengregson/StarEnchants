package tester.suite;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import tester.harness.Harness;

/**
 * Tick-anchored arrival poll shared by the TELEPORT suites (live-server-testing: wait in GAME ticks, assert the
 * server-side state change). PASS the moment {@code actor} is within {@code maxHorizontal} blocks (Y ignored, so
 * a falling player's drift never counts) of a FIXED {@code target}; FAIL at {@code maxTicks} with the final
 * distance and both positions, so a future failure says whether the actor never moved (≈ the staged gap) or
 * merely landed short (between the threshold and the gap).
 *
 * <p>Polling proximity to the victim — not displacement from the origin — tests the actual semantic ("teleported
 * TO the victim") and tolerates the collision push-out that lands an actor a block or two shy of a mob's space.
 * The target is captured ONCE by the caller (on the victim's own thread) and passed in by value, and the poll
 * reads only {@code actor.getLocation()} on the actor's own scheduler — which follows the actor across a Folia
 * region boundary — so nothing here ever touches a second entity cross-region.
 */
final class Proximity {

    private Proximity() {
    }

    static void awaitWithin(Player actor, Location target, double maxHorizontal, int maxTicks,
                            Harness h, String check, Runnable cleanup) {
        step(actor, target, maxHorizontal, 0, maxTicks, h, check, cleanup);
    }

    private static void step(Player actor, Location target, double maxHorizontal, int tick, int maxTicks,
                             Harness h, String check, Runnable cleanup) {
        double dist = horizontalDistance(actor.getLocation(), target);
        if (dist <= maxHorizontal) {
            h.pass(check);
            cleanup.run();
            return;
        }
        if (tick >= maxTicks) {
            h.fail(check, "actor did not arrive within " + maxHorizontal + " blocks of the target in " + maxTicks
                    + " ticks (final horizontal distance " + String.format("%.2f", dist)
                    + ", actor=" + format(actor.getLocation()) + ", target=" + format(target) + ")");
            cleanup.run();
            return;
        }
        Scheduling.onEntityLater(actor, 1L,
                () -> step(actor, target, maxHorizontal, tick + 1, maxTicks, h, check, cleanup));
    }

    /** Distance between two locations ignoring the Y axis — immune to a falling actor's vertical drift. */
    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String format(Location loc) {
        return String.format("(%.1f,%.1f,%.1f)", loc.getX(), loc.getY(), loc.getZ());
    }
}
