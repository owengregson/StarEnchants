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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * The Mole home-window visuals (ADR-0061 amendment): while a {@link PetHomeStore} window is live, a 10-tick
 * pulse draws a pulsating ring on the home block and, while the owner is within recall range, a dust tracer
 * line from the home block to the player, both in the pet's colour at the KOTH feet offset. Window-tied
 * exactly like the expiry: generation-guarded, self-cancelling when the store entry is consumed/expired/
 * replaced, cleared on recall/death ({@code PetService}), quit (the module store sweep) and disable
 * ({@link #clearAll}). Every mote rides the shared sink's dust intent — region-routed on Folia, offset-RGB
 * colour on 1.8.9 (the KOTH plumbing). The class also owns the mole's five layered sound-cue tables
 * (ADR-0067) — dig / teleport / range-exit / range-enter / expired — resolved once at construction, with any
 * layer whose sound is absent on this version silently skipped.
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
    // TUNABLE(recall cues): a small colour ring at each end of the hop; the sound layers are the `teleport` table.
    static final double BURST_RADIUS = 0.5;
    static final int BURST_COUNT = 10;

    /** One owner-specced cue layer: the interned sound id (-1 = absent on this version → skipped) + volume/pitch. */
    private record Cue(int sound, float volume, float pitch) {
    }

    private record Task(TaskHandle handle, long generation, AtomicBoolean inRange) {
    }

    private final TriggerDispatch dispatch;
    private final PetHomeStore homes;
    private final LongSupplier nowTicks;
    private final int dust; // interned REDSTONE (DUST alias) or -1: visuals silently off, never a crash
    private final Cue[] dig;      // dig home (ADR-0067 — owner-specced verbatim)
    private final Cue[] teleport; // teleport home, played at both ends of the hop
    private final Cue[] exit;     // leaving the recall range
    private final Cue[] enter;    // re-entering the recall range
    private final Cue[] expired;  // the window lapsed without a recall
    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

    public PetHomeVisuals(TriggerDispatch dispatch, PetHomeStore homes, LongSupplier nowTicks,
                          PlatformResolvers resolvers) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.homes = Objects.requireNonNull(homes, "homes");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        this.dust = resolvers.particle("REDSTONE").orElse(-1);
        this.dig = new Cue[]{
                cue(resolvers, "ENTITY_ENDER_EYE_DEATH", 1.0f, 0.85f),
                cue(resolvers, "BLOCK_BAMBOO_WOOD_DOOR_OPEN", 0.7f, 1.0f),
                cue(resolvers, "ITEM_SHOVEL_FLATTEN", 1.0f, 0.7f)};
        this.teleport = new Cue[]{
                cue(resolvers, "ENTITY_ENDER_EYE_DEATH", 1.0f, 1.45f),
                cue(resolvers, "BLOCK_BAMBOO_WOOD_DOOR_OPEN", 0.7f, 1.55f),
                cue(resolvers, "ITEM_SHOVEL_FLATTEN", 1.0f, 1.25f)};
        this.exit = new Cue[]{
                cue(resolvers, "BLOCK_NETHER_WOOD_DOOR_CLOSE", 0.9f, 1.0f),
                cue(resolvers, "BLOCK_ROOTED_DIRT_BREAK", 0.55f, 0.7f),
                cue(resolvers, "BLOCK_CANDLE_EXTINGUISH", 1.0f, 1.10f)};
        this.enter = new Cue[]{
                cue(resolvers, "BLOCK_AMETHYST_CLUSTER_PLACE", 0.5f, 1.20f),
                cue(resolvers, "BLOCK_NETHER_WOOD_DOOR_OPEN", 1.0f, 1.0f),
                cue(resolvers, "BLOCK_ROOTED_DIRT_BREAK", 0.85f, 1.15f)};
        this.expired = new Cue[]{
                cue(resolvers, "BLOCK_ROOTED_DIRT_BREAK", 0.55f, 0.7f),
                cue(resolvers, "BLOCK_NETHER_WOOD_DOOR_CLOSE", 0.9f, 1.0f),
                cue(resolvers, "BLOCK_CANDLE_EXTINGUISH", 1.0f, 1.10f),
                cue(resolvers, "BLOCK_VAULT_DEACTIVATE", 0.75f, 0.7f),
                cue(resolvers, "BLOCK_GLASS_BREAK", 0.3f, 0.6f)};
    }

    private static Cue cue(PlatformResolvers resolvers, String token, float volume, float pitch) {
        return new Cue(resolvers.sound(token).orElse(-1), volume, pitch);
    }

    private void play(Location at, Cue[] cues) {
        for (Cue cue : cues) {
            dispatch.sound(at, cue.sound(), cue.volume(), cue.pitch()); // id < 0 (absent here) skips
        }
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
        AtomicBoolean in = new AtomicBoolean(true); // dug at the home → IN; no enter-cue on creation (ADR-0067)
        TaskHandle[] handle = new TaskHandle[1];
        handle[0] = Scheduling.repeatingEntity(player, PERIOD_TICKS, PERIOD_TICKS, () -> {
            PetHomeStore.Home home = homes.peek(id, nowTicks.getAsLong()); // non-evicting: §5 owns the ending
            if (home == null || home.generation() != generation || !player.isValid()) {
                endIfGeneration(id, generation);
                handle[0].cancel(); // belt: also stops a task the map no longer tracks
                return;
            }
            World world = player.getWorld();
            Location at = player.getLocation();
            boolean now = home.inRange(world.getUID(), at.getX(), at.getY(), at.getZ());
            if (in.compareAndSet(!now, now)) {
                play(at, now ? enter : exit); // once per boundary crossing, at the player (ADR-0067)
            }
            if (!world.getUID().equals(home.worldId())) {
                return; // cross-world: nothing to draw (the recall reads it as out-of-range); window kept
            }
            int phase = pulse[0]++ % PULSE_RADII.length;
            List<Location> points = new ArrayList<>(MAX_LINE_STEPS + 1 + PULSE_COUNTS[phase]);
            collect(points, world, ringPoints(home.x(), home.y() + FEET_OFFSET, home.z(),
                    PULSE_RADII[phase], PULSE_COUNTS[phase]));
            if (now) { // the tracer LINE is range-gated (the spec's "[particle effect line stops playing]")
                collect(points, world, linePoints(home.x(), home.y() + FEET_OFFSET, home.z(),
                        at.getX(), at.getY() + FEET_OFFSET, at.getZ(), LINE_DENSITY, MAX_LINE_STEPS));
            }
            dispatch.dust(points, dust, rgb[0], rgb[1], rgb[2], DUST_SIZE);
        });
        tasks.put(id, new Task(handle[0], generation, in));
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
        play(from, teleport);
        // The arrival burrow AFTER the async hop: a dest-anchored play now reaches bystanders but not
        // the recalled player — their client is not there yet to receive the sound packet.
        platform.sched.Scheduling.onEntityLater(player, 2L, () -> {
            if (player.isValid()) {
                play(player.getLocation(), teleport);
            }
        });
    }

    /** The dig-home layered cue (ADR-0067) at the freshly-dug home — code-side because content cannot
     *  author tokens absent on old versions (a blocking E_UNKNOWN_HANDLE); these layers skip-absent. */
    public void digCues(Player player) {
        play(player.getLocation(), dig);
    }

    /** The home-expired-unused layered cue (ADR-0067) at the owner; the caller's generation guard
     *  already proved the window died unconsumed. A gone owner is silent. */
    public void expiredCues(Player player) {
        if (!player.isValid()) {
            return;
        }
        play(player.getLocation(), expired);
    }

    /** Whether a pulse task is live for {@code player} — the suite's window-tied start/stop seam. */
    public boolean active(UUID player) {
        return tasks.containsKey(player);
    }

    /** Whether the live pulse task currently holds the IN-range state — the suite's range-machine seam. */
    public boolean inRange(UUID player) {
        Task task = tasks.get(player);
        return task != null && task.inRange().get();
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
