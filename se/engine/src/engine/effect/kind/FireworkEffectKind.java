package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code FIREWORK} — spawn a cosmetic firework at the activation location (docs/v3-directives.md §C).
 * Stateless; emits one {@code firework} intent and never touches the world directly. {@code power} is the
 * rocket flight duration (0–3 typically). No-op when there is no activation location. {@link Affinity#REGION}:
 * routed to the region thread owning the location.
 *
 * <p>Named {@code FireworkEffectKind} (not {@code FireworkEffect}) to avoid colliding with Bukkit's
 * {@code org.bukkit.FireworkEffect}.
 */
public final class FireworkEffectKind implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FIREWORK")
            .param("power", D.INT.min(0).max(3).def(1))
            .affinity(Affinity.REGION)
            .doc("Spawn a cosmetic firework at the activation location. No-op if there is no location.")
            .example("FIREWORK:1")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location loc = ctx.location();
        if (loc != null) {
            sink.firework(loc, ctx.integer("power"));
        }
    }
}
