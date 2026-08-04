package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.selector.kind.Targets;
import engine.sink.FieldCue;
import engine.sink.Sink;
import engine.sink.StrikeFieldProfile;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code DELAYED_STRIKE_FIELD} — scatter {@code points} marked spots on the ground around the origin, telegraph
 * every one of them at once, warn everyone in range, then a {@code delay} later strike all of them together.
 * The window between the two phases IS the ability: it is entirely dodgeable, and standing still is what gets
 * you hit. Yijki's Revenge.
 *
 * <p>Points are independent and never de-duplicated — stand where two of them overlap and you take two hits —
 * but each hit is a raw health subtraction floored at {@code health-floor}, so the field alone can never kill.
 *
 * <p><strong>Why flat cue params and not nested effect chains.</strong> The two phases are naturally "run these
 * effects here, then those effects there", and the DSL has no effect-chain parameter type: {@code D} offers
 * scalars, enums and version-volatile handles, and nothing carries a nested effect list. Rather than invent a
 * nested grammar for one kind, the phases are parameterised by the cues and payload the contract actually names
 * — {@code cue-*} for the telegraph, {@code strike-*}/{@code lightning} for the detonation, {@code damage} +
 * {@code health-floor} for what it does, {@code warning} for the shout — exactly the way {@code PERIODIC_DAMAGE}
 * parameterises {@code tick-sound}/{@code tick-particle} instead of taking a chain. An author who wants more
 * than a cue at the strike hangs it off the trigger the field runs on.
 */
public final class DelayedStrikeFieldEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DELAYED_STRIKE_FIELD")
            .param("points", D.INT.range(1, 64).def(16), "how many ground spots are marked")
            .param("offset-min", D.INT.range(0, 64).def(2), "closest a spot lands, per axis")
            .param("offset-max", D.INT.range(0, 64).def(9), "furthest a spot lands, per axis")
            .param("delay", D.TICKS.min(1).def(20), "ticks between the telegraph and the strike")
            // sqrt(2): the measured test is `distanceSquared <= 2`, and this radius squares back to it.
            .param("hit-radius", D.DOUBLE.min(0).def(1.4142135623730951), "how far from a spot the strike reaches")
            .param("target-range", D.DOUBLE.min(0).def(32), "how far the warning carries from the origin")
            .param("filter", D.enumSetOf(Targets.names()).def("ENEMIES"),
                    "who the warning and the strike admit; re-checked at the strike, not carried from the warning")
            .param("damage", D.DOUBLE.min(0).def(16), "raw half-hearts subtracted from a struck body's health")
            .param("health-floor", D.DOUBLE.min(0).def(1),
                    "health a strike can never take a body below — the reason the field cannot kill")
            .param("warning", D.STRING.def(""), "line shouted at everyone in range ({caster}); empty = no warning")
            .param("cue-sound", D.sound().optional(), "telegraph cue at each spot; omit for silence")
            .param("cue-volume", D.DOUBLE.min(0).def(1))
            .param("cue-pitch", D.DOUBLE.min(0).def(1))
            .param("cue-particle", D.particle().optional(), "telegraph burst at each spot; omit for none")
            .param("cue-particle-count", D.INT.min(0).def(1))
            .param("strike-sound", D.sound().optional(), "detonation cue at each spot; omit for silence")
            .param("strike-volume", D.DOUBLE.min(0).def(1))
            .param("strike-pitch", D.DOUBLE.min(0).def(1))
            .param("strike-particle", D.particle().optional(), "detonation burst at each spot; omit for none")
            .param("strike-particle-count", D.INT.min(0).def(1))
            .param("lightning", D.BOOL.def(true), "strike each spot with a damage-free lightning visual")
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .doc("Mark `points` ground spots around each target, at an independent per-axis offset of "
                    + "offset-min..offset-max blocks (a spot over lower ground snaps down onto it, but never "
                    + "rises above the origin), play the `cue-` telegraph at each one and shout `warning` "
                    + "({caster}) at everyone the `filter` admits within target-range. `delay` ticks later every "
                    + "spot detonates together: a damage-free lightning visual (unless lightning: false), the "
                    + "`strike-` cue, and `damage` raw half-hearts subtracted from every body within hit-radius "
                    + "of it — floored at health-floor, so the field cannot kill, and the filter is RE-CHECKED "
                    + "then, so walking into a spot during the delay gets you hit. Spots are independent: "
                    + "overlapping ones each land their own hit.")
            .example("{ DELAYED_STRIKE_FIELD: { points: 16, offset-min: 2, offset-max: 9, delay: 20, "
                    + "damage: 16, health-floor: 1, filter: ENEMIES, target-range: 32, "
                    + "cue-sound: ENTITY_WITHER_SPAWN, cue-pitch: 0.4, cue-particle: SPELL_WITCH, "
                    + "cue-particle-count: 32, strike-sound: ENTITY_WITHER_DEATH, strike-pitch: 0.4, "
                    + "strike-particle: EXPLOSION_LARGE, strike-particle-count: 4, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        StrikeFieldProfile profile = new StrikeFieldProfile(
                ctx.integer("points"), ctx.integer("offset-min"), ctx.integer("offset-max"),
                ctx.integer("delay"), ctx.dbl("hit-radius"), ctx.dbl("target-range"), ctx.str("filter"),
                ctx.dbl("damage"), ctx.dbl("health-floor"));
        FieldCue telegraph = cue(ctx, "cue-sound", "cue-volume", "cue-pitch", "cue-particle", "cue-particle-count");
        FieldCue strike = cue(ctx, "strike-sound", "strike-volume", "strike-pitch",
                "strike-particle", "strike-particle-count");
        boolean lightning = ctx.bool("lightning");
        String warning = ctx.str("warning");
        for (LivingEntity who : ctx.targets("who")) {
            Location origin;
            try {
                origin = who.getLocation(); // guarded like every other field origin: @Attacker can be cross-region (ADR-0043)
            } catch (RuntimeException unreadable) {
                Regions.swallowed("DelayedStrikeFieldEffect.origin", unreadable);
                continue;
            }
            if (origin.getWorld() == null) {
                continue;
            }
            sink.delayedStrikeField(origin, ctx.actor(), profile, telegraph, strike, lightning, warning);
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
