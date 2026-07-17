package feature.pet;

import compile.load.ChatColorRgb;
import compile.load.PetDef;
import compile.resolve.PlatformResolvers;
import engine.stores.PlayerScoped;
import feature.trigger.TriggerDispatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * The Mole home-window visuals (ADR-0061 amendment): while a {@link PetHomeStore} window is live, a 10-tick
 * pulse draws a dust tracer line from the home block to the player and a pulsating ring on the home block,
 * both in the pet's colour at the KOTH feet offset. Window-tied exactly like the expiry: generation-guarded,
 * self-cancelling when the store entry is consumed/expired/replaced, cleared on recall/death
 * ({@code PetService}), quit (the module store sweep) and disable ({@link #clearAll}). Every mote rides the
 * shared sink's dust intent — region-routed on Folia, offset-RGB colour on 1.8.9 (the KOTH plumbing).
 */
public final class PetHomeVisuals implements PlayerScoped {

    // TUNABLE(pulse): cadence + shape. Cadence/offset/density mirror the KOTH aura (koth.yml repeat/height/density).
    static final long PERIOD_TICKS = 10L;
    static final double FEET_OFFSET = 0.1;
    static final double LINE_DENSITY = 2.0;
    static final int MAX_LINE_STEPS = 100; // bounds a wander past the 50-block recall range
    static final double[] PULSE_RADII = {0.25, 0.5, 0.7}; // expanding ping, capped at the 0.7 spec radius
    static final int[] PULSE_COUNTS = {6, 9, 12};
    static final float DUST_SIZE = 1.0f;
    // TUNABLE(recall cues): a small colour ring + the burrow sound at each end of the hop.
    static final double BURST_RADIUS = 0.5;
    static final int BURST_COUNT = 10;
    static final float CUE_VOLUME = 1.0f;
    static final float DEPART_PITCH = 1.2f;
    static final float ARRIVE_PITCH = 0.8f;

    private record Task(TaskHandle handle, long generation) {
    }

    private final TriggerDispatch dispatch;
    private final PetHomeStore homes;
    private final LongSupplier nowTicks;
    private final int dust; // interned REDSTONE (DUST alias) or -1: visuals silently off, never a crash
    private final int cue;  // interned BLOCK_GRASS_BREAK (1.8 DIG_GRASS alias) or -1
    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

    public PetHomeVisuals(TriggerDispatch dispatch, PetHomeStore homes, LongSupplier nowTicks,
                          PlatformResolvers resolvers) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.homes = Objects.requireNonNull(homes, "homes");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        this.dust = resolvers.particle("REDSTONE").orElse(-1);
        this.cue = resolvers.sound("BLOCK_GRASS_BREAK").orElse(-1);
    }

    /**
     * Start the pulse for {@code player}'s freshly-armed window (call right after {@link PetHomeStore#arm}).
     * The pet colour resolves ONCE here — a dig is a cold click; the pulse body never parses. A re-dig
     * replaces its predecessor's task; the body self-cancels once the window is consumed, expired or
     * replaced (the store read is the truth, so no teardown path can leak a pulse).
     */
    public void begin(Player player, PetDef def, long generation) {
        UUID id = player.getUniqueId();
        clear(id);
        if (dust < 0) {
            return;
        }
        int[] rgb = rgbOrWhite(def.color());
        int[] pulse = {0}; // the body always runs on the player's entity thread — no atomics needed
        TaskHandle[] handle = new TaskHandle[1];
        handle[0] = Scheduling.repeatingEntity(player, PERIOD_TICKS, PERIOD_TICKS, () -> {
            PetHomeStore.Home home = homes.get(id, nowTicks.getAsLong());
            if (home == null || home.generation() != generation || !player.isValid()) {
                endIfGeneration(id, generation);
                handle[0].cancel(); // belt: also stops a task the map no longer tracks
                return;
            }
            World world = player.getWorld();
            if (!world.getUID().equals(home.worldId())) {
                return; // cross-world: nothing to draw (the recall reads it as out-of-range); window kept
            }
            int phase = pulse[0]++ % PULSE_RADII.length;
            Location at = player.getLocation();
            List<Location> points = new ArrayList<>(MAX_LINE_STEPS + 1 + PULSE_COUNTS[phase]);
            collect(points, world, ringPoints(home.x(), home.y() + FEET_OFFSET, home.z(),
                    PULSE_RADII[phase], PULSE_COUNTS[phase]));
            collect(points, world, linePoints(home.x(), home.y() + FEET_OFFSET, home.z(),
                    at.getX(), at.getY() + FEET_OFFSET, at.getZ(), LINE_DENSITY, MAX_LINE_STEPS));
            dispatch.dust(points, dust, rgb[0], rgb[1], rgb[2], DUST_SIZE);
        });
        tasks.put(id, new Task(handle[0], generation));
    }

    /** Departure/arrival cues for a landed recall: a small colour ring + the burrow sound at both ends. */
    public void recallCues(Player player, PetDef def, Location from, Location to) {
        if (dust >= 0) {
            int[] rgb = rgbOrWhite(def.color());
            World world = player.getWorld(); // the recall already verified from/to share the player's world
            List<Location> points = new ArrayList<>(2 * BURST_COUNT);
            collect(points, world, ringPoints(from.getX(), from.getY() + FEET_OFFSET, from.getZ(),
                    BURST_RADIUS, BURST_COUNT));
            collect(points, world, ringPoints(to.getX(), to.getY() + FEET_OFFSET, to.getZ(),
                    BURST_RADIUS, BURST_COUNT));
            dispatch.dust(points, dust, rgb[0], rgb[1], rgb[2], DUST_SIZE);
        }
        dispatch.sound(from, cue, CUE_VOLUME, DEPART_PITCH);
        dispatch.sound(to, cue, CUE_VOLUME, ARRIVE_PITCH);
    }

    /** Whether a pulse task is live for {@code player} — the suite's window-tied start/stop seam. */
    public boolean active(UUID player) {
        return tasks.containsKey(player);
    }

    /** Stop iff the tracked task is still {@code generation}'s — the scheduled expiry's twin guard. */
    public void endIfGeneration(UUID player, long generation) {
        Task task = tasks.get(player);
        if (task != null && task.generation() == generation && tasks.remove(player, task)) {
            task.handle().cancel();
        }
    }

    /** Recall/death/quit teardown. */
    @Override
    public void clear(UUID player) {
        Task task = tasks.remove(player);
        if (task != null) {
            task.handle().cancel();
        }
    }

    /** Disable teardown. */
    public void clearAll() {
        for (UUID player : tasks.keySet()) {
            clear(player);
        }
    }

    private static void collect(List<Location> into, World world, double[][] points) {
        for (double[] p : points) {
            into.add(new Location(world, p[0], p[1], p[2]));
        }
    }

    /** Evenly-spaced ring of {@code count} points at {@code radius} around (cx, cy, cz) — the PARTICLE_RING math. */
    static double[][] ringPoints(double cx, double cy, double cz, double radius, int count) {
        double[][] points = new double[count][];
        for (int i = 0; i < count; i++) {
            double angle = (2.0 * Math.PI * i) / count;
            points[i] = new double[]{cx + radius * Math.cos(angle), cy, cz + radius * Math.sin(angle)};
        }
        return points;
    }

    /** Inclusive from→to line at {@code density} motes/block — the PARTICLE_LINE stepping, capped at {@code maxSteps}. */
    static double[][] linePoints(double fx, double fy, double fz, double tx, double ty, double tz,
                                 double density, int maxSteps) {
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.min(maxSteps, Math.max(1, (int) Math.round(dist * density)));
        double[][] points = new double[steps + 1][];
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            points[s] = new double[]{fx + dx * t, fy + dy * t, fz + dz * t};
        }
        return points;
    }

    /** The pet's first colour code as RGB, or white — resolved at dig time, never per pulse. */
    static int[] rgbOrWhite(String colorToken) {
        int[] rgb = ChatColorRgb.of(colorToken);
        return rgb == null ? new int[]{255, 255, 255} : rgb;
    }
}
