package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.selector.kind.Targets;
import engine.sink.FieldCue;
import engine.sink.Sink;
import engine.sink.TurretRingProfile;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code TURRET_RING} — stand a ring of invulnerable emplacements on the ground around the actor, each of which
 * arms, then picks the nearest enemy it can SEE and lobs a slow projectile at it on a jittered beat until its
 * lifetime runs out. Demonic Gateway.
 *
 * <p>{@code SPAWN_ENTITY}/{@code SPAWN_SWARM} are the near misses and are a different thing: their summons are
 * mobs with vanilla AI that walk, path and die. These do not move, cannot be killed, and their only behaviour is
 * the shot — the reason they are placed on OPEN ground rather than at the cast point, and the reason the
 * placement is gated per SITE: a ring several blocks wide crosses claim boundaries the cast itself never did, so
 * each spot is asked about separately and a denied or unusable one is simply skipped (logged at DEBUG).
 *
 * <p>The shot carries no payload of its own — a strike fires the ACTOR's {@code IMPACT} abilities on whatever it
 * hits, once, exactly as a landing {@code FALLING_BLOCK} does. Emplacements and their shots never break blocks:
 * both blasts are cancelled, so a turret ring is safe to fire inside a build.
 *
 * <p><strong>Era note.</strong> Invulnerability rides the engine's summon registry (every hit is ZEROED at
 * {@code HIGHEST}), which is plain Bukkit event work and holds on 1.8.9 as well as the modern range — no
 * NMS flag. A fireball-family projectile is propelled by {@code setDirection}, whose scaling changed in the
 * 1.21 line: the shot flies on every lane, but reads noticeably faster on the newest servers than the
 * authored speed describes.
 */
