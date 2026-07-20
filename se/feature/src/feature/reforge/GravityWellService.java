package feature.reforge;

import compile.model.CompiledEffect;
import compile.resolve.PlatformResolvers;
import engine.sink.EngineDamage;
import engine.stores.PlayerScoped;
import feature.trigger.TriggerDispatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import platform.caps.Regions;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import schema.spec.Args;

/**
 * The Singularity collapsing star (ADR-0071): per activation, ONE region task at the well core — beam to the
 * anchor (the sighted block, or the ground straight beneath the ray's end when the aim finds only air; mid-air
 * only over a bottomless drop), per-period pull pulses, then a single implosion — armed from the compiled
 * {@code GRAVITY_WELL}
 * args at the reforge activation success point (the {@code PetService.digHomeEffect} template). Visuals/cues ride
 * fresh per-call {@link TriggerDispatch} sinks; every victim mutation hops to the victim's own scheduler; the
 * implosion attributes to the caster only when the caster handle is region-owned at hurt time (the
 * {@code ComboDotRelease} idiom). Wells are location-anchored: the owner quitting drops only the attribution
 * handle, never the star. Static LIVE map (the {@code SwarmClouds} lifecycle) with a {@link #scope} quit sweep
 * and a {@link #clearAll} disable teardown.
 */
public final class GravityWellService {

    /** One live well: the core, the schedule, the owner (attribution only), the captured args. */
    private static final class Well {
        final long id;
        final Location core;
        final UUID ownerId;
        volatile LivingEntity owner; // dropped on owner quit (scope sweep); only handed to EngineDamage
        final double radius;
        final double pull;
        final double damage;
        final double falloffFloor;
        final int duration;
        final int period;
        final boolean selfPull;
        final boolean selfDamage;
        final int r;
        final int g;
        final int b;
        int elapsed;
        TaskHandle task;

        Well(long id, Location core, UUID ownerId, LivingEntity owner, double radius, double pull, double damage,
             double falloffFloor, int duration, int period, boolean selfPull, boolean selfDamage,
             int r, int g, int b) {
            this.id = id;
            this.core = core;
            this.ownerId = ownerId;
            this.owner = owner;
            this.radius = radius;
            this.pull = pull;
            this.damage = damage;
            this.falloffFloor = falloffFloor;
            this.duration = duration;
            this.period = period;
            this.selfPull = selfPull;
            this.selfDamage = selfDamage;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private static final Map<Long, Well> LIVE = new ConcurrentHashMap<>();
    private static final AtomicLong IDS = new AtomicLong();
    /** Max blocks an into-air aim drops looking for ground before it settles for a mid-air anchor. */
    private static final int GROUND_DROP_LIMIT = 128;

    private final TriggerDispatch dispatch;
    private final BiFunction<Player, Integer, Block> targetBlock; // era raycast ref (§B2.5 composition-only seam)
    private final int dustId; // interned REDSTONE or -1 (the PetHomeVisuals idiom)
    private final Cue[] launch;   // beam-throw cues
    private final Cue[] collapse; // periodic groan during the pull phase
    private final Cue[] implodeCue; // the implosion crack

    public GravityWellService(TriggerDispatch dispatch, BiFunction<Player, Integer, Block> targetBlock,
                              PlatformResolvers resolvers) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.targetBlock = Objects.requireNonNull(targetBlock, "targetBlock");
        Objects.requireNonNull(resolvers, "resolvers");
        this.dustId = resolvers.particle("REDSTONE").orElse(-1);
        this.launch = new Cue[]{
                cue(resolvers, "ENTITY_ENDER_EYE_DEATH", 1.0f, 0.6f),
                cue(resolvers, "ENTITY_ILLUSIONER_CAST_SPELL", 0.8f, 0.7f)};
        this.collapse = new Cue[]{
                cue(resolvers, "BLOCK_BEACON_AMBIENT", 0.6f, 0.5f),
                cue(resolvers, "BLOCK_PORTAL_AMBIENT", 0.5f, 0.7f)};
        this.implodeCue = new Cue[]{
                cue(resolvers, "ENTITY_GENERIC_EXPLODE", 1.0f, 0.8f),
                cue(resolvers, "BLOCK_ANVIL_LAND", 0.7f, 0.6f)};
    }

