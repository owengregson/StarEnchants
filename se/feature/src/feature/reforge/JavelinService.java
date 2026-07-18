package feature.reforge;

import compile.model.CompiledEffect;
import compile.resolve.PlatformResolvers;
import engine.sink.EngineDamage;
import engine.stores.PlayerScoped;
import feature.trigger.TriggerDispatch;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import platform.caps.Regions;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import schema.spec.Args;

/**
 * The Javelin flight + camera-lock machine (ADR-0071): a self-rescheduling one-tick chain re-keyed to the
 * advancing tip's region every step (honest Folia region-hopping), a per-victim position+look pin task for the
 * lock, and boot-resolved cue layers. Damage is priced AT THROW (a mid-flight weapon swap does not retro-price
 * it); the impact hurt attributes to the thrower only when its handle is region-owned at hurt time (the
 * {@code ComboDotRelease} idiom). The wall-check AND the hit scan are {@code Regions.read}-guarded because the
 * per-step advance can cross a region boundary — a guarded fault fails closed and the next re-keyed step
 * re-covers the sliver (hit-radius ≫ the advance). Flights are fire-and-forget with a hard step budget;
 * {@link #clearAll} tears down flights AND held victims; the thrower quitting drops only attribution.
 */
public final class JavelinService {

    private static final int HARD_STEP_CAP = 400;

    private static final class Flight {
        final long id;
        final UUID throwerId;
        volatile LivingEntity thrower; // attribution only (scope-swept on quit)
        final Vector step;             // dir * speed (blocks/tick)
        final Location tip;            // advanced in place, only on the owning region thread
        final Location start;
        final double maxSq;
        final double hitRadius;
        final double damage;           // resolved at launch (WEAPON read happens at launch)
        final double kbVelocity;       // knockback-base * knockback
        final int lock;
        final int lockDelay;
        final int nauseaId;
        final int nauseaTicks;
        final int particleId;
        final int r;
        final int g;
        final int b;
        final float size;
        final int budget;
        int steps;
        volatile boolean done;

        Flight(long id, UUID throwerId, LivingEntity thrower, Vector step, Location tip, Location start,
               double maxSq, double hitRadius, double damage, double kbVelocity, int lock, int lockDelay,
               int nauseaId, int nauseaTicks, int particleId, int r, int g, int b, float size, int budget) {
            this.id = id;
            this.throwerId = throwerId;
            this.thrower = thrower;
            this.step = step;
            this.tip = tip;
            this.start = start;
            this.maxSq = maxSq;
            this.hitRadius = hitRadius;
            this.damage = damage;
            this.kbVelocity = kbVelocity;
            this.lock = lock;
            this.lockDelay = lockDelay;
            this.nauseaId = nauseaId;
            this.nauseaTicks = nauseaTicks;
            this.particleId = particleId;
            this.r = r;
            this.g = g;
            this.b = b;
            this.size = size;
            this.budget = budget;
        }
    }

    private static final Map<Long, Flight> LIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, TaskHandle> HOLDS = new ConcurrentHashMap<>(); // victim → pin task
    private static final AtomicLong IDS = new AtomicLong();

    private final TriggerDispatch dispatch;
    private final WeaponDamage weaponDamage; // §B6.2 seam, injected from the bindings
    private final Cue[] throwCue;
    private final Cue[] impactCue;
    private final Cue[] thudCue; // wall hit

