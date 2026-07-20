package feature.reforge;

import compile.model.CompiledEffect;
import feature.trigger.TriggerDispatch;
import java.util.Objects;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import platform.caps.Regions;
import schema.spec.Args;

/**
 * The Leviathan's Reach resolver (ADR-0071): resolve-and-emit, no per-tick machine, no LIVE map —
 * both rays via the era raycast on the actor's thread at the reforge activation success point, the
 * closer (eye-measured) wins, then ONE {@code grapple} intent through a fresh {@link TriggerDispatch}
 * sink; an open-air aim (neither ray connects) ZIPS the caster toward the point at full range along
 * the facing — grappling air pulls you to where you aimed, it never fails (cooldown stays spent).
 * Stateless: registers no store and no stop. The runtime resolves one selector per effect, so
 * GRAPPLE cannot author its two rays as selectors — the service resolves entity + block directly
 * through the era raycast method refs (the composition-only seam: no era class named in feature
 * code). The closer-of-entity-vs-block rule also compensates the 1.8 dot-scan's wall-blindness.
 */
public final class GrappleService {

    private final TriggerDispatch dispatch;
    private final BiFunction<Player, Integer, Entity> targetEntity;
    private final BiFunction<Player, Integer, Block> targetBlock;

    public GrappleService(TriggerDispatch dispatch,
                          BiFunction<Player, Integer, Entity> targetEntity,
                          BiFunction<Player, Integer, Block> targetBlock) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.targetEntity = Objects.requireNonNull(targetEntity, "targetEntity");
        this.targetBlock = Objects.requireNonNull(targetBlock, "targetBlock");
    }

    /** Reforge runner hook: the def activated with a GRAPPLE effect — resolve both rays and emit (or whiff). */
    public void start(Player actor, CompiledEffect fx) {
        Args a = fx.args();
        double range = a.dbl("range");
        double hookSpeed = a.dbl("hook-speed");
        double reelDistance = a.dbl("reel-distance");
        int slowId = a.integer("slow-effect");
        int slowLevel = a.integer("slow-level");
        int slowTicks = a.integer("slow-duration");
        double zipStrength = a.dbl("zip-strength");
        double zipCap = a.dbl("zip-cap");
        double zipRise = a.dbl("zip-rise");
        int particleId = a.integer("particle");
        int r = a.integer("r");
        int g = a.integer("g");
        int b = a.integer("b");
        float size = (float) a.dbl("size");
        double density = a.dbl("density");

        int reach = (int) Math.ceil(range);
        Location origin = actor.getLocation();
        Location eye = actor.getEyeLocation();

        // Entity ray: keep a living hit that is not the caster (the 1.8 dot-scan can self-hit at pitch extremes).
        Entity hit = targetEntity.apply(actor, reach);
        LivingEntity victim = (hit instanceof LivingEntity le
                && !le.getUniqueId().equals(actor.getUniqueId())) ? le : null;
        double dVictim = Double.MAX_VALUE;
        if (victim != null) {
            LivingEntity v = victim;
            Location vloc = Regions.read("GrappleService.victim", v::getLocation, null); // faulted read → no entity
            if (vloc == null || vloc.getWorld() == null || vloc.getWorld() != origin.getWorld()) {
                victim = null;
            } else {
                // Mode-pick distances measure from the EYE, where both rays cast — feet-measured, the ground
                // behind a victim's feet reads closer and silently flips an aimed reel into a zip.
                dVictim = vloc.distanceSquared(eye);
            }
        }

        Block block = targetBlock.apply(actor, reach);
        Location blockCenter = block == null ? null : block.getLocation().add(0.5, 0.5, 0.5);
        double dBlock = blockCenter == null ? Double.MAX_VALUE : blockCenter.distanceSquared(eye);

        if (victim != null && dVictim <= dBlock) {
            // ENTITY MODE: reel the hooked enemy to reel-distance blocks in front of the caster (feet Y kept).
            Vector facing = origin.getDirection();
            facing.setY(0);
            if (facing.lengthSquared() < 1.0e-6) {
                facing = new Vector(0, 0, 1); // straight-up/down look → an arbitrary but stable horizontal
            }
            Location reelTo = origin.clone().add(facing.normalize().multiply(reelDistance));
            int flightTicks = Math.max(1, (int) Math.round(Math.sqrt(dVictim) / hookSpeed));
            dispatch.grapple(actor, eye, victim, reelTo, null, flightTicks,
                    slowId, slowLevel - 1, slowTicks, null, particleId, r, g, b, size, density);
        } else {
            // TERRAIN or AIR: zip the caster toward the sighted block, or — when neither ray connects — toward
            // the point at full range along the facing (grappling air pulls you where you aimed; ADR-0071 owner
            // ruling: a miss reels you, it never fails). Same capped velocity + rise; cooldown stays spent.
            Location hookPoint = blockCenter != null
                    ? blockCenter
                    : eye.clone().add(eye.getDirection().multiply(range));
            Vector delta = hookPoint.toVector().subtract(origin.toVector());
            double dist = delta.length();
            Vector zip;
            if (dist < 1.0e-6) {
                zip = new Vector(0, zipRise, 0);
            } else {
                zip = delta.normalize().multiply(Math.min(zipCap, zipStrength * dist));
                zip.setY(zip.getY() + zipRise);
            }
            int flightTicks = Math.max(1, (int) Math.round(dist / hookSpeed));
            dispatch.grapple(actor, eye, null, null, hookPoint, flightTicks,
                    0, 0, 0, zip, particleId, r, g, b, size, density);
        }
    }
}