    /** Reforge runner hook: the def activated and carries a {@code GRAVITY_WELL} effect — start the well. */
    public void start(Player actor, CompiledEffect fx) {
        Args a = fx.args();
        double range = a.dbl("range");
        double radius = a.dbl("radius");
        double rise = a.dbl("rise");
        int duration = a.integer("duration");
        int period = a.integer("period");
        double pull = a.dbl("pull");
        double damage = a.dbl("damage");
        double falloffFloor = a.dbl("falloff-floor");
        boolean selfPull = a.bool("self-pull");
        boolean selfDamage = a.bool("self-damage");
        int r = a.integer("r");
        int g = a.integer("g");
        int b = a.integer("b");

        // Guarded: on Folia a ray crossing an unowned region can throw; a faulted (null) read falls into the
        // air anchor below (pure math, region-safe).
        int reach = (int) Math.ceil(range);
        Block block = Regions.read("GravityWellService.sight", () -> targetBlock.apply(actor, reach), null);
        Location eye = actor.getEyeLocation();
        Location core;
        Location beamEnd;
        if (block != null) {
            beamEnd = block.getLocation().clone().add(0.5, 0.5, 0.5);
            core = block.getLocation().clone().add(0.5, 1.0 + rise, 0.5);
        } else {
            // Air aim — the ray reached full range without hitting terrain. Drop STRAIGHT DOWN from the ray's
            // end to the first solid block and anchor the well above it (the terrain rule), so an into-air aim
            // still lands a real ground singularity where you pointed. Only a bottomless drop (deep void / a
            // very high aim) keeps the mid-air anchor. Guarded: the column read may cross an unowned region.
            Location airPoint = eye.clone().add(eye.getDirection().multiply(range));
            Block ground = Regions.read("GravityWellService.drop", () -> groundUnder(airPoint), null);
            if (ground != null) {
                beamEnd = ground.getLocation().clone().add(0.5, 0.5, 0.5);
                core = ground.getLocation().clone().add(0.5, 1.0 + rise, 0.5);
            } else {
                core = airPoint;
                beamEnd = airPoint;
            }
        }
        dispatch.dust(beam(eye, beamEnd), dustId, r, g, b, 1.0f);
        play(eye, launch);

        long id = IDS.incrementAndGet();
        Well well = new Well(id, core, actor.getUniqueId(), actor, radius, pull, damage, falloffFloor,
                duration, period, selfPull, selfDamage, r, g, b);
        LIVE.put(id, well);
        well.task = Scheduling.repeatingRegion(core, period, period, () -> tick(id));
    }

    /** The repeating body (on the core's region thread): star dust + a pull pulse, then the implosion at the end. */
    private void tick(long id) {
        Well w = LIVE.get(id);
        if (w == null) {
            return;
        }
        w.elapsed += w.period;
        starVisual(w);
        if (w.elapsed % 10 == 0) {
            play(w.core, collapse);
        }
        // The boundary-inclusive rule: the implosion and its teardown share the FINAL run so no pulse races them.
        if (w.elapsed >= w.duration) {
            implode(w);
            drop(id);
            return;
        }
        pullPulse(w);
    }

    /** Drag every living thing within radius toward the core (each velocity write on the victim's own scheduler). */
    private void pullPulse(Well w) {
        for (Entity e : w.core.getWorld().getNearbyEntities(w.core, w.radius, w.radius, w.radius)) {
            if (!(e instanceof LivingEntity victim)) {
                continue;
            }
            if (!w.selfPull && victim.getUniqueId().equals(w.ownerId)) {
                continue;
            }
            Location vloc = Regions.read("GravityWellService.pull", victim::getLocation, null);
            if (vloc == null) {
                continue;
            }
            double dx = w.core.getX() - vloc.getX();
            double dy = w.core.getY() - vloc.getY();
            double dz = w.core.getZ() - vloc.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > w.radius) {
                continue; // getNearbyEntities scans a CUBE whose corners reach r·√3 — trim to the authored sphere
            }
            if (dist < 0.4) {
                continue; // already at the core
            }
            double scale = w.pull / dist;
            Vector v = new Vector(dx * scale, dy * scale + 0.04, dz * scale); // +y fights gravity → levitational drag
            Scheduling.onEntity(victim, () -> victim.setVelocity(victim.getVelocity().add(v)));
        }
    }

