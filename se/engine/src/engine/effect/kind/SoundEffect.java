package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
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
            .param("dedupe", D.BOOL.def(true), "collapse the same sound id to one play per event sink")
            .param("audience", D.enumOf("world", "target").def("world"))
            .param("players-only", D.BOOL.def(false), "skip resolved non-player living targets")
            .param("at", D.enumOf("target", "activation").def("target"),
                    "where the sound is located when who resolves entities")
            .target("who", T.HERE)
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
        int soundId = ctx.integer("sound");
        boolean dedupe = !ctx.args().has("dedupe") || ctx.bool("dedupe");
        if (dedupe && !CueOnce.claim(sink, soundId)) {
            return;
        }
        float volume = (float) ctx.dbl("volume");
        float pitch = (float) ctx.dbl("pitch");
        boolean privateAudience = ctx.args().has("audience")
                && "target".equalsIgnoreCase(ctx.str("audience"));
        boolean playersOnly = ctx.args().has("players-only") && ctx.bool("players-only");
        java.util.Iterator<LivingEntity> targets = ctx.targets("who").iterator();
        if (targets.hasNext()) {
            do {
                LivingEntity target = targets.next();
                if (playersOnly && !(target instanceof org.bukkit.entity.Player)) {
                    continue;
                }
                if (privateAudience && target instanceof org.bukkit.entity.Player player) {
                    if ("activation".equalsIgnoreCase(ctx.str("at"))) {
                        sink.privateSoundAt(player, ctx.location(), soundId, volume, pitch);
                    } else {
                        sink.privateSound(player, soundId, volume, pitch);
                    }
                } else {
                    sink.sound(target, soundId, volume, pitch);
                }
            } while (targets.hasNext());
            return;
        }
        Location loc = ctx.location();
        if (loc != null) {
            sink.sound(loc, soundId, volume, pitch);
        }
    }
}