    public JavelinService(TriggerDispatch dispatch, WeaponDamage weaponDamage, PlatformResolvers resolvers) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.weaponDamage = Objects.requireNonNull(weaponDamage, "weaponDamage");
        Objects.requireNonNull(resolvers, "resolvers");
        this.throwCue = new Cue[]{
                cue(resolvers, "ITEM_TRIDENT_THROW", 1.0f, 0.8f),
                cue(resolvers, "ENTITY_ARROW_SHOOT", 1.0f, 0.7f)};
        this.impactCue = new Cue[]{
                cue(resolvers, "ITEM_TRIDENT_HIT", 1.0f, 1.0f),
                cue(resolvers, "ENTITY_PLAYER_ATTACK_CRIT", 0.7f, 0.9f)};
        this.thudCue = new Cue[]{
                cue(resolvers, "ITEM_TRIDENT_HIT_GROUND", 1.0f, 0.9f),
                cue(resolvers, "BLOCK_STONE_HIT", 0.7f, 0.8f)};
    }

    /** Reforge runner hook: launch from the caster's eye along their facing. */
    public void start(Player actor, CompiledEffect fx) {
        Args a = fx.args();
        double speed = a.dbl("speed");
        double maxTravel = a.dbl("max-travel");
        double hitRadius = a.dbl("hit-radius");
        boolean weaponMode = "WEAPON".equalsIgnoreCase(a.str("damage-mode"));
        double flatDamage = a.dbl("damage");
        double knockback = a.dbl("knockback");
        double knockbackBase = a.dbl("knockback-base");
        int lock = a.integer("lock");
        int lockDelay = a.integer("lock-delay");
        int nauseaId = a.integer("nausea-effect");
        int nauseaTicks = a.integer("nausea-duration");
        int particleId = a.integer("particle");
        int r = a.integer("r");
        int g = a.integer("g");
        int b = a.integer("b");
        float size = (float) a.dbl("size");

        double damage = weaponMode ? weaponDamage.swingDamage(actor) : flatDamage; // priced at THROW
        Location eye = actor.getEyeLocation();
        Vector step = eye.getDirection().multiply(speed);
        int budget = Math.min(HARD_STEP_CAP, (int) Math.ceil(maxTravel / speed));
        play(eye, throwCue);

        long id = IDS.incrementAndGet();
        Flight f = new Flight(id, actor.getUniqueId(), actor, step, eye.clone(), eye.clone(),
                maxTravel * maxTravel, hitRadius, damage, knockbackBase * knockback, lock, lockDelay,
                nauseaId, nauseaTicks, particleId, r, g, b, size, budget);
        LIVE.put(id, f);
        Scheduling.onRegion(f.tip, () -> step(id));
    }

    /** One tick of flight, on the region the step was ARMED on (the pre-advance tip). */
    private void step(long id) {
        Flight f = LIVE.get(id);
        if (f == null || f.done) {
            return;
        }
        f.tip.add(f.step);
        f.steps++;
        if (!isPassableHere(f.tip)) {
            play(f.tip, thudCue);
            drop(id);
            return; // a wall stops it
        }
        // Region honesty: the advance in (2) may cross a Folia boundary, so the off-region scan is guarded
        // and fails closed — the next re-keyed step re-covers the sliver (hit-radius ≫ the advance).
        LivingEntity victim = Regions.read("JavelinService.scan", () -> firstVictim(f), null);
        if (victim != null) {
            impact(f, victim);
            drop(id);
            return;
        }
        dispatch.dust(List.of(f.tip.clone()), f.particleId, f.r, f.g, f.b, f.size);
        if (f.steps >= f.budget || distanceSq(f.start, f.tip) > f.maxSq) {
            drop(id);
            return; // the wasted miss
        }
        Scheduling.onRegionLater(f.tip.clone(), 1L, () -> step(id)); // the clone re-keys the next hop to the advanced region
    }

    private LivingEntity firstVictim(Flight f) {
        for (Entity e : f.tip.getWorld().getNearbyEntities(f.tip, f.hitRadius, f.hitRadius, f.hitRadius)) {
            if (e instanceof LivingEntity le && !le.getUniqueId().equals(f.throwerId)) {
                return le;
            }
        }
        return null;
    }

    private boolean isPassableHere(Location at) {
        return Regions.read("JavelinService.wall", () -> !at.getBlock().getType().isSolid(), true);
    }

    /** Impact on the tip's region; every victim mutation hops to the victim's own scheduler. */
    private void impact(Flight f, LivingEntity victim) {
        Vector kb = f.step.clone().normalize().multiply(f.kbVelocity); // straight back along the flight angle
        kb.setY(Math.max(0.12, kb.getY() + 0.12)); // a touch of lift so the knock reads
        Scheduling.onEntity(victim, () -> {
            if (!victim.isValid()) {
                return;
            }
            LivingEntity thrower = f.thrower;
            boolean attributable = thrower != null && Regions.ownedByCurrentRegion(thrower) && thrower.isValid();
            EngineDamage.hurt(victim, f.damage, attributable ? thrower : null); // §B1.6 idiom
            victim.setVelocity(victim.getVelocity().add(kb));
            armHold(victim, f.lockDelay, f.lock);
            // Nausea is SEQUENCED, not concurrent (owner LAW: camera-lock + freeze, THEN Nausea): armed here but
            // landing only when the hold releases, so the full duration hits a victim who just regained control.
            int nauseaId = f.nauseaId;
            int nauseaTicks = f.nauseaTicks;
            Scheduling.onEntityLater(victim, (long) f.lockDelay + f.lock, () -> {
                if (victim.isValid()) {
                    dispatch.potion(victim, nauseaId, 0, nauseaTicks); // fresh sink → era potionEffect(int) leaf
                }
            });
            play(victim.getLocation(), impactCue);
        });
    }

    /** The camera-lock/movement-freeze: a per-tick position+look pin on the victim's own scheduler for {@code lock} ticks. */
    private void armHold(LivingEntity victim, int delay, int lock) {
        UUID vid = victim.getUniqueId();
        Scheduling.onEntityLater(victim, Math.max(0, delay), () -> {
            if (!victim.isValid()) {
                return;
            }
            TaskHandle old = HOLDS.remove(vid);
            if (old != null) {
                old.cancel(); // refresh-not-stack: a second impact mid-hold replaces the task
            }
            Location held = victim.getLocation().clone(); // position + yaw + pitch, captured post-knock
            long deadline = lock;
            long[] t = {0};
            TaskHandle[] h = new TaskHandle[1];
            h[0] = Scheduling.repeatingEntity(victim, 1L, 1L, () -> {
                if (!victim.isValid() || ++t[0] > deadline) { // quit/death/expiry → release
                    HOLDS.remove(vid);
                    if (h[0] != null) {
                        h[0].cancel();
                    }
                    return;
                }
                // Folia's sync teleport throws unconditionally, so the pin rides the era teleport leaf every tick
                // (modern = teleportAsync completes the zero-distance same-region hop within the tick; legacy sync).
                dispatch.teleportEntity(victim, held);
                victim.setVelocity(new Vector(0, 0, 0)); // own-scheduler velocity write: legal both platforms
            });
            HOLDS.put(vid, h[0]);
        });
    }

    /** Cancel + evict (idempotent); the pending re-arm sees {@code done} and stops. */
    private static void drop(long id) {
        Flight f = LIVE.remove(id);
        if (f != null) {
            f.done = true;
        }
    }

    /** Thrower quit → null attribution on their live flights (the flights carry on, unattributed). */
    public static PlayerScoped scope() {
        return quitter -> {
            for (Flight f : LIVE.values()) {
                if (f.throwerId.equals(quitter)) {
                    f.thrower = null;
                }
            }
        };
    }

    /** Disable stop ("javelin flights"): end every flight AND release every held victim. */
    public static void clearAll() {
        for (Flight f : LIVE.values()) {
            f.done = true;
        }
        LIVE.clear();
        for (TaskHandle hold : HOLDS.values()) {
            hold.cancel();
        }
        HOLDS.clear();
    }

    /** Test seam: the number of live flights. */
    static int liveFlightCount() {
        return LIVE.size();
    }

    /** Test seam: the number of held victims. */
    static int holdCount() {
        return HOLDS.size();
    }

    private void play(Location at, Cue[] cues) {
        for (Cue cue : cues) {
            dispatch.sound(at, cue.sound(), cue.volume(), cue.pitch());
        }
    }

    private static Cue cue(PlatformResolvers resolvers, String token, float volume, float pitch) {
        return new Cue(resolvers.sound(token).orElse(-1), volume, pitch);
    }

    private static double distanceSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record Cue(int sound, float volume, float pitch) {
    }
}