    /** The single implosion: RAW health-space damage with linear falloff floored at {@code falloff-floor}. */
    private void implode(Well w) {
        play(w.core, implodeCue);
        for (Entity e : w.core.getWorld().getNearbyEntities(w.core, w.radius, w.radius, w.radius)) {
            if (!(e instanceof LivingEntity victim)) {
                continue;
            }
            if (!w.selfDamage && victim.getUniqueId().equals(w.ownerId)) {
                continue;
            }
            Location vloc = Regions.read("GravityWellService.implode", victim::getLocation, null);
            if (vloc == null) {
                continue;
            }
            double dist = distance(w.core, vloc);
            if (dist > w.radius) {
                continue; // cube corner outside the authored sphere
            }
            double dmg = w.damage * Math.max(w.falloffFloor, 1.0 - dist / w.radius);
            Scheduling.onEntity(victim, () -> {
                LivingEntity owner = w.owner;
                boolean attributable = owner != null && Regions.ownedByCurrentRegion(owner) && owner.isValid();
                EngineDamage.hurt(victim, dmg, attributable ? owner : null); // the ComboDotRelease attribution idiom
            });
        }
    }

    /** A shrinking dust ring at the core — pure math + one fresh-sink batch. */
    private void starVisual(Well w) {
        if (dustId < 0) {
            return;
        }
        double fraction = Math.min(1.0, (double) w.elapsed / Math.max(1, w.duration));
        double radius = 2.0 - 1.8 * fraction;
        int count = 14;
        List<Location> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = (2.0 * Math.PI * i) / count;
            points.add(new Location(w.core.getWorld(),
                    w.core.getX() + radius * Math.cos(angle),
                    w.core.getY(),
                    w.core.getZ() + radius * Math.sin(angle)));
        }
        dispatch.dust(points, dustId, w.r, w.g, w.b, 1.0f);
    }

    /** Cancel + evict (idempotent). */
    private static void drop(long id) {
        Well w = LIVE.remove(id);
        if (w != null && w.task != null) {
            w.task.cancel();
        }
    }

    /** Quit sweep adapter: null out the quitter's attribution handle on their live wells (the star lives on). */
    public static PlayerScoped scope() {
        return quitter -> {
            for (Well w : LIVE.values()) {
                if (w.ownerId.equals(quitter)) {
                    w.owner = null;
                }
            }
        };
    }

    /** Disable stop ("gravity wells"): cancel every well task and clear the registry. */
    public static void clearAll() {
        for (Long id : List.copyOf(LIVE.keySet())) {
            drop(id);
        }
    }

    /** Test seam: the number of live wells. */
    static int liveCount() {
        return LIVE.size();
    }

    private void play(Location at, Cue[] cues) {
        for (Cue cue : cues) {
            dispatch.sound(at, cue.sound(), cue.volume(), cue.pitch()); // id < 0 (absent here) skips inside dispatch
        }
    }

    private static Cue cue(PlatformResolvers resolvers, String token, float volume, float pitch) {
        return new Cue(resolvers.sound(token).orElse(-1), volume, pitch);
    }

    /**
     * The first solid block straight down from {@code airPoint} (inclusive of the block it sits in), scanning a
     * bounded column — the ground an air-aimed well drops onto. {@code null} when nothing solid is within
     * {@link #GROUND_DROP_LIMIT} (a bottomless drop). Version-safe: a fixed step budget, no {@code
     * World#getMinHeight} (absent pre-1.17 / on the legacy tree).
     */
    private static Block groundUnder(Location airPoint) {
        if (airPoint.getWorld() == null) {
            return null;
        }
        int x = airPoint.getBlockX();
        int z = airPoint.getBlockZ();
        int top = airPoint.getBlockY();
        for (int drop = 0; drop <= GROUND_DROP_LIMIT; drop++) {
            Block candidate = airPoint.getWorld().getBlockAt(x, top - drop, z);
            if (candidate.getType().isSolid()) {
                return candidate;
            }
        }
        return null;
    }

    /** Sample the eye→block-center segment at 3 points/block for the throw beam. */
    private static List<Location> beam(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.round(dist * 3.0));
        List<Location> points = new ArrayList<>(steps + 1);
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            points.add(new Location(from.getWorld(), from.getX() + dx * t, from.getY() + dy * t, from.getZ() + dz * t));
        }
        return points;
    }

    private static double distance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record Cue(int sound, float volume, float pitch) {
    }
}