public final class TurretRingEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TURRET_RING")
            .param("type", D.entityType().def("ENDER_CRYSTAL"), "what each emplacement is")
            .param("count", D.INT.range(1, 16).def(3), "how many emplacements the ring tries to place")
            .param("ring-radius", D.DOUBLE.min(0).def(7), "blocks from the actor to each emplacement")
            .param("ttl", D.TICKS.min(1).def(200), "how long the ring stands before it despawns")
            .param("acquire-range", D.DOUBLE.min(0).def(8), "how far an emplacement looks for a target")
            .param("initial-delay", D.TICKS.min(0).def(30), "ticks before the FIRST volley — the arming window")
            .param("period-min", D.TICKS.min(1).def(8), "shortest gap between volleys")
            .param("period-max", D.TICKS.min(1).def(13), "longest gap between volleys")
            .param("filter", D.enumSetOf(Targets.names()).def("ENEMIES"), "who an emplacement will shoot at")
            .param("projectile", D.entityType().def("WITHER_SKULL"), "what an emplacement fires")
            .param("projectile-speed", D.DOUBLE.min(0).def(0.06), "how hard each shot is launched")
            .param("spawn-sound", D.sound().optional(), "cue as the ring lands; omit for silence")
            .param("spawn-volume", D.DOUBLE.min(0).def(1))
            .param("spawn-pitch", D.DOUBLE.min(0).def(1))
            .param("spawn-particle", D.particle().optional(), "burst at each emplacement; omit for none")
            .param("spawn-particle-count", D.INT.min(0).def(1))
            .param("spawn-particle-spread", D.DOUBLE.min(0).def(0))
            .param("spawn-lightning", D.BOOL.def(true), "flash a damage-free lightning visual at each emplacement")
            .param("despawn-sound", D.sound().optional(), "cue as an emplacement expires; omit for silence")
            .param("despawn-volume", D.DOUBLE.min(0).def(1))
            .param("despawn-pitch", D.DOUBLE.min(0).def(1))
            .param("despawn-particle", D.particle().optional(), "burst as an emplacement expires; omit for none")
            .param("despawn-particle-count", D.INT.min(0).def(16))
            .param("despawn-particle-spread", D.DOUBLE.min(0).def(0.75))
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .doc("Stand `count` invulnerable `type` emplacements on open ground, evenly spaced on a "
                    + "ring-radius ring around each target, for `ttl` ticks. A site with no open ground, or one "
                    + "the protection gate denies the actor, is SKIPPED (logged) — the ring is gated spot by "
                    + "spot, not once for the cast. After `initial-delay` ticks each emplacement fires a "
                    + "`projectile` at `projectile-speed` toward the nearest body the `filter` admits within "
                    + "acquire-range that has line of sight to it, then re-fires every period-min..period-max "
                    + "ticks (a fresh draw per volley, so a ring never fires as one salvo). A shot that strikes "
                    + "a body runs the ACTOR's IMPACT abilities on it ONCE — that payload is the whole damage; "
                    + "emplacements take no damage and neither they nor their shots ever break blocks. The "
                    + "`spawn-` cue plays where each one lands (plus a damage-free lightning flash unless "
                    + "spawn-lightning: false) and the `despawn-` cue where it expires. Era note: a "
                    + "fireball-family projectile is propelled with setDirection, whose scaling changed in the "
                    + "1.21 line — the shot flies everywhere, but reads faster there than the authored speed.")
            .example("{ TURRET_RING: { type: ENDER_CRYSTAL, count: 5, ring-radius: 8, ttl: 300, "
                    + "acquire-range: 11, initial-delay: 30, period-min: 8, period-max: 13, filter: ENEMIES, "
                    + "projectile: WITHER_SKULL, projectile-speed: 0.065, spawn-sound: ENTITY_GHAST_SHOOT, "
                    + "spawn-volume: 3.0, spawn-pitch: 0.9, spawn-particle: FLAME, spawn-particle-count: 24, "
                    + "spawn-lightning: true, despawn-particle: SPELL_WITCH, despawn-particle-count: 16, "
                    + "despawn-particle-spread: 0.75, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        TurretRingProfile profile = new TurretRingProfile(
                ctx.integer("type"), ctx.integer("count"), ctx.dbl("ring-radius"), ctx.integer("ttl"),
                ctx.dbl("acquire-range"), ctx.integer("initial-delay"), ctx.integer("period-min"),
                ctx.integer("period-max"), ctx.integer("projectile"), ctx.dbl("projectile-speed"),
                ctx.str("filter"));
        FieldCue spawnCue = cue(ctx, "spawn-sound", "spawn-volume", "spawn-pitch",
                "spawn-particle", "spawn-particle-count");
        FieldCue despawnCue = cue(ctx, "despawn-sound", "despawn-volume", "despawn-pitch",
                "despawn-particle", "despawn-particle-count");
        double spawnSpread = ctx.dbl("spawn-particle-spread");
        double despawnSpread = ctx.dbl("despawn-particle-spread");
        boolean lightning = ctx.bool("spawn-lightning");
        for (LivingEntity who : ctx.targets("who")) {
            Location origin;
            try {
                origin = who.getLocation(); // guarded like every other field origin: @Attacker can be cross-region (ADR-0043)
            } catch (RuntimeException unreadable) {
                Regions.swallowed("TurretRingEffect.origin", unreadable);
                continue;
            }
            if (origin.getWorld() == null) {
                continue;
            }
            sink.turretRing(origin, ctx.actor(), profile, spawnCue, spawnSpread, lightning,
                    despawnCue, despawnSpread);
        }
    }

    /** One phase's cue pair. An absent HANDLE never interns, so -1 is unambiguously "no cue". */
    private static FieldCue cue(EffectCtx ctx, String sound, String volume, String pitch,
                                String particle, String count) {
        int soundId = ctx.args().has(sound) ? ctx.integer(sound) : -1;
        int particleId = ctx.args().has(particle) ? ctx.integer(particle) : -1;
        if (soundId < 0 && particleId < 0) {
            return FieldCue.SILENT;
        }
        return new FieldCue(soundId,
                soundId < 0 ? 0f : (float) ctx.dbl(volume),
                soundId < 0 ? 0f : (float) ctx.dbl(pitch),
                particleId,
                particleId < 0 ? 0 : ctx.integer(count));
    }
}
