package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Iterator;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code SOUND} — play a sound at the activation location (§7); {@code sound} interned at compile (§9).
 * Emission is deduped per hit: the same sound id plays at most once per event sink ({@link CueOnce}, ADR-0066).
 *
 * <p>An optional {@code who} target slot moves the anchor: the cue plays at each resolved entity's own
 * position instead of the activation's, still world-audible there at the same volume and pitch (the
 * {@code PARTICLE} {@code who} shape). When {@code who} resolves no entities — the default {@code @Here} —
 * it falls back to the activation location exactly as before.
 *
 * <p>{@code dy} raises that anchor. It resolves on two sides on purpose: the location branch already HOLDS the
 * point, so it moves it here; the entity branch does not (reading a target's position in an effect is the
 * cross-region read ADR-0043 forbids), so the offset rides the intent and lands in the sink on the target's own
 * thread. Both spend nothing at {@code dy: 0}, which is every line authored before this.
 */
public final class SoundEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SOUND")
            .param("sound", D.sound())
            .param("volume", D.DOUBLE.min(0).def(1))
            .param("pitch", D.DOUBLE.min(0).def(1))
            .param("dy", D.DOUBLE.range(-16, 16).def(0), "blocks to raise the anchor before the cue plays")
            .target("who", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Play a sound at the activation location, or at each entity in `who` when given — world-audible "
                    + "there at the same volume and pitch. `dy` raises that anchor (an overhead cue is `dy: 4`). "
                    + "No-op if `who` resolves nothing and the activation has no location.")
            .example("{ SOUND: { sound: ENTITY_GENERIC_EXPLODE, volume: 1, pitch: 1 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int soundId = ctx.integer("sound");
        double dy = ctx.dbl("dy");
        Iterator<LivingEntity> targets = ctx.targets("who").iterator();
        if (targets.hasNext()) {
            // ADR-0066 brackets CO-ACTIVATIONS, not targets: one authored line still reaches every entity it
            // resolved. Claimed here rather than per target so a sibling line sharing the cue stays collapsed.
            if (!CueOnce.claim(sink, soundId)) {
                return;
            }
            float volume = (float) ctx.dbl("volume");
            float pitch = (float) ctx.dbl("pitch");
            do {
                sink.sound(targets.next(), soundId, volume, pitch, dy);
            } while (targets.hasNext());
            return;
        }
        Location loc = ctx.location(); // no who (default @Here): the original activation-location cue
        if (loc == null) {
            return;
        }
        // ADR-0066: one audible cue per sound per hit. Same-sound co-activations inside one event walk
        // (sibling enchants sharing a cue, worn multi-copies, the ECHO_STRIKE pass) collapse to ONE play.
        if (CueOnce.claim(sink, soundId)) {
            sink.sound(Anchors.raised(loc, dy), soundId, (float) ctx.dbl("volume"), (float) ctx.dbl("pitch"));
        }
    }
}
