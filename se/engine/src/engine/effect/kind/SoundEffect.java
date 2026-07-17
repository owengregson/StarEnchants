package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code SOUND} — play a sound at the activation location (§7); {@code sound} interned at compile (§9).
 * Emission is deduped per hit: the same sound id plays at most once per event sink ({@link CueOnce}, ADR-0066).
 */
public final class SoundEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SOUND")
            .param("sound", D.sound())
            .param("volume", D.DOUBLE.min(0).def(1))
            .param("pitch", D.DOUBLE.min(0).def(1))
            .affinity(Affinity.REGION)
            .doc("Play a sound at the activation location. No-op if the activation has no location.")
            .example("{ SOUND: { sound: ENTITY_GENERIC_EXPLODE, volume: 1, pitch: 1 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location loc = ctx.location();
        if (loc == null) {
            return;
        }
        int soundId = ctx.integer("sound");
        // ADR-0066: one audible cue per sound per hit. Same-sound co-activations inside one event walk
        // (sibling enchants sharing a cue, worn multi-copies, the ECHO_STRIKE pass) collapse to ONE play.
        if (CueOnce.claim(sink, soundId)) {
            sink.sound(loc, soundId, (float) ctx.dbl("volume"), (float) ctx.dbl("pitch"));
        }
    }
}
